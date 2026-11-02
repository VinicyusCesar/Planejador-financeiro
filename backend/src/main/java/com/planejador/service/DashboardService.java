package com.planejador.service;

import com.planejador.dto.DashboardDto;
import com.planejador.model.CartaoCredito;
import com.planejador.repository.CartaoCreditoRepository;
import com.planejador.repository.ConsultasFinanceirasRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final ConsultasFinanceirasRepository consultas;
    private final CartaoCreditoRepository cartoes;
    private final ReferenciaService referencias;
    private final CalculosFinanceirosService calculos;
    private final FaturaService faturas;

    public DashboardService(ConsultasFinanceirasRepository consultas, CartaoCreditoRepository cartoes,
            ReferenciaService referencias, CalculosFinanceirosService calculos, FaturaService faturas) {
        this.consultas = consultas;
        this.cartoes = cartoes;
        this.referencias = referencias;
        this.calculos = calculos;
        this.faturas = faturas;
    }

    public DashboardDto.Mensal mensal(UUID usuarioId, int ano, int mes) {
        referencias.usuario(usuarioId);
        YearMonth periodo = YearMonth.of(ano, mes);
        LocalDate inicio = periodo.atDay(1);
        LocalDate fim = periodo.plusMonths(1).atDay(1);
        var totais = consultas.totais(usuarioId, inicio, fim);
        BigDecimal previsto = consultas.previsto(usuarioId, ano, mes);
        return new DashboardDto.Mensal(ano, mes, totais.receitas(), totais.despesas(),
                totais.receitas().subtract(totais.despesas()), consultas.categorias(usuarioId, inicio, fim),
                previsto, totais.despesas(), previsto.subtract(totais.despesas()));
    }

    public DashboardDto.Semanal semanal(UUID usuarioId, LocalDate referencia) {
        referencias.usuario(usuarioId);
        LocalDate inicio = (referencia == null ? calculos.hoje() : referencia)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fim = inicio.plusDays(7);
        BigDecimal atual = consultas.totais(usuarioId, inicio, fim).despesas();
        BigDecimal anterior = consultas.totais(usuarioId, inicio.minusWeeks(1), inicio).despesas();
        Map<LocalDate, BigDecimal> valores = consultas.dias(usuarioId, inicio, fim).stream()
                .collect(Collectors.toMap(DashboardDto.DiaGasto::data, DashboardDto.DiaGasto::total));
        List<DashboardDto.DiaGasto> dias = IntStream.range(0, 7).mapToObj(indice -> {
            LocalDate data = inicio.plusDays(indice);
            return new DashboardDto.DiaGasto(data, valores.getOrDefault(data, CalculosFinanceirosService.ZERO));
        }).toList();
        BigDecimal variacao = calculos.variacao(atual, anterior);
        return new DashboardDto.Semanal(inicio, fim.minusDays(1), atual, anterior, atual.subtract(anterior),
                variacao, variacao != null, dias);
    }

    public List<DashboardDto.Cartao> cartoes(UUID usuarioId) {
        referencias.usuario(usuarioId);
        Map<UUID, List<ConsultasFinanceirasRepository.ValoresFatura>> valores = consultas
                .faturas(usuarioId, null, calculos.hoje()).stream()
                .collect(Collectors.groupingBy(ConsultasFinanceirasRepository.ValoresFatura::cartaoId));
        return cartoes.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
                .map(cartao -> resumoCartao(cartao, valores.getOrDefault(cartao.getId(), List.of()))).toList();
    }

    private DashboardDto.Cartao resumoCartao(CartaoCredito cartao,
            List<ConsultasFinanceirasRepository.ValoresFatura> valores) {
        YearMonth referencia = calculos.ciclo(calculos.hoje(), cartao.getDiaFechamento(), cartao.getDiaVencimento()).referencia();
        Map<YearMonth, ConsultasFinanceirasRepository.ValoresFatura> porMes = valores.stream()
                .collect(Collectors.toMap(v -> YearMonth.of(v.ano(), v.mes()), Function.identity()));
        var atual = porMes.get(referencia);
        var seguinte = porMes.get(referencia.plusMonths(1));
        BigDecimal utilizado = valores.stream().map(ConsultasFinanceirasRepository.ValoresFatura::pendente)
                .reduce(CalculosFinanceirosService.ZERO, BigDecimal::add);
        BigDecimal proximas = valores.stream().filter(v -> YearMonth.of(v.ano(), v.mes()).isAfter(referencia))
                .map(ConsultasFinanceirasRepository.ValoresFatura::pendente)
                .reduce(CalculosFinanceirosService.ZERO, BigDecimal::add);
        return new DashboardDto.Cartao(cartao.getId(), cartao.getNome(),
                atual == null ? null : faturas.resumo(atual), seguinte == null ? null : faturas.resumo(seguinte),
                proximas, utilizado, cartao.getLimite().subtract(utilizado));
    }
}
