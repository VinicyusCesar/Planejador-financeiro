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

public final class TransferenciaDto {
    private TransferenciaDto() {}
    public record Entrada(@NotNull UUID contaOrigemId, @NotNull UUID contaDestinoId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor,
            @NotNull LocalDate dataTransferencia, SituacaoTransacao situacao) {}
    public record Saida(UUID id, UUID usuarioId, UUID contaOrigemId, UUID contaDestinoId,
            BigDecimal valor, LocalDate dataTransferencia, SituacaoTransacao situacao) {}
}
