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

public final class OrcamentoDto {
    private OrcamentoDto() {}
    public record ItemEntrada(@NotNull UUID categoriaId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal valorPrevisto) {}
    public record Entrada(@Min(2000) @Max(9998) int ano, @Min(1) @Max(12) int mes,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal metaEconomia,
            @NotNull List<@NotNull @Valid ItemEntrada> itens) {}
    public record ItemSaida(UUID id, UUID categoriaId, String categoria, BigDecimal previsto,
            BigDecimal realizado, BigDecimal diferenca) {}
    public record Saida(UUID id, UUID usuarioId, int ano, int mes, BigDecimal metaEconomia,
            BigDecimal totalPrevisto, BigDecimal totalRealizado, BigDecimal diferenca,
            BigDecimal realizadoSemPrevisao, List<ItemSaida> itens) {}
}
