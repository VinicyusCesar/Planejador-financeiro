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
@RequestMapping("/api/faturas")
public class FaturaController {
    private final FaturaService service;
    public FaturaController(FaturaService service) { this.service = service; }

    @GetMapping
    public List<FaturaDto.Resumo> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @RequestParam(required = false) UUID cartaoId, @RequestParam(required = false) PeriodoFatura periodo,
            @RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim) {
        return service.listar(usuarioId, cartaoId, inicio, fim, periodo);
    }

    @GetMapping("/{id}")
    public FaturaDto.Detalhe consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @PostMapping("/{id}/pagamentos")
    public ResponseEntity<FaturaDto.PagamentoSaida> pagar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @PathVariable UUID id, @Valid @RequestBody FaturaDto.PagamentoEntrada entrada) {
        return ResponseEntity.status(201).body(service.pagar(usuarioId, id, entrada));
    }
}
