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

public final class ContaDto {
    private ContaDto() {}
    public record Entrada(@NotBlank @Size(max = 100) String nome, @NotNull TipoConta tipo,
            @NotNull @Digits(integer = 13, fraction = 2) BigDecimal saldoInicial) {}
    public record Atualizacao(@NotBlank @Size(max = 100) String nome, @NotNull TipoConta tipo) {}
    public record Saida(UUID id, UUID usuarioId, String nome, TipoConta tipo, BigDecimal saldoInicial, boolean ativa) {}
    public record Saldo(UUID contaId, BigDecimal saldoAtual) {}
}
