package com.planejador.service;

import com.planejador.enums.SituacaoFatura;
import com.planejador.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class CalculosFinanceirosService {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAXIMO = new BigDecimal("9999999999999.99");
    private final Clock clock;

    public CalculosFinanceirosService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoje() { return LocalDate.now(clock); }

    public BigDecimal moeda(BigDecimal valor, boolean aceitarZero) {
        if (valor == null || (aceitarZero ? valor.signum() < 0 : valor.signum() <= 0)
                || valor.abs().compareTo(MAXIMO) > 0) {
            throw ApiException.invalido("VALOR_INVALIDO", "Informe um valor monetário válido.");
        }
        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw ApiException.invalido("VALOR_INVALIDO", "Utilize no máximo duas casas decimais.");
        }
    }

    public List<BigDecimal> parcelar(BigDecimal total, int quantidade) {
        BigDecimal valor = moeda(total, false);
        if (quantidade < 1 || quantidade > 600) {
            throw ApiException.invalido("PARCELAMENTO_INVALIDO", "Informe entre 1 e 600 parcelas.");
        }
        long centavos = valor.movePointRight(2).longValueExact();
        if (centavos < quantidade) {
            throw ApiException.invalido("PARCELAMENTO_INVALIDO", "Cada parcela deve ter pelo menos um centavo.");
        }
        long base = centavos / quantidade;
        long resto = centavos % quantidade;
        return IntStream.range(0, quantidade)
                .mapToObj(indice -> BigDecimal.valueOf(base + (indice < resto ? 1 : 0), 2)).toList();
    }

    public record Ciclo(YearMonth referencia, LocalDate fechamento, LocalDate vencimento) {}

    public Ciclo ciclo(LocalDate compra, int diaFechamento, int diaVencimento) {
        validarDias(diaFechamento, diaVencimento);
        if (compra == null || compra.getYear() < 2000 || compra.getYear() > 9998) {
            throw ApiException.invalido("DATA_INVALIDA", "A compra no crédito deve estar entre os anos 2000 e 9998.");
        }
        YearMonth referencia = YearMonth.from(compra);
        if (compra.isAfter(dia(referencia, diaFechamento))) {
            referencia = referencia.plusMonths(1);
        }
        return datas(referencia, diaFechamento, diaVencimento);
    }

    public Ciclo datas(YearMonth referencia, int diaFechamento, int diaVencimento) {
        validarDias(diaFechamento, diaVencimento);
        LocalDate fechamento = dia(referencia, diaFechamento);
        LocalDate vencimento = dia(referencia, diaVencimento);
        if (!vencimento.isAfter(fechamento)) {
            vencimento = dia(referencia.plusMonths(1), diaVencimento);
        }
        return new Ciclo(referencia, fechamento, vencimento);
    }

    private LocalDate dia(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    private void validarDias(int fechamento, int vencimento) {
        if (fechamento < 1 || fechamento > 31 || vencimento < 1 || vencimento > 31) {
            throw ApiException.invalido("DIA_INVALIDO", "Fechamento e vencimento devem estar entre 1 e 31.");
        }
    }

    public SituacaoFatura situacao(BigDecimal total, BigDecimal pago, LocalDate fechamento, LocalDate vencimento) {
        if (total.signum() > 0 && pago.compareTo(total) >= 0) return SituacaoFatura.PAGA;
        if (total.signum() > 0 && hoje().isAfter(vencimento)) return SituacaoFatura.ATRASADA;
        if (hoje().isAfter(fechamento)) return SituacaoFatura.FECHADA;
        return SituacaoFatura.ABERTA;
    }

    public BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior.signum() == 0) return null;
        return atual.subtract(anterior).multiply(new BigDecimal("100")).divide(anterior, 2, RoundingMode.HALF_UP);
    }

    public void periodo(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw ApiException.invalido("PERIODO_INVALIDO", "A data inicial deve ser anterior ou igual à final.");
        }
    }
}
