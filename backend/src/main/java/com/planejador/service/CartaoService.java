package com.planejador.service;

import com.planejador.dto.CartaoDto;
import com.planejador.exception.ApiException;
import com.planejador.model.CartaoCredito;
import com.planejador.repository.CartaoCreditoRepository;
import com.planejador.repository.ConsultasFinanceirasRepository;
import com.planejador.repository.FaturaRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartaoService {
    private final CartaoCreditoRepository cartoes;
    private final FaturaRepository faturas;
    private final ConsultasFinanceirasRepository consultas;
    private final ReferenciaService referencias;
    private final CalculosFinanceirosService calculos;

    public CartaoService(CartaoCreditoRepository cartoes, FaturaRepository faturas,
            ConsultasFinanceirasRepository consultas, ReferenciaService referencias, CalculosFinanceirosService calculos) {
        this.cartoes = cartoes;
        this.faturas = faturas;
        this.consultas = consultas;
        this.referencias = referencias;
        this.calculos = calculos;
    }

    public List<CartaoDto.Saida> listar(UUID usuarioId) {
        referencias.usuario(usuarioId);
        Map<UUID, BigDecimal> usados = consultas.faturas(usuarioId, null, calculos.hoje()).stream()
                .collect(Collectors.groupingBy(ConsultasFinanceirasRepository.ValoresFatura::cartaoId,
                        Collectors.reducing(CalculosFinanceirosService.ZERO,
                                ConsultasFinanceirasRepository.ValoresFatura::pendente, BigDecimal::add)));
        return cartoes.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
                .map(cartao -> saida(cartao, usados.getOrDefault(cartao.getId(), CalculosFinanceirosService.ZERO))).toList();
    }

    public CartaoDto.Saida consultar(UUID usuarioId, UUID id) {
        return saida(referencias.cartao(usuarioId, id, false),
                consultas.limiteUtilizado(usuarioId, id, calculos.hoje()));
    }

    @Transactional
    public CartaoDto.Saida criar(UUID usuarioId, CartaoDto.Entrada entrada) {
        CartaoCredito cartao = new CartaoCredito();
        cartao.setUsuario(referencias.bloquearUsuario(usuarioId));
        preencher(usuarioId, cartao, entrada);
        cartao.setAtivo(true);
        return saida(cartoes.save(cartao), CalculosFinanceirosService.ZERO);
    }

    @Transactional
    public CartaoDto.Saida atualizar(UUID usuarioId, UUID id, CartaoDto.Entrada entrada) {
        referencias.bloquearUsuario(usuarioId);
        CartaoCredito cartao = referencias.cartao(usuarioId, id, false);
        BigDecimal utilizado = consultas.limiteUtilizado(usuarioId, id, calculos.hoje());
        if (entrada.limite().compareTo(utilizado) < 0) {
            throw ApiException.conflito("LIMITE_EM_USO", "O novo limite não pode ser menor que o valor utilizado.");
        }
        if ((cartao.getDiaFechamento() != entrada.diaFechamento()
                || cartao.getDiaVencimento() != entrada.diaVencimento()) && faturas.existsByCartaoId(id)) {
            throw ApiException.conflito("CALENDARIO_EM_USO",
                    "O calendário não pode ser alterado após a criação de faturas. Cadastre outro cartão para um novo calendário.");
        }
        preencher(usuarioId, cartao, entrada);
        return saida(cartao, utilizado);
    }

    @Transactional
    public void desativar(UUID usuarioId, UUID id) {
        referencias.bloquearUsuario(usuarioId);
        referencias.cartao(usuarioId, id, false).setAtivo(false);
    }

    private void preencher(UUID usuarioId, CartaoCredito cartao, CartaoDto.Entrada entrada) {
        calculos.datas(YearMonth.from(calculos.hoje()), entrada.diaFechamento(), entrada.diaVencimento());
        cartao.setNome(entrada.nome().trim());
        cartao.setFinalCartao(entrada.finalCartao());
        cartao.setContaPagamento(entrada.contaPagamentoId() == null ? null
                : referencias.conta(usuarioId, entrada.contaPagamentoId(), true));
        cartao.setLimite(calculos.moeda(entrada.limite(), true));
        cartao.setDiaFechamento(entrada.diaFechamento());
        cartao.setDiaVencimento(entrada.diaVencimento());
    }

    private CartaoDto.Saida saida(CartaoCredito cartao, BigDecimal utilizado) {
        return new CartaoDto.Saida(cartao.getId(), cartao.getUsuario().getId(),
                cartao.getContaPagamento() == null ? null : cartao.getContaPagamento().getId(),
                cartao.getNome(), cartao.getFinalCartao(), cartao.getLimite(), cartao.getDiaFechamento(),
                cartao.getDiaVencimento(), cartao.getAtivo(), utilizado, cartao.getLimite().subtract(utilizado));
    }
}
