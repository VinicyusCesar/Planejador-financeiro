package com.planejador.integration;

import com.planejador.dto.*;
import com.planejador.enums.*;
import com.planejador.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@EnabledIfEnvironmentVariable(named = "SQLSERVER_TEST_ENABLED", matches = "true")
class SqlServerIntegrationTests extends SuporteSqlServer {
    @Test
    void preservaLimiteDeCaracteresUnicodeDoNome() {
        String nome = "é".repeat(100);
        var conta = contas.criar(usuarioId, new ContaDto.Entrada(nome, TipoConta.CORRENTE, valor("0.00")));
        assertThat(contas.consultar(usuarioId, conta.id()).nome()).isEqualTo(nome);
    }

    @Test
    void rejeitaCategoriaIncompativelComTipoFinanceiro() {
        var entrada = new TransacaoDto.Entrada(despesaId, contaId, null, "Categoria incorreta", valor("10.00"),
                TipoTransacao.RECEITA, MeioPagamento.PIX, CondicaoPagamento.AVISTA, 1,
                HOJE, null, null, SituacaoTransacao.CONFIRMADA);
        assertThatThrownBy(() -> transacoes.criar(usuarioId, entrada))
                .isInstanceOfSatisfying(ApiException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("CATEGORIA_INCOMPATIVEL"));
    }

    @Test
    void agregacoesPreservamCentavosNoSaldoENaDivida() {
        transacoes.criar(usuarioId, direta(TipoTransacao.RECEITA, "0.02", HOJE, SituacaoTransacao.CONFIRMADA));
        transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "0.01", HOJE, SituacaoTransacao.CONFIRMADA));
        var compra = transacoes.criar(usuarioId, credito("100.02", 1, HOJE));
        UUID faturaId = primeiraFatura(compra.id());
        faturas.pagar(usuarioId, faturaId, new FaturaDto.PagamentoEntrada(contaId, valor("0.01"), HOJE));
        assertThat(faturas.consultar(usuarioId, faturaId).resumo().saldoPendente()).isEqualByComparingTo("100.01");
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1000.00");
    }

    @Test
    void validaMapeamentosEConexaoComSqlServerIsolado() {
        Object banco = entityManager.createNativeQuery("SELECT DB_NAME()").getSingleResult();
        assertThat(banco.toString()).isEqualTo("PlanejadorFinanceiroTest");
        Number tabelas = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'dbo' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'")
                .getSingleResult();
        assertThat(tabelas.intValue()).isEqualTo(12);
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1000.00");
    }

    @Test
    void healthExecutaConsultaRealPeloEndpoint() throws Exception {
        mvc.perform(get("/api/health/database"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.database").value("PlanejadorFinanceiroTest"))
                .andExpect(jsonPath("$.schema").value("dbo"))
                .andExpect(jsonPath("$.user").isNotEmpty());
    }

    @Test
    void saldoExatoDoCenarioDeAceite() {
        transacoes.criar(usuarioId, direta(TipoTransacao.RECEITA, "500.00", HOJE, SituacaoTransacao.CONFIRMADA));
        transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "150.00", HOJE, SituacaoTransacao.CONFIRMADA));
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1350.00");
    }

    @Test
    void receitaLiquidadaAumentaSaldo() {
        transacoes.criar(usuarioId, direta(TipoTransacao.RECEITA, "250.00", HOJE, SituacaoTransacao.CONFIRMADA));
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1250.00");
        assertThat(dashboard.mensal(usuarioId, 2026, 10).receitas()).isEqualByComparingTo("250.00");
    }

    @Test
    void despesaPixLiquidadaReduzSaldo() {
        var transacao = transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "200.00", HOJE, SituacaoTransacao.CONFIRMADA));
        assertThat(transacao.dataLiquidacao()).isEqualTo(HOJE);
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("800.00");
    }

    @Test
    void pendenteNaoAfetaSaldoAteConfirmacaoECancelamentoReverte() {
        var transacao = transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "100.00", HOJE, SituacaoTransacao.PENDENTE));
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1000.00");
        assertThat(dashboard.mensal(usuarioId, 2026, 10).despesas()).isEqualByComparingTo("0.00");
        transacoes.atualizar(usuarioId, transacao.id(), direta(TipoTransacao.DESPESA, "100.00", HOJE, SituacaoTransacao.CONFIRMADA));
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("900.00");
        transacoes.cancelar(usuarioId, transacao.id());
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1000.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00", "1.001"})
    void apiRejeitaValorInvalido(String valor) throws Exception {
        mvc.perform(post("/api/transacoes").header("X-Usuario-Id", usuarioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(direta(TipoTransacao.DESPESA, valor, HOJE, SituacaoTransacao.CONFIRMADA))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void contaInexistenteRetorna404() throws Exception {
        var entrada = new TransacaoDto.Entrada(despesaId, UUID.randomUUID(), null, "PIX", valor("10.00"),
                TipoTransacao.DESPESA, MeioPagamento.PIX, null, null, HOJE, null, null, null);
        mvc.perform(post("/api/transacoes").header("X-Usuario-Id", usuarioId)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(entrada)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void creditoSemCartaoRetorna400() throws Exception {
        var entrada = new TransacaoDto.Entrada(despesaId, null, null, "CrÃ©dito", valor("10.00"),
                TipoTransacao.DESPESA, MeioPagamento.CREDITO, null, null, HOJE, null, null, null);
        mvc.perform(post("/api/transacoes").header("X-Usuario-Id", usuarioId)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(entrada)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("CREDITO_INVALIDO"));
    }

    @Test
    void impedeUsoDeContaDeOutroUsuario() {
        UUID outroUsuario = usuarios.criar(new UsuarioDto.Entrada("Outro", UUID.randomUUID() + "@teste.local")).id();
        UUID outraConta = contas.criar(outroUsuario, new ContaDto.Entrada("Outra", TipoConta.CORRENTE, valor("0"))).id();
        var entrada = new TransacaoDto.Entrada(despesaId, outraConta, null, "PIX", valor("10"),
                TipoTransacao.DESPESA, MeioPagamento.PIX, null, null, HOJE, null, null, null);
        assertThatThrownBy(() -> transacoes.criar(usuarioId, entrada)).isInstanceOf(ApiException.class)
                .hasMessage("Conta nÃ£o encontrado.");
    }

    @Test
    void compraAVistaNoCreditoNaoDebitaConta() {
        var compra = transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
        var parcelas = transacoes.parcelas(usuarioId, compra.id());
        assertThat(parcelas).hasSize(1);
        assertThat(parcelas.getFirst().valor()).isEqualByComparingTo("100.00");
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1000.00");
        assertThat(faturas.consultar(usuarioId, parcelas.getFirst().faturaId()).resumo().total()).isEqualByComparingTo("100.00");
    }

    @Test
    void compraParceladaDistribuiCentavosFaturasELimite() {
        var compra = transacoes.criar(usuarioId, credito("100.00", 3, LocalDate.of(2026, 10, 20)));
        var parcelas = transacoes.parcelas(usuarioId, compra.id());
        assertThat(parcelas).extracting(TransacaoDto.ParcelaSaida::valor)
                .containsExactly(valor("33.34"), valor("33.33"), valor("33.33"));
        assertThat(parcelas).extracting(TransacaoDto.ParcelaSaida::dataVencimento)
                .containsExactly(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5));
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteUtilizado()).isEqualByComparingTo("100.00");
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteDisponivel()).isEqualByComparingTo("4900.00");
    }

    @Test
    void somaParcelasEPagamentosSeparadamenteSemMultiplicarNoJoin() {
        var primeira = transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
        var segunda = transacoes.criar(usuarioId, credito("50.00", 1, LocalDate.of(2026, 10, 21)));
        UUID faturaId = primeiraFatura(primeira.id());
        assertThat(primeiraFatura(segunda.id())).isEqualTo(faturaId);
        faturas.pagar(usuarioId, faturaId, new FaturaDto.PagamentoEntrada(contaId, valor("20.00"), HOJE));
        faturas.pagar(usuarioId, faturaId, new FaturaDto.PagamentoEntrada(contaId, valor("30.00"), HOJE));
        var fatura = faturas.consultar(usuarioId, faturaId);
        assertThat(fatura.resumo().total()).isEqualByComparingTo("150.00");
        assertThat(fatura.resumo().totalPago()).isEqualByComparingTo("50.00");
        assertThat(fatura.resumo().saldoPendente()).isEqualByComparingTo("100.00");
        assertThat(dashboard.mensal(usuarioId, 2026, 10).despesas()).isEqualByComparingTo("150.00");
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("950.00");
    }

    @Test
    void pagamentoParcialAtualizaSaldoPendenteSemDuplicarDespesa() {
        var compra = transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
        UUID faturaId = primeiraFatura(compra.id());
        faturas.pagar(usuarioId, faturaId, new FaturaDto.PagamentoEntrada(contaId, valor("30.00"), HOJE));
        assertThat(faturas.consultar(usuarioId, faturaId).resumo().saldoPendente()).isEqualByComparingTo("70.00");
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("970.00");
        assertThat(dashboard.mensal(usuarioId, 2026, 10).despesas()).isEqualByComparingTo("100.00");
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteUtilizado()).isEqualByComparingTo("70.00");
    }

    @Test
    void pagamentoTotalMarcaPagaELiberaLimite() {
        var compra = transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
        UUID faturaId = primeiraFatura(compra.id());
        faturas.pagar(usuarioId, faturaId, new FaturaDto.PagamentoEntrada(contaId, valor("100.00"), HOJE));
        var fatura = faturas.consultar(usuarioId, faturaId).resumo();
        assertThat(fatura.situacao()).isEqualTo(SituacaoFatura.PAGA);
        assertThat(fatura.saldoPendente()).isEqualByComparingTo("0.00");
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteDisponivel()).isEqualByComparingTo("5000.00");
    }

    @Test
    void pagamentoExcedenteRetorna409() throws Exception {
        var compra = transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
        mvc.perform(post("/api/faturas/" + primeiraFatura(compra.id()) + "/pagamentos")
                        .header("X-Usuario-Id", usuarioId).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new FaturaDto.PagamentoEntrada(contaId, valor("100.01"), HOJE))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGAMENTO_EXCEDENTE"));
    }

    @Test
    void pagamentoImpedeEdicaoECancelamentoDaCompra() {
        var compra = transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
        faturas.pagar(usuarioId, primeiraFatura(compra.id()), new FaturaDto.PagamentoEntrada(contaId, valor("10.00"), HOJE));
        assertThatThrownBy(() -> transacoes.atualizar(usuarioId, compra.id(), credito("90.00", 1, LocalDate.of(2026, 10, 20))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> transacoes.cancelar(usuarioId, compra.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void cancelamentoDeCreditoSemPagamentoLiberaLimite() {
        var compra = transacoes.criar(usuarioId, credito("100.00", 3, LocalDate.of(2026, 10, 20)));
        transacoes.cancelar(usuarioId, compra.id());
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteUtilizado()).isEqualByComparingTo("0.00");
        assertThat(dashboard.mensal(usuarioId, 2026, 10).despesas()).isEqualByComparingTo("0.00");
    }

    @Test
    void edicaoDeCompraRecriaParcelasSemDuplicar() {
        var compra = transacoes.criar(usuarioId, credito("100.00", 3, LocalDate.of(2026, 10, 20)));
        transacoes.atualizar(usuarioId, compra.id(), credito("60.00", 2, HOJE));
        var parcelas = transacoes.parcelas(usuarioId, compra.id());
        assertThat(parcelas).hasSize(2);
        assertThat(parcelas.getFirst().dataVencimento()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteUtilizado()).isEqualByComparingTo("60.00");
    }

    @Test
    void creditoPendenteNaoGeraParcelasNemConsomeLimite() {
        var entrada = credito("100.00", 3, HOJE);
        var pendente = new TransacaoDto.Entrada(entrada.categoriaId(), null, cartaoId, entrada.descricao(), entrada.valor(),
                entrada.tipo(), entrada.meioPagamento(), entrada.condicao(), entrada.quantidadeParcelas(), HOJE,
                null, null, SituacaoTransacao.PENDENTE);
        var compra = transacoes.criar(usuarioId, pendente);
        assertThat(transacoes.parcelas(usuarioId, compra.id())).isEmpty();
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteUtilizado()).isEqualByComparingTo("0.00");
    }

    @Test
    void transferenciaMoveSaldoSemGerarReceitaOuDespesaECancela() {
        var transferencia = transferencias.criar(usuarioId,
                new TransferenciaDto.Entrada(contaId, destinoId, valor("200.00"), HOJE, null));
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("800.00");
        assertThat(contas.saldo(usuarioId, destinoId).saldoAtual()).isEqualByComparingTo("200.00");
        assertThat(dashboard.mensal(usuarioId, 2026, 10).receitas()).isEqualByComparingTo("0.00");
        assertThat(dashboard.mensal(usuarioId, 2026, 10).despesas()).isEqualByComparingTo("0.00");
        transferencias.cancelar(usuarioId, transferencia.id());
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1000.00");
        assertThat(contas.saldo(usuarioId, destinoId).saldoAtual()).isEqualByComparingTo("0.00");
    }

    @Test
    void transferenciaImpedeOrigemIgualAoDestino() {
        assertThatThrownBy(() -> transferencias.criar(usuarioId,
                new TransferenciaDto.Entrada(contaId, contaId, valor("10.00"), HOJE, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void transferenciaPendenteSoMovimentaAposConfirmacao() {
        var transferencia = transferencias.criar(usuarioId,
                new TransferenciaDto.Entrada(contaId, destinoId, valor("100.00"), HOJE, SituacaoTransacao.PENDENTE));
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("1000.00");
        transferencias.confirmar(usuarioId, transferencia.id());
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("900.00");
    }

    @Test
    void orcamentoComparaPrevistoRealizadoEDespesasNaoOrcadas() {
        transacoes.criar(usuarioId, direta(TipoTransacao.RECEITA, "1000.00", HOJE, SituacaoTransacao.CONFIRMADA));
        transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "100.00", HOJE, SituacaoTransacao.CONFIRMADA));
        transacoes.criar(usuarioId, credito("120.00", 3, HOJE));
        UUID transporte = categorias.criar(usuarioId, new CategoriaDto.Entrada("Transporte", TipoCategoria.DESPESA)).id();
        transacoes.criar(usuarioId, new TransacaoDto.Entrada(transporte, contaId, null, "Ã”nibus", valor("50.00"),
                TipoTransacao.DESPESA, MeioPagamento.DINHEIRO, null, null, HOJE, null, null, null));
        var orcamento = orcamentos.criar(usuarioId, new OrcamentoDto.Entrada(2026, 10, valor("100.00"),
                List.of(new OrcamentoDto.ItemEntrada(despesaId, valor("250.00")))));
        assertThat(orcamento.totalPrevisto()).isEqualByComparingTo("250.00");
        assertThat(orcamento.totalRealizado()).isEqualByComparingTo("270.00");
        assertThat(orcamento.realizadoSemPrevisao()).isEqualByComparingTo("50.00");
        assertThat(orcamento.diferenca()).isEqualByComparingTo("-20.00");
        assertThat(orcamento.itens().getFirst().realizado()).isEqualByComparingTo("220.00");
        var mensal = dashboard.mensal(usuarioId, 2026, 10);
        assertThat(mensal.saldoDoMes()).isEqualByComparingTo("730.00");
        assertThat(mensal.gastosPorCategoria()).hasSize(2);
        assertThat(mensal.orcamentoPrevisto()).isEqualByComparingTo("250.00");
    }

    @Test
    void resumoSemanalComecaNaSegundaEComparaComAnterior() {
        transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "100.00", HOJE, SituacaoTransacao.CONFIRMADA));
        transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "50.00", HOJE.minusWeeks(1), SituacaoTransacao.CONFIRMADA));
        var semanal = dashboard.semanal(usuarioId, HOJE);
        assertThat(semanal.inicio()).isEqualTo(HOJE);
        assertThat(semanal.fim()).isEqualTo(HOJE.plusDays(6));
        assertThat(semanal.totalSemana()).isEqualByComparingTo("100.00");
        assertThat(semanal.totalSemanaAnterior()).isEqualByComparingTo("50.00");
        assertThat(semanal.diferenca()).isEqualByComparingTo("50.00");
        assertThat(semanal.variacaoPercentual()).isEqualByComparingTo("100.00");
        assertThat(semanal.gastosPorDia()).hasSize(7);
        assertThat(semanal.gastosPorDia().getFirst().total()).isEqualByComparingTo("100.00");
    }

    @Test
    void semanaSemBaseNaoProduzPercentualFicticio() {
        var semanal = dashboard.semanal(usuarioId, HOJE);
        assertThat(semanal.variacaoPercentual()).isNull();
        assertThat(semanal.comparacaoPercentualDisponivel()).isFalse();
    }

    @Test
    void metaNaoInventaProgressoEPermiteAtualizacaoDeclarada() {
        var meta = metas.criar(usuarioId, new MetaDto.Entrada("Reserva", valor("10000.00"), HOJE, HOJE.plusYears(1), null));
        assertThat(meta.progressoDisponivel()).isFalse();
        assertThat(meta.valorAcumulado()).isNull();
        assertThat(meta.percentual()).isNull();
        assertThat(metas.atualizar(usuarioId, meta.id(), new MetaDto.Entrada("Reserva", valor("10000.00"),
                HOJE, HOJE.plusYears(1), SituacaoMeta.CONCLUIDA)).situacao()).isEqualTo(SituacaoMeta.CONCLUIDA);
    }

    @Test
    void categoriaSemDependenciasPodeSerExcluida() {
        UUID id = categorias.criar(usuarioId, new CategoriaDto.Entrada("Livre", TipoCategoria.AMBOS)).id();
        categorias.excluir(usuarioId, id);
        assertThat(categorias.listar(usuarioId, null)).noneMatch(categoria -> categoria.id().equals(id));
    }

    @Test
    void categoriaComTransacaoNaoPodeSerExcluida() {
        transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "10.00", HOJE, SituacaoTransacao.CONFIRMADA));
        assertThatThrownBy(() -> categorias.excluir(usuarioId, despesaId)).isInstanceOf(ApiException.class);
    }

    @Test
    void filtrosDeTransacoesSaoAplicadosNoBancoComPaginacao() {
        transacoes.criar(usuarioId, direta(TipoTransacao.DESPESA, "10.00", HOJE, SituacaoTransacao.CONFIRMADA));
        transacoes.criar(usuarioId, direta(TipoTransacao.RECEITA, "50.00", HOJE.minusDays(1), SituacaoTransacao.CONFIRMADA));
        var pagina = transacoes.listar(usuarioId, HOJE, HOJE, TipoTransacao.DESPESA, contaId,
                null, despesaId, SituacaoTransacao.CONFIRMADA, 0, 1);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.conteudo().getFirst().valor()).isEqualByComparingTo("10.00");
    }

    @Test
    void listaFaturasAtuaisAnterioresEProximasSemCriarNovas() {
        transacoes.criar(usuarioId, credito("120.00", 3, LocalDate.of(2026, 10, 20)));
        var atual = faturas.listar(usuarioId, cartaoId, null, null, PeriodoFatura.ATUAL);
        var anteriores = faturas.listar(usuarioId, cartaoId, null, null, PeriodoFatura.ANTERIORES);
        var proximas = faturas.listar(usuarioId, cartaoId, null, null, PeriodoFatura.PROXIMAS);
        assertThat(atual).hasSize(1);
        assertThat(atual.getFirst().mesReferencia()).isEqualTo(11);
        assertThat(anteriores).hasSize(1);
        assertThat(anteriores.getFirst().mesReferencia()).isEqualTo(10);
        assertThat(proximas).hasSize(1);
        assertThat(proximas.getFirst().mesReferencia()).isEqualTo(12);
        var cartao = dashboard.cartoes(usuarioId).getFirst();
        assertThat(cartao.faturaAtual().total()).isEqualByComparingTo("40.00");
        assertThat(cartao.faturaSeguinte().total()).isEqualByComparingTo("40.00");
        assertThat(cartao.totalProximasFaturas()).isEqualByComparingTo("40.00");
        assertThat(cartao.limiteUtilizado()).isEqualByComparingTo("120.00");
    }

    @Test
    void endpointsValidamCabecalhoEnumsEPaginacao() throws Exception {
        mvc.perform(get("/api/contas")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/transacoes").header("X-Usuario-Id", usuarioId).param("tamanho", "1000"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/resumo-mensal").header("X-Usuario-Id", usuarioId)
                        .param("ano", "2026").param("mes", "13"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/categorias").header("X-Usuario-Id", usuarioId).param("tipo", "INVALIDO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criacaoPorHttpRetorna201EDtoComLocation() throws Exception {
        mvc.perform(post("/api/contas").header("X-Usuario-Id", usuarioId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Dinheiro\",\"tipo\":\"DINHEIRO\",\"saldoInicial\":25.00}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.saldoInicial").value(25.00));
    }

    @Test
    void contasInativasNaoRecebemNovasMovimentacoes() {
        contas.desativar(usuarioId, contaId);
        assertThatThrownBy(() -> transacoes.criar(usuarioId,
                direta(TipoTransacao.DESPESA, "10.00", HOJE, SituacaoTransacao.CONFIRMADA)))
                .isInstanceOf(ApiException.class);
    }
}

