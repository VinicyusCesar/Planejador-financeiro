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

public final class MetaDto {
    private MetaDto() {}
    public record Entrada(@NotBlank @Size(max = 150) String nome,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valorObjetivo,
            @NotNull LocalDate dataInicio, LocalDate dataLimite, SituacaoMeta situacao) {}
    public record Saida(UUID id, UUID usuarioId, String nome, BigDecimal valorObjetivo,
            LocalDate dataInicio, LocalDate dataLimite, SituacaoMeta situacao,
            boolean progressoDisponivel, BigDecimal valorAcumulado, BigDecimal percentual, String observacao) {}
}
