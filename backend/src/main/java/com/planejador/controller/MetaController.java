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
@RequestMapping("/api/metas")
public class MetaController {
    private final MetaService service;
    public MetaController(MetaService service) { this.service = service; }

    @GetMapping
    public List<MetaDto.Saida> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId) {
        return service.listar(usuarioId);
    }

    @GetMapping("/{id}")
    public MetaDto.Saida consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @PostMapping
    public ResponseEntity<MetaDto.Saida> criar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @Valid @RequestBody MetaDto.Entrada entrada) {
        var saida = service.criar(usuarioId, entrada);
        return ResponseEntity.created(URI.create("/api/metas/" + saida.id())).body(saida);
    }

    @PutMapping("/{id}")
    public MetaDto.Saida atualizar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id,
            @Valid @RequestBody MetaDto.Entrada entrada) {
        return service.atualizar(usuarioId, id, entrada);
    }
}
