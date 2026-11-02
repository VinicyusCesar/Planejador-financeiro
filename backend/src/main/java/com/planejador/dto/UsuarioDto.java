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

public final class UsuarioDto {
    private UsuarioDto() {}
    public record Entrada(@NotBlank @Size(max = 100) String nome, @NotBlank @Email @Size(max = 150) String email) {}
    public record Saida(UUID id, String nome, String email, LocalDateTime criadoEm) {}
}
