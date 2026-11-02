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

public final class CartaoDto {
    private CartaoDto() {}
    public record Entrada(@NotBlank @Size(max = 100) String nome,
            @Pattern(regexp = "[0-9]{4}") String finalCartao, UUID contaPagamentoId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal limite,
            @Min(1) @Max(31) int diaFechamento, @Min(1) @Max(31) int diaVencimento) {}
    public record Saida(UUID id, UUID usuarioId, UUID contaPagamentoId, String nome, String finalCartao,
            BigDecimal limite, int diaFechamento, int diaVencimento, boolean ativo,
            BigDecimal limiteUtilizado, BigDecimal limiteDisponivel) {}
}
