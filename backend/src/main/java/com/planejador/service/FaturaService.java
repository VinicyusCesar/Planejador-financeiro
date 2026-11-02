package com.planejador.service;

import com.planejador.dto.FaturaDto;
import com.planejador.enums.PeriodoFatura;
import com.planejador.enums.SituacaoFatura;
import com.planejador.enums.SituacaoTransacao;
import com.planejador.exception.ApiException;
import com.planejador.model.*;
import com.planejador.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FaturaService {
    private final FaturaRepository faturas;
    private final ParcelaRepository parcelas;
    private final PagamentoFaturaRepository pagamentos;
    private final ConsultasFinanceirasRepository consultas;
    private final ReferenciaService referencias;
    private final CalculosFinanceirosService calculos;

    public FaturaService(FaturaRepository faturas, ParcelaRepository parcelas, PagamentoFaturaRepository pagamentos,
            ConsultasFinanceirasRepository consultas, ReferenciaService referencias, CalculosFinanceirosService calculos) {
        this.faturas = faturas;
        this.parcelas = parcelas;
        this.pagamentos = pagamentos;
        this.consultas = consultas;
        this.referencias = referencias;
        this.calculos = calculos;
    }

    public List<FaturaDto.Resumo> listar(UUID usuarioId, UUID cartaoId, LocalDate inicio,
            LocalDate fim, PeriodoFatura periodo) {
        referencias.usuario(usuarioId);
        calculos.periodo(inicio, fim);
        if (periodo != null && cartaoId == null) {
            throw ApiException.invalido("CARTAO_OBRIGATORIO", "Informe o cartão para filtrar o período de faturas.");
        }
        CartaoCredito cartao = cartaoId == null ? null : referencias.cartao(usuarioId, cartaoId, false);
        YearMonth atual = cartao == null ? null
                : calculos.ciclo(calculos.hoje(), cartao.getDiaFechamento(), cartao.getDiaVencimento()).referencia();
        return consultas.faturas(usuarioId, cartaoId, calculos.hoje()).stream()
                .filter(v -> inicio == null || !v.vencimento().isBefore(inicio))
                .filter(v -> fim == null || !v.vencimento().isAfter(fim))
                .filter(v -> periodo == null || corresponde(YearMonth.of(v.ano(), v.mes()), atual, periodo))
                .map(this::resumo).toList();
    }

    private boolean corresponde(YearMonth referencia, YearMonth atual, PeriodoFatura periodo) {
        return switch (periodo) {
            case ATUAL -> referencia.equals(atual);
            case ANTERIORES -> referencia.isBefore(atual);
            case PROXIMAS -> referencia.isAfter(atual);
        };
    }

    public FaturaDto.Detalhe consultar(UUID usuarioId, UUID id) {
        referencias.fatura(usuarioId, id);
        List<FaturaDto.Compra> compras = parcelas
                .findByFaturaIdAndTransacaoSituacaoOrderByNumeroAsc(id, SituacaoTransacao.CONFIRMADA).stream()
                .map(p -> new FaturaDto.Compra(p.getId(), p.getTransacao().getId(), p.getTransacao().getDescricao(),
                        p.getTransacao().getDataTransacao(), p.getNumero(), p.getTransacao().getQuantidadeParcelas(),
                        p.getValor())).toList();
        List<FaturaDto.PagamentoSaida> listaPagamentos = pagamentos.findByFaturaIdOrderByDataPagamentoAsc(id)
                .stream().map(this::pagamentoSaida).toList();
        return new FaturaDto.Detalhe(resumo(consultas.fatura(usuarioId, id, calculos.hoje())), compras, listaPagamentos);
    }

    // Chamado por TransacaoService após obter o bloqueio do proprietário.
    @Transactional
    public void gerarParcelas(Transacao transacao) {
        CartaoCredito cartao = transacao.getCartao();
        var primeiroCiclo = calculos.ciclo(transacao.getDataTransacao(),
                cartao.getDiaFechamento(), cartao.getDiaVencimento());
        List<BigDecimal> valores = calculos.parcelar(transacao.getValor(), transacao.getQuantidadeParcelas());
        java.util.ArrayList<Fatura> afetadas = new java.util.ArrayList<>();
        for (int indice = 0; indice < valores.size(); indice++) {
            YearMonth referencia = primeiroCiclo.referencia().plusMonths(indice);
            Fatura fatura = obterOuCriar(cartao, referencia);
            if (pagamentos.existsByFaturaId(fatura.getId())) {
                throw ApiException.conflito("FATURA_COM_PAGAMENTO",
                        "Não é permitido adicionar compras a uma fatura que já recebeu pagamentos.");
            }
            Parcela parcela = new Parcela();
            parcela.setTransacao(transacao);
            parcela.setFatura(fatura);
            parcela.setNumero(indice + 1);
            parcela.setValor(valores.get(indice));
            parcelas.save(parcela);
            afetadas.add(fatura);
        }
        // JdbcTemplate compartilha a transação, mas não provoca flush automático do JPA.
        parcelas.flush();
        recalcular(afetadas);
    }

    private Fatura obterOuCriar(CartaoCredito cartao, YearMonth referencia) {
        return faturas.findByCartaoIdAndMesReferenciaAndAnoReferencia(
                cartao.getId(), referencia.getMonthValue(), referencia.getYear()).orElseGet(() -> {
            var ciclo = calculos.datas(referencia, cartao.getDiaFechamento(), cartao.getDiaVencimento());
            Fatura fatura = new Fatura();
            fatura.setCartao(cartao);
            fatura.setMesReferencia(referencia.getMonthValue());
            fatura.setAnoReferencia(referencia.getYear());
            fatura.setDataFechamento(ciclo.fechamento());
            fatura.setDataVencimento(ciclo.vencimento());
            fatura.setSituacao(SituacaoFatura.ABERTA);
            return faturas.save(fatura);
        });
    }

    public void exigirParcelasSemPagamento(List<Parcela> lista) {
        if (lista.stream().map(p -> p.getFatura().getId()).distinct().anyMatch(pagamentos::existsByFaturaId)) {
            throw ApiException.conflito("FATURA_COM_PAGAMENTO",
                    "A transação não pode ser editada ou cancelada porque uma fatura já recebeu pagamentos.");
        }
    }

    @Transactional
    public void recalcular(Collection<Fatura> afetadas) {
        if (afetadas.isEmpty()) return;
        UUID usuarioId = afetadas.iterator().next().getCartao().getUsuario().getId();
        Map<UUID, ConsultasFinanceirasRepository.ValoresFatura> valores = consultas
                .faturas(usuarioId, null, calculos.hoje()).stream()
                .collect(Collectors.toMap(ConsultasFinanceirasRepository.ValoresFatura::id, Function.identity()));
        afetadas.forEach(fatura -> {
            var valor = valores.get(fatura.getId());
            fatura.setSituacao(calculos.situacao(valor.total(), valor.pago(), valor.fechamento(), valor.vencimento()));
        });
    }

    @Transactional
    public FaturaDto.PagamentoSaida pagar(UUID usuarioId, UUID id, FaturaDto.PagamentoEntrada entrada) {
        referencias.bloquearUsuario(usuarioId);
        Fatura fatura = referencias.fatura(usuarioId, id);
        Conta conta = referencias.conta(usuarioId, entrada.contaId(), true);
        BigDecimal valor = calculos.moeda(entrada.valor(), false);
        if (entrada.dataPagamento() == null || entrada.dataPagamento().isAfter(calculos.hoje())) {
            throw ApiException.invalido("DATA_INVALIDA", "O pagamento não pode ter data futura.");
        }
        var valores = consultas.fatura(usuarioId, id, calculos.hoje());
        if (valor.compareTo(valores.pendente()) > 0) {
            throw ApiException.conflito("PAGAMENTO_EXCEDENTE", "O pagamento não pode ultrapassar o saldo pendente da fatura.");
        }
        PagamentoFatura pagamento = new PagamentoFatura();
        pagamento.setFatura(fatura);
        pagamento.setConta(conta);
        pagamento.setValor(valor);
        pagamento.setDataPagamento(entrada.dataPagamento());
        pagamentos.saveAndFlush(pagamento);
        recalcular(List.of(fatura));
        return pagamentoSaida(pagamento);
    }

    public FaturaDto.Resumo resumo(ConsultasFinanceirasRepository.ValoresFatura v) {
        return new FaturaDto.Resumo(v.id(), v.cartaoId(), v.mes(), v.ano(), v.fechamento(), v.vencimento(),
                calculos.situacao(v.total(), v.pago(), v.fechamento(), v.vencimento()),
                v.total(), v.pago(), v.pendente());
    }

    private FaturaDto.PagamentoSaida pagamentoSaida(PagamentoFatura p) {
        return new FaturaDto.PagamentoSaida(p.getId(), p.getFatura().getId(), p.getConta().getId(),
                p.getValor(), p.getDataPagamento());
    }
}
