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
@RequestMapping("/api/contas")
public class ContaController {
    private final ContaService service;
    public ContaController(ContaService service) { this.service = service; }

    @GetMapping
    public List<ContaDto.Saida> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId) {
        return service.listar(usuarioId);
    }

    @GetMapping("/{id}")
    public ContaDto.Saida consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @GetMapping("/{id}/saldo")
    public ContaDto.Saldo saldo(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.saldo(usuarioId, id);
    }

    @PostMapping
    public ResponseEntity<ContaDto.Saida> criar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @Valid @RequestBody ContaDto.Entrada entrada) {
        var saida = service.criar(usuarioId, entrada);
        return ResponseEntity.created(URI.create("/api/contas/" + saida.id())).body(saida);
    }

    @PutMapping("/{id}")
    public ContaDto.Saida atualizar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id,
            @Valid @RequestBody ContaDto.Atualizacao entrada) {
        return service.atualizar(usuarioId, id, entrada);
    }

    @PatchMapping("/{id}/desativacao")
    public ResponseEntity<Void> desativar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        service.desativar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
