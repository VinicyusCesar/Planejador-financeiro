package com.planejador.integration;

import com.planejador.dto.CartaoDto;
import com.planejador.dto.FaturaDto;
import com.planejador.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:(?!5432/)\\d+/planejador_financeiro_test(?:\\?.*)?")
class ConcorrenciaPostgresTests extends SuportePostgres {
    @Test
    void pagamentosConcorrentesNaoUltrapassamDivida() throws Exception {
        var compra = transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
        UUID faturaId = primeiraFatura(compra.id());
        List<String> resultados = disputar(() -> {
            faturas.pagar(usuarioId, faturaId, new FaturaDto.PagamentoEntrada(contaId, valor("80.00"), HOJE));
            return "OK";
        });
        assertThat(resultados).containsExactlyInAnyOrder("OK", "PAGAMENTO_EXCEDENTE");
        assertThat(faturas.consultar(usuarioId, faturaId).resumo().totalPago()).isEqualByComparingTo("80.00");
        assertThat(contas.saldo(usuarioId, contaId).saldoAtual()).isEqualByComparingTo("920.00");
    }

    @Test
    void comprasConcorrentesReutilizamMesmaFatura() throws Exception {
        List<String> resultados = disputar(() -> {
            transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
            return "OK";
        });
        assertThat(resultados).containsExactly("OK", "OK");
        var lista = faturas.listar(usuarioId, cartaoId, null, null, null);
        assertThat(lista).hasSize(1);
        assertThat(lista.getFirst().total()).isEqualByComparingTo("200.00");
    }

    @Test
    void comprasConcorrentesRespeitamLimiteDisponivel() throws Exception {
        cartoes.atualizar(usuarioId, cartaoId, new CartaoDto.Entrada("Cartão", "1234", contaId, valor("150.00"), 25, 5));
        List<String> resultados = disputar(() -> {
            transacoes.criar(usuarioId, credito("100.00", 1, LocalDate.of(2026, 10, 20)));
            return "OK";
        });
        assertThat(resultados).containsExactlyInAnyOrder("OK", "LIMITE_INSUFICIENTE");
        assertThat(cartoes.consultar(usuarioId, cartaoId).limiteUtilizado()).isEqualByComparingTo("100.00");
    }

    private List<String> disputar(Callable<String> operacao) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        Callable<String> tarefa = () -> {
            if (!inicio.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Espera de teste excedida");
            try {
                return operacao.call();
            } catch (ApiException exception) {
                return exception.getCodigo();
            }
        };
        try {
            var primeira = executor.submit(tarefa);
            var segunda = executor.submit(tarefa);
            inicio.countDown();
            return List.of(primeira.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
