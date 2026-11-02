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

public record ApiErro(String codigo, String mensagem, Map<String, String> campos) {
    public ApiErro(String codigo, String mensagem) {
        this(codigo, mensagem, Map.of());
    }
}
