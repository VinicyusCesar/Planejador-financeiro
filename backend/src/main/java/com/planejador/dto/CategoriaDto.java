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

public final class CategoriaDto {
    private CategoriaDto() {}
    public record Entrada(@NotBlank @Size(max = 80) String nome, @NotNull TipoCategoria tipo) {}
    public record Saida(UUID id, UUID usuarioId, String nome, TipoCategoria tipo) {}
}
