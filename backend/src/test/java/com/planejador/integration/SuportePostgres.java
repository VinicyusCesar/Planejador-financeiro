package com.planejador.integration;

import com.planejador.BackendApplication;
import com.planejador.dto.*;
import com.planejador.enums.*;
import com.planejador.service.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
@Import(SuportePostgres.RelogioTeste.class)
abstract class SuportePostgres {
    protected static final LocalDate HOJE = LocalDate.of(2026, 10, 26);
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UsuarioService usuarios;
    @Autowired protected ContaService contas;
    @Autowired protected CategoriaService categorias;
    @Autowired protected CartaoService cartoes;
    @Autowired protected TransacaoService transacoes;
    @Autowired protected FaturaService faturas;
    @Autowired protected TransferenciaService transferencias;
    @Autowired protected OrcamentoService orcamentos;
    @Autowired protected MetaService metas;
    @Autowired protected DashboardService dashboard;
    @Autowired protected EntityManager entityManager;
    @Autowired protected PlatformTransactionManager gerenciadorTransacoes;
    protected UUID usuarioId;
    protected UUID contaId;
    protected UUID destinoId;
    protected UUID despesaId;
    protected UUID receitaId;
    protected UUID cartaoId;

    @TestConfiguration(proxyBeanMethods = false)
    static class RelogioTeste {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @BeforeEach
    void preparar() {
        new TransactionTemplate(gerenciadorTransacoes).executeWithoutResult(status -> {
        usuarioId = usuarios.criar(new UsuarioDto.Entrada("Usuário de teste", UUID.randomUUID() + "@teste.local")).id();
        contaId = contas.criar(usuarioId, new ContaDto.Entrada("Principal", TipoConta.CORRENTE, valor("1000.00"))).id();
        destinoId = contas.criar(usuarioId, new ContaDto.Entrada("Reserva", TipoConta.POUPANCA, valor("0.00"))).id();
        despesaId = categorias.criar(usuarioId, new CategoriaDto.Entrada("Alimentação", TipoCategoria.DESPESA)).id();
        receitaId = categorias.criar(usuarioId, new CategoriaDto.Entrada("Salário", TipoCategoria.RECEITA)).id();
        cartaoId = cartoes.criar(usuarioId, new CartaoDto.Entrada("Cartão", "1234", contaId,
                valor("5000.00"), 25, 5)).id();
        entityManager.flush();
        });
    }

    protected static BigDecimal valor(String valor) { return new BigDecimal(valor); }

    protected TransacaoDto.Entrada direta(TipoTransacao tipo, String valor, LocalDate data, SituacaoTransacao situacao) {
        return new TransacaoDto.Entrada(tipo == TipoTransacao.RECEITA ? receitaId : despesaId,
                contaId, null, "Movimento", valor(valor), tipo, MeioPagamento.PIX,
                CondicaoPagamento.AVISTA, 1, data, null, null, situacao);
    }

    protected TransacaoDto.Entrada credito(String valor, int quantidade, LocalDate data) {
        return new TransacaoDto.Entrada(despesaId, null, cartaoId, "Compra no crédito", valor(valor),
                TipoTransacao.DESPESA, MeioPagamento.CREDITO,
                quantidade == 1 ? CondicaoPagamento.AVISTA : CondicaoPagamento.PARCELADO,
                quantidade, data, null, null, SituacaoTransacao.CONFIRMADA);
    }

    protected UUID primeiraFatura(UUID transacaoId) {
        return transacoes.parcelas(usuarioId, transacaoId).getFirst().faturaId();
    }
}
