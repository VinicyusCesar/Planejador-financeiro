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
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService service;
    public UsuarioController(UsuarioService service) { this.service = service; }

    @GetMapping
    public List<UsuarioDto.Saida> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public UsuarioDto.Saida consultar(@PathVariable UUID id) { return service.consultar(id); }

    @PostMapping
    public ResponseEntity<UsuarioDto.Saida> criar(@Valid @RequestBody UsuarioDto.Entrada entrada) {
        var saida = service.criar(entrada);
        return ResponseEntity.created(URI.create("/api/usuarios/" + saida.id())).body(saida);
    }

    @PutMapping("/{id}")
    public UsuarioDto.Saida atualizar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @PathVariable UUID id, @Valid @RequestBody UsuarioDto.Entrada entrada) {
        return service.atualizar(usuarioId, id, entrada);
    }
}
