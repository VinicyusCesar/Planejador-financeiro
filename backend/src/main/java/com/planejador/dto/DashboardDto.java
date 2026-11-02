package com.planejador.dto;

import com.planejador.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardDto {
    private DashboardDto() {}
    public record CategoriaGasto(UUID categoriaId, String categoria, BigDecimal total) {}
    public record DiaGasto(LocalDate data, BigDecimal total) {}
    public record Mensal(int ano, int mes, BigDecimal receitas, BigDecimal despesas, BigDecimal saldoDoMes,
            List<CategoriaGasto> gastosPorCategoria, BigDecimal orcamentoPrevisto,
            BigDecimal orcamentoRealizado, BigDecimal diferencaOrcamento) {}
    public record Semanal(LocalDate inicio, LocalDate fim, BigDecimal totalSemana, BigDecimal totalSemanaAnterior,
            BigDecimal diferenca, BigDecimal variacaoPercentual, boolean comparacaoPercentualDisponivel,
            List<DiaGasto> gastosPorDia) {}
    public record Cartao(UUID cartaoId, String nome, FaturaDto.Resumo faturaAtual, FaturaDto.Resumo faturaSeguinte,
            BigDecimal totalProximasFaturas, BigDecimal limiteUtilizado, BigDecimal limiteDisponivel) {}
}
