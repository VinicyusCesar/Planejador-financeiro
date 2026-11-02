package com.planejador.service;

import com.planejador.dto.PaginaDto;
import com.planejador.dto.TransacaoDto;
import com.planejador.enums.*;
import com.planejador.exception.ApiException;
import com.planejador.model.*;
import com.planejador.repository.ConsultasFinanceirasRepository;
import com.planejador.repository.ParcelaRepository;
import com.planejador.repository.TransacaoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransacaoService {
    private final TransacaoRepository transacoes;
    private final ParcelaRepository parcelas;
    private final ConsultasFinanceirasRepository consultas;
    private final ReferenciaService referencias;
    private final CalculosFinanceirosService calculos;
    private final FaturaService faturas;

    public TransacaoService(TransacaoRepository transacoes, ParcelaRepository parcelas,
            ConsultasFinanceirasRepository consultas, ReferenciaService referencias,
            CalculosFinanceirosService calculos, FaturaService faturas) {
        this.transacoes = transacoes;
        this.parcelas = parcelas;
        this.consultas = consultas;
        this.referencias = referencias;
        this.calculos = calculos;
        this.faturas = faturas;
    }

    public PaginaDto<TransacaoDto.Saida> listar(UUID usuarioId, LocalDate inicio, LocalDate fim,
            TipoTransacao tipo, UUID contaId, UUID cartaoId, UUID categoriaId, SituacaoTransacao situacao,
            int pagina, int tamanho) {
        referencias.usuario(usuarioId);
        calculos.periodo(inicio, fim);
        if (contaId != null) referencias.conta(usuarioId, contaId, false);
        if (cartaoId != null) referencias.cartao(usuarioId, cartaoId, false);
        if (categoriaId != null) referencias.categoria(usuarioId, categoriaId);
        Specification<Transacao> filtro = (root, query, cb) -> {
            var predicados = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicados.add(cb.equal(root.get("usuario").get("id"), usuarioId));
            if (inicio != null) predicados.add(cb.greaterThanOrEqualTo(root.get("dataTransacao"), inicio));
            if (fim != null) predicados.add(cb.lessThanOrEqualTo(root.get("dataTransacao"), fim));
            if (tipo != null) predicados.add(cb.equal(root.get("tipo"), tipo));
            if (situacao != null) predicados.add(cb.equal(root.get("situacao"), situacao));
            if (contaId != null) predicados.add(cb.equal(root.get("conta").get("id"), contaId));
            if (cartaoId != null) predicados.add(cb.equal(root.get("cartao").get("id"), cartaoId));
            if (categoriaId != null) predicados.add(cb.equal(root.get("categoria").get("id"), categoriaId));
            return cb.and(predicados.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PaginaDto.de(transacoes.findAll(filtro,
                PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "dataTransacao", "id"))).map(this::saida));
    }

    public TransacaoDto.Saida consultar(UUID usuarioId, UUID id) {
        return saida(referencias.transacao(usuarioId, id));
    }

    public List<TransacaoDto.ParcelaSaida> parcelas(UUID usuarioId, UUID id) {
        referencias.transacao(usuarioId, id);
        return parcelas.findByTransacaoIdOrderByNumeroAsc(id).stream()
                .map(p -> new TransacaoDto.ParcelaSaida(p.getId(), id, p.getFatura().getId(), p.getNumero(),
                        p.getValor(), p.getFatura().getDataVencimento())).toList();
    }

    @Transactional
    public TransacaoDto.Saida criar(UUID usuarioId, TransacaoDto.Entrada entrada) {
        Usuario usuario = referencias.bloquearUsuario(usuarioId);
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        preencher(usuarioId, transacao, entrada);
        validarLimite(usuarioId, transacao);
        transacoes.saveAndFlush(transacao);
        if (creditoConfirmado(transacao)) faturas.gerarParcelas(transacao);
        return saida(transacao);
    }

    @Transactional
    public TransacaoDto.Saida atualizar(UUID usuarioId, UUID id, TransacaoDto.Entrada entrada) {
        referencias.bloquearUsuario(usuarioId);
        Transacao transacao = referencias.transacao(usuarioId, id);
        exigirEditavel(transacao);
        List<Parcela> anteriores = parcelas.findByTransacaoIdOrderByNumeroAsc(id);
        faturas.exigirParcelasSemPagamento(anteriores);
        preencher(usuarioId, transacao, entrada);
        parcelas.deleteAll(anteriores);
        transacoes.flush();
        validarLimite(usuarioId, transacao);
        if (creditoConfirmado(transacao)) faturas.gerarParcelas(transacao);
        faturas.recalcular(anteriores.stream().map(Parcela::getFatura).toList());
        return saida(transacao);
    }

    @Transactional
    public void cancelar(UUID usuarioId, UUID id) {
        referencias.bloquearUsuario(usuarioId);
        Transacao transacao = referencias.transacao(usuarioId, id);
        if (transacao.getSituacao() == SituacaoTransacao.CANCELADA) return;
        List<Parcela> anteriores = parcelas.findByTransacaoIdOrderByNumeroAsc(id);
        faturas.exigirParcelasSemPagamento(anteriores);
        transacao.setSituacao(SituacaoTransacao.CANCELADA);
        transacoes.flush();
        faturas.recalcular(anteriores.stream().map(Parcela::getFatura).toList());
    }

    private void exigirEditavel(Transacao transacao) {
        if (transacao.getSituacao() == SituacaoTransacao.CANCELADA) {
            throw ApiException.conflito("TRANSACAO_CANCELADA", "Uma transação cancelada não pode ser editada.");
        }
    }

    private boolean creditoConfirmado(Transacao transacao) {
        return transacao.getMeioPagamento() == MeioPagamento.CREDITO
                && transacao.getSituacao() == SituacaoTransacao.CONFIRMADA;
    }

    private void validarLimite(UUID usuarioId, Transacao transacao) {
        if (!creditoConfirmado(transacao)) return;
        BigDecimal usado = consultas.limiteUtilizado(usuarioId, transacao.getCartao().getId(), calculos.hoje());
        if (transacao.getValor().compareTo(transacao.getCartao().getLimite().subtract(usado)) > 0) {
            throw ApiException.conflito("LIMITE_INSUFICIENTE", "A compra ultrapassa o limite disponível do cartão.");
        }
    }

    private void preencher(UUID usuarioId, Transacao transacao, TransacaoDto.Entrada entrada) {
        BigDecimal valor = calculos.moeda(entrada.valor(), false);
        int quantidade = entrada.quantidadeParcelas() == null ? 1 : entrada.quantidadeParcelas();
        CondicaoPagamento condicao = entrada.condicao() == null
                ? (quantidade > 1 ? CondicaoPagamento.PARCELADO : CondicaoPagamento.AVISTA) : entrada.condicao();
        SituacaoTransacao situacao = entrada.situacao() == null ? SituacaoTransacao.CONFIRMADA : entrada.situacao();
        if (situacao == SituacaoTransacao.CANCELADA) {
            throw ApiException.invalido("SITUACAO_INVALIDA", "Utilize a operação de cancelamento.");
        }
        if (quantidade < 1 || quantidade > 600 || (condicao == CondicaoPagamento.AVISTA && quantidade != 1)
                || (condicao == CondicaoPagamento.PARCELADO && quantidade <= 1)) {
            throw ApiException.invalido("PARCELAMENTO_INVALIDO", "A condição e a quantidade de parcelas são incompatíveis.");
        }
        if (entrada.dataTransacao() == null || (situacao == SituacaoTransacao.CONFIRMADA
                && entrada.dataTransacao().isAfter(calculos.hoje()))) {
            throw ApiException.invalido("DATA_INVALIDA", "Transações futuras devem permanecer pendentes.");
        }
        Categoria categoria = referencias.categoria(usuarioId, entrada.categoriaId());
        if (categoria.getTipo() != TipoCategoria.AMBOS
                && !categoria.getTipo().name().equals(entrada.tipo().name())) {
            throw ApiException.invalido("CATEGORIA_INCOMPATIVEL", "O tipo da categoria não corresponde ao tipo da transação.");
        }
        Conta conta = null;
        CartaoCredito cartao = null;
        LocalDate liquidacao = entrada.dataLiquidacao();
        if (entrada.meioPagamento() == MeioPagamento.CREDITO) {
            if (entrada.cartaoId() == null || entrada.contaId() != null || entrada.tipo() != TipoTransacao.DESPESA) {
                throw ApiException.invalido("CREDITO_INVALIDO", "Uma compra no crédito exige cartão, tipo DESPESA e nenhuma conta.");
            }
            if (liquidacao != null || entrada.dataVencimento() != null) {
                throw ApiException.invalido("DATA_INVALIDA", "Compras no crédito usam os vencimentos das faturas e não possuem liquidação bancária direta.");
            }
            cartao = referencias.cartao(usuarioId, entrada.cartaoId(), true);
            calculos.ciclo(entrada.dataTransacao(), cartao.getDiaFechamento(), cartao.getDiaVencimento());
            calculos.parcelar(valor, quantidade);
        } else {
            if (entrada.contaId() == null || entrada.cartaoId() != null) {
                throw ApiException.invalido("CONTA_OBRIGATORIA", "Informe uma conta e nenhum cartão para operações diretas.");
            }
            if (condicao != CondicaoPagamento.AVISTA || quantidade != 1) {
                throw ApiException.invalido("PARCELAMENTO_INVALIDO", "Parcelamento é permitido apenas no cartão de crédito.");
            }
            conta = referencias.conta(usuarioId, entrada.contaId(), true);
            if (situacao == SituacaoTransacao.PENDENTE && liquidacao != null) {
                throw ApiException.invalido("LIQUIDACAO_INVALIDA", "Uma operação pendente não pode possuir data de liquidação.");
            }
            if (situacao == SituacaoTransacao.CONFIRMADA) {
                if (liquidacao == null) liquidacao = entrada.dataTransacao();
                if (liquidacao.isBefore(entrada.dataTransacao()) || liquidacao.isAfter(calculos.hoje())) {
                    throw ApiException.invalido("LIQUIDACAO_INVALIDA", "A liquidação deve ocorrer entre a transação e a data atual.");
                }
            }
        }
        transacao.setCategoria(categoria);
        transacao.setConta(conta);
        transacao.setCartao(cartao);
        transacao.setDescricao(entrada.descricao().trim());
        transacao.setValor(valor);
        transacao.setTipo(entrada.tipo());
        transacao.setMeioPagamento(entrada.meioPagamento());
        transacao.setCondicao(condicao);
        transacao.setQuantidadeParcelas(quantidade);
        transacao.setDataTransacao(entrada.dataTransacao());
        transacao.setDataVencimento(entrada.dataVencimento());
        transacao.setDataLiquidacao(liquidacao);
        transacao.setSituacao(situacao);
    }

    private TransacaoDto.Saida saida(Transacao t) {
        return new TransacaoDto.Saida(t.getId(), t.getUsuario().getId(), t.getCategoria().getId(),
                t.getConta() == null ? null : t.getConta().getId(), t.getCartao() == null ? null : t.getCartao().getId(),
                t.getDescricao(), t.getValor(), t.getTipo(), t.getMeioPagamento(), t.getCondicao(),
                t.getQuantidadeParcelas(), t.getDataTransacao(), t.getDataVencimento(),
                t.getDataLiquidacao(), t.getSituacao());
    }
}
