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

public final class TransacaoDto {
    private TransacaoDto() {}
    public record Entrada(@NotNull UUID categoriaId, UUID contaId, UUID cartaoId,
            @NotBlank @Size(max = 200) String descricao,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor,
            @NotNull TipoTransacao tipo, @NotNull MeioPagamento meioPagamento,
            CondicaoPagamento condicao, @Min(1) @Max(600) Integer quantidadeParcelas,
            @NotNull LocalDate dataTransacao, LocalDate dataVencimento, LocalDate dataLiquidacao,
            SituacaoTransacao situacao) {}
    public record Saida(UUID id, UUID usuarioId, UUID categoriaId, UUID contaId, UUID cartaoId,
            String descricao, BigDecimal valor, TipoTransacao tipo, MeioPagamento meioPagamento,
            CondicaoPagamento condicao, int quantidadeParcelas, LocalDate dataTransacao,
            LocalDate dataVencimento, LocalDate dataLiquidacao, SituacaoTransacao situacao) {}
    public record ParcelaSaida(UUID id, UUID transacaoId, UUID faturaId, int numero,
            BigDecimal valor, LocalDate dataVencimento) {}
}
