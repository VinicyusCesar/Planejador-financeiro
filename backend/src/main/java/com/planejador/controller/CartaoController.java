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
@RequestMapping("/api/cartoes")
public class CartaoController {
    private final CartaoService service;
    private final FaturaService faturas;
    public CartaoController(CartaoService service, FaturaService faturas) {
        this.service = service;
        this.faturas = faturas;
    }

    @GetMapping
    public List<CartaoDto.Saida> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId) {
        return service.listar(usuarioId);
    }

    @GetMapping("/{id}")
    public CartaoDto.Saida consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @GetMapping("/{id}/faturas")
    public List<FaturaDto.Resumo> faturas(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id,
            @RequestParam(required = false) PeriodoFatura periodo, @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fim) {
        return faturas.listar(usuarioId, id, inicio, fim, periodo);
    }

    @PostMapping
    public ResponseEntity<CartaoDto.Saida> criar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @Valid @RequestBody CartaoDto.Entrada entrada) {
        var saida = service.criar(usuarioId, entrada);
        return ResponseEntity.created(URI.create("/api/cartoes/" + saida.id())).body(saida);
    }

    @PutMapping("/{id}")
    public CartaoDto.Saida atualizar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id,
            @Valid @RequestBody CartaoDto.Entrada entrada) {
        return service.atualizar(usuarioId, id, entrada);
    }

    @PatchMapping("/{id}/desativacao")
    public ResponseEntity<Void> desativar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        service.desativar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
