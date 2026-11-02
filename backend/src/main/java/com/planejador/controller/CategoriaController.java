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
@RequestMapping("/api/categorias")
public class CategoriaController {
    private final CategoriaService service;
    public CategoriaController(CategoriaService service) { this.service = service; }

    @GetMapping
    public List<CategoriaDto.Saida> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @RequestParam(required = false) TipoCategoria tipo) {
        return service.listar(usuarioId, tipo);
    }

    @GetMapping("/{id}")
    public CategoriaDto.Saida consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @PostMapping
    public ResponseEntity<CategoriaDto.Saida> criar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @Valid @RequestBody CategoriaDto.Entrada entrada) {
        var saida = service.criar(usuarioId, entrada);
        return ResponseEntity.created(URI.create("/api/categorias/" + saida.id())).body(saida);
    }

    @PutMapping("/{id}")
    public CategoriaDto.Saida atualizar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id,
            @Valid @RequestBody CategoriaDto.Entrada entrada) {
        return service.atualizar(usuarioId, id, entrada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        service.excluir(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
