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
@RequestMapping("/api/orcamentos")
public class OrcamentoController {
    private final OrcamentoService service;
    public OrcamentoController(OrcamentoService service) { this.service = service; }

    @GetMapping
    public List<OrcamentoDto.Saida> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId) {
        return service.listar(usuarioId);
    }

    @GetMapping("/{id}")
    public OrcamentoDto.Saida consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @PostMapping
    public ResponseEntity<OrcamentoDto.Saida> criar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @Valid @RequestBody OrcamentoDto.Entrada entrada) {
        var saida = service.criar(usuarioId, entrada);
        return ResponseEntity.created(URI.create("/api/orcamentos/" + saida.id())).body(saida);
    }

    @PutMapping("/{id}")
    public OrcamentoDto.Saida atualizar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id,
            @Valid @RequestBody OrcamentoDto.Entrada entrada) {
        return service.atualizar(usuarioId, id, entrada);
    }
}
