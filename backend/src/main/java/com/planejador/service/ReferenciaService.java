package com.planejador.service;

import com.planejador.exception.ApiException;
import com.planejador.model.*;
import com.planejador.repository.*;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReferenciaService {
    private final UsuarioRepository usuarios;
    private final ContaRepository contas;
    private final CategoriaRepository categorias;
    private final CartaoCreditoRepository cartoes;
    private final TransacaoRepository transacoes;
    private final FaturaRepository faturas;

    public ReferenciaService(UsuarioRepository usuarios, ContaRepository contas, CategoriaRepository categorias,
            CartaoCreditoRepository cartoes, TransacaoRepository transacoes, FaturaRepository faturas) {
        this.usuarios = usuarios;
        this.contas = contas;
        this.categorias = categorias;
        this.cartoes = cartoes;
        this.transacoes = transacoes;
        this.faturas = faturas;
    }

    public Usuario usuario(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
    }

    // As escritas financeiras do mesmo proprietário são serializadas nesta linha.
    // O bloqueio só é utilizado dentro de métodos de serviço transacionais.
    public Usuario bloquearUsuario(UUID id) {
        return usuarios.bloquear(id).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
    }

    public Conta conta(UUID usuarioId, UUID id, boolean exigirAtiva) {
        Conta conta = contas.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Conta"));
        if (exigirAtiva && !conta.getAtiva()) {
            throw ApiException.conflito("CONTA_INATIVA", "A conta está desativada.");
        }
        return conta;
    }

    public Categoria categoria(UUID usuarioId, UUID id) {
        return categorias.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Categoria"));
    }

    public CartaoCredito cartao(UUID usuarioId, UUID id, boolean exigirAtivo) {
        CartaoCredito cartao = cartoes.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Cartão"));
        if (exigirAtivo && !cartao.getAtivo()) {
            throw ApiException.conflito("CARTAO_INATIVO", "O cartão está desativado.");
        }
        return cartao;
    }

    public Transacao transacao(UUID usuarioId, UUID id) {
        return transacoes.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Transação"));
    }

    public Fatura fatura(UUID usuarioId, UUID id) {
        return faturas.findByIdAndCartaoUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Fatura"));
    }
}
