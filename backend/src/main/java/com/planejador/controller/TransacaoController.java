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
@RequestMapping("/api/transacoes")
public class TransacaoController {
    private final TransacaoService service;
    public TransacaoController(TransacaoService service) { this.service = service; }

    @GetMapping
    public PaginaDto<TransacaoDto.Saida> listar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim,
            @RequestParam(required = false) TipoTransacao tipo, @RequestParam(required = false) UUID contaId,
            @RequestParam(required = false) UUID cartaoId, @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) SituacaoTransacao situacao,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(usuarioId, inicio, fim, tipo, contaId, cartaoId, categoriaId, situacao, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public TransacaoDto.Saida consultar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(usuarioId, id);
    }

    @GetMapping("/{id}/parcelas")
    public List<TransacaoDto.ParcelaSaida> parcelas(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        return service.parcelas(usuarioId, id);
    }

    @PostMapping
    public ResponseEntity<TransacaoDto.Saida> criar(@RequestHeader("X-Usuario-Id") UUID usuarioId,
            @Valid @RequestBody TransacaoDto.Entrada entrada) {
        var saida = service.criar(usuarioId, entrada);
        return ResponseEntity.created(URI.create("/api/transacoes/" + saida.id())).body(saida);
    }

    @PutMapping("/{id}")
    public TransacaoDto.Saida atualizar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id,
            @Valid @RequestBody TransacaoDto.Entrada entrada) {
        return service.atualizar(usuarioId, id, entrada);
    }

    @PostMapping("/{id}/cancelamento")
    public ResponseEntity<Void> cancelar(@RequestHeader("X-Usuario-Id") UUID usuarioId, @PathVariable UUID id) {
        service.cancelar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
