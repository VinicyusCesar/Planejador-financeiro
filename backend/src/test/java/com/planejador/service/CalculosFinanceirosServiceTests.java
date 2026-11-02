package com.planejador.service;

import com.planejador.enums.SituacaoFatura;
import com.planejador.exception.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculosFinanceirosServiceTests {
    private final CalculosFinanceirosService service = new CalculosFinanceirosService(
            Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    void distribuiMilEDuzentosEmTresParcelas() {
        assertThat(service.parcelar(new BigDecimal("1200.00"), 3))
                .containsExactly(new BigDecimal("400.00"), new BigDecimal("400.00"), new BigDecimal("400.00"));
    }

    @Test
    void distribuiCentavosSemPerderValor() {
        var parcelas = service.parcelar(new BigDecimal("100.00"), 3);
        assertThat(parcelas).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(parcelas.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test
    void compraAVistaNoCreditoGeraParcelaUnica() {
        assertThat(service.parcelar(new BigDecimal("12.34"), 1)).containsExactly(new BigDecimal("12.34"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 601})
    void rejeitaQuantidadeInvalida(int quantidade) {
        assertThatThrownBy(() -> service.parcelar(new BigDecimal("100.00"), quantidade)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00", "1.001", "10000000000000.00"})
    void rejeitaValorInvalido(String valor) {
        assertThatThrownBy(() -> service.parcelar(new BigDecimal(valor), 1)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejeitaParcelaDeZeroCentavos() {
        assertThatThrownBy(() -> service.parcelar(new BigDecimal("0.01"), 2)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-20,2026-10,2026-11-05",
            "2026-10-25,2026-10,2026-11-05",
            "2026-10-27,2026-11,2026-12-05",
            "2026-12-27,2027-01,2027-02-05"
    })
    void distribuiConformeFechamento(String compra, String referencia, String vencimento) {
        var ciclo = service.ciclo(LocalDate.parse(compra), 25, 5);
        assertThat(ciclo.referencia()).isEqualTo(YearMonth.parse(referencia));
        assertThat(ciclo.vencimento()).isEqualTo(LocalDate.parse(vencimento));
    }

    @ParameterizedTest
    @CsvSource({"2026-02,2026-02-28", "2028-02,2028-02-29", "2026-04,2026-04-30"})
    void ajustaFechamentoEmMesesCurtos(String mes, String fechamento) {
        assertThat(service.datas(YearMonth.parse(mes), 31, 5).fechamento()).isEqualTo(LocalDate.parse(fechamento));
    }

    @Test
    void vencimentoSempreOcorreDepoisDoFechamentoMesmoEmFevereiro() {
        var ciclo = service.datas(YearMonth.of(2026, 2), 28, 31);
        assertThat(ciclo.vencimento()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void permiteVencimentoNoMesmoMesQuandoPosteriorAoFechamento() {
        assertThat(service.datas(YearMonth.of(2026, 10), 5, 25).vencimento()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void comparaSemanasSemDividirPorZero() {
        assertThat(service.variacao(new BigDecimal("100.00"), BigDecimal.ZERO)).isNull();
        assertThat(service.variacao(new BigDecimal("150.00"), new BigDecimal("100.00"))).isEqualByComparingTo("50.00");
    }

    @Test
    void reconheceFaturaPagaEAtrasada() {
        assertThat(service.situacao(new BigDecimal("100"), new BigDecimal("100"),
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 5))).isEqualTo(SituacaoFatura.PAGA);
        assertThat(service.situacao(new BigDecimal("100"), new BigDecimal("50"),
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 5))).isEqualTo(SituacaoFatura.ATRASADA);
    }
}
