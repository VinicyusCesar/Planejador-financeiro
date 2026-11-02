package com.planejador.controller;

import com.planejador.dto.*;
import com.planejador.enums.*;
import com.planejador.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/resumo-mensal")
    public DashboardDto.Mensal mensal(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @RequestParam @Min(2000) @Max(9998) int ano, @RequestParam @Min(1) @Max(12) int mes) {
        return service.mensal(usuarioId, ano, mes);
    }

    @GetMapping("/resumo-semanal")
    public DashboardDto.Semanal semanal(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @RequestParam(required = false) LocalDate dataReferencia) {
        return service.semanal(usuarioId, dataReferencia);
    }

    @GetMapping("/cartoes")
    public List<DashboardDto.Cartao> cartoes(@RequestHeader("X-Usuario-Id") UUID usuarioId) {
        return service.cartoes(usuarioId);
    }
}
