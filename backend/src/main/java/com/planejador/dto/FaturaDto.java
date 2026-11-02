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

public final class FaturaDto {
    private FaturaDto() {}
    public record Resumo(UUID id, UUID cartaoId, int mesReferencia, int anoReferencia,
            LocalDate dataFechamento, LocalDate dataVencimento, SituacaoFatura situacao,
            BigDecimal total, BigDecimal totalPago, BigDecimal saldoPendente) {}
    public record Compra(UUID parcelaId, UUID transacaoId, String descricao, LocalDate dataCompra,
            int numero, int quantidadeParcelas, BigDecimal valor) {}
    public record PagamentoEntrada(@NotNull UUID contaId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor,
            @NotNull LocalDate dataPagamento) {}
    public record PagamentoSaida(UUID id, UUID faturaId, UUID contaId, BigDecimal valor, LocalDate dataPagamento) {}
    public record Detalhe(Resumo resumo, List<Compra> compras, List<PagamentoSaida> pagamentos) {}
}
