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
@RequestMapping("/api/transferencias")
public class TransferenciaController {
    private final TransferenciaService service;
    public TransferenciaController(TransferenciaService service) { this.service = service; }

    @GetMapping
    public PaginaDto<TransferenciaDto.Saida> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(usuarioId, inicio, fim, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public TransferenciaDto.Saida consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @PostMapping
    public ResponseEntity<TransferenciaDto.Saida> criar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @Valid @RequestBody TransferenciaDto.Entrada entrada) {
        var saida = service.criar(usuarioId, entrada);
        return ResponseEntity.created(URI.create("/api/transferencias/" + saida.id())).body(saida);
    }

    @PostMapping("/{id}/cancelamento")
    public ResponseEntity<Void> cancelar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        service.cancelar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirmacao")
    public TransferenciaDto.Saida confirmar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.confirmar(usuarioId, id);
    }
}
