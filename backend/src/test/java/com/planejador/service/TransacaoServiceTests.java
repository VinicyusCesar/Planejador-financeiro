package com.planejador.service;

import com.planejador.dto.TransacaoDto;
import com.planejador.enums.*;
import com.planejador.exception.ApiException;
import com.planejador.model.*;
import com.planejador.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransacaoServiceTests {
    @Mock private TransacaoRepository transacoes;
    @Mock private ParcelaRepository parcelas;
    @Mock private ConsultasFinanceirasRepository consultas;
    @Mock private ReferenciaService referencias;
    @Mock private FaturaService faturas;
    private TransacaoService service;
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID categoriaId = UUID.randomUUID();
    private final UUID contaId = UUID.randomUUID();
    private final UUID cartaoId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.of(2026, 10, 26);

    @BeforeEach
    void preparar() {
        service = new TransacaoService(transacoes, parcelas, consultas, referencias,
                new CalculosFinanceirosService(Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"),
                        ZoneId.of("America/Sao_Paulo"))), faturas);
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        when(referencias.bloquearUsuario(usuarioId)).thenReturn(usuario);
    }

    private void categoria() {
        Categoria categoria = new Categoria();
        categoria.setId(categoriaId);
        categoria.setTipo(TipoCategoria.AMBOS);
        when(referencias.categoria(usuarioId, categoriaId)).thenReturn(categoria);
    }

    private void persistencia() {
        when(transacoes.saveAndFlush(any())).thenAnswer(invocacao -> {
            Transacao transacao = invocacao.getArgument(0);
            transacao.setId(UUID.randomUUID());
            return transacao;
        });
    }

    @ParameterizedTest
    @CsvSource({"RECEITA,CONFIRMADA", "DESPESA,CONFIRMADA", "DESPESA,PENDENTE"})
    void processaReceitaDespesaEPendente(TipoTransacao tipo, SituacaoTransacao situacao) {
        categoria();
        persistencia();
        Conta conta = new Conta();
        conta.setId(contaId);
        when(referencias.conta(usuarioId, contaId, true)).thenReturn(conta);
        var entrada = new TransacaoDto.Entrada(categoriaId, contaId, null, "Movimento", new BigDecimal("10.00"),
                tipo, MeioPagamento.PIX, null, null, hoje, null, null, situacao);
        var saida = service.criar(usuarioId, entrada);
        assertThat(saida.situacao()).isEqualTo(situacao);
        assertThat(saida.dataLiquidacao()).isEqualTo(situacao == SituacaoTransacao.PENDENTE ? null : hoje);
        verifyNoInteractions(faturas, consultas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00", "1.001"})
    void rejeitaValorInvalidoAntesDePersistir(String valor) {
        var entrada = new TransacaoDto.Entrada(categoriaId, contaId, null, "Movimento", new BigDecimal(valor),
                TipoTransacao.DESPESA, MeioPagamento.PIX, null, null, hoje, null, null, null);
        assertThatThrownBy(() -> service.criar(usuarioId, entrada)).isInstanceOf(ApiException.class);
        verifyNoInteractions(transacoes, parcelas, consultas, faturas);
    }

    @Test
    void rejeitaContaInexistente() {
        categoria();
        when(referencias.conta(usuarioId, contaId, true)).thenThrow(ApiException.naoEncontrado("Conta"));
        var entrada = new TransacaoDto.Entrada(categoriaId, contaId, null, "Movimento", new BigDecimal("10.00"),
                TipoTransacao.DESPESA, MeioPagamento.PIX, null, null, hoje, null, null, null);
        assertThatThrownBy(() -> service.criar(usuarioId, entrada)).isInstanceOf(ApiException.class);
        verifyNoInteractions(transacoes);
    }

    @Test
    void rejeitaCreditoSemCartao() {
        categoria();
        var entrada = new TransacaoDto.Entrada(categoriaId, null, null, "Movimento", new BigDecimal("10.00"),
                TipoTransacao.DESPESA, MeioPagamento.CREDITO, null, null, hoje, null, null, null);
        assertThatThrownBy(() -> service.criar(usuarioId, entrada)).isInstanceOf(ApiException.class)
                .satisfies(erro -> assertThat(((ApiException) erro).getCodigo()).isEqualTo("CREDITO_INVALIDO"));
    }

    @Test
    void creditoConfirmadoAcionaGeracaoDeParcelas() {
        categoria();
        persistencia();
        CartaoCredito cartao = new CartaoCredito();
        cartao.setId(cartaoId);
        cartao.setDiaFechamento(25);
        cartao.setDiaVencimento(5);
        cartao.setLimite(new BigDecimal("1000.00"));
        when(referencias.cartao(usuarioId, cartaoId, true)).thenReturn(cartao);
        when(consultas.limiteUtilizado(usuarioId, cartaoId, hoje)).thenReturn(new BigDecimal("0.00"));
        var entrada = new TransacaoDto.Entrada(categoriaId, null, cartaoId, "Crédito", new BigDecimal("100.00"),
                TipoTransacao.DESPESA, MeioPagamento.CREDITO, CondicaoPagamento.PARCELADO, 3,
                hoje, null, null, null);
        service.criar(usuarioId, entrada);
        verify(faturas).gerarParcelas(any(Transacao.class));
    }
}
