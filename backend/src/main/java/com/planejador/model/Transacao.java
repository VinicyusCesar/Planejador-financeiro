package com.planejador.model;

import com.planejador.enums.CondicaoPagamento;
import com.planejador.enums.MeioPagamento;
import com.planejador.enums.SituacaoTransacao;
import com.planejador.enums.TipoTransacao;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transacao", schema = "public")
public class Transacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "conta_id", nullable = true)
    private Conta conta;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cartao_id", nullable = true)
    private CartaoCredito cartao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "descricao", nullable = false, length = 200)
    private String descricao;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoTransacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "meio_pagamento", nullable = false, length = 20)
    private MeioPagamento meioPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "condicao", nullable = false, length = 10)
    private CondicaoPagamento condicao;

    @Column(name = "quantidade_parcelas", nullable = false)
    private int quantidadeParcelas;

    @Column(name = "data_transacao", nullable = false)
    private LocalDate dataTransacao;

    @Column(name = "data_vencimento", nullable = true)
    private LocalDate dataVencimento;

    @Column(name = "data_liquidacao", nullable = true)
    private LocalDate dataLiquidacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 15)
    private SituacaoTransacao situacao;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Conta getConta() {
        return conta;
    }

    public void setConta(Conta conta) {
        this.conta = conta;
    }

    public CartaoCredito getCartao() {
        return cartao;
    }

    public void setCartao(CartaoCredito cartao) {
        this.cartao = cartao;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public void setTipo(TipoTransacao tipo) {
        this.tipo = tipo;
    }

    public MeioPagamento getMeioPagamento() {
        return meioPagamento;
    }

    public void setMeioPagamento(MeioPagamento meioPagamento) {
        this.meioPagamento = meioPagamento;
    }

    public CondicaoPagamento getCondicao() {
        return condicao;
    }

    public void setCondicao(CondicaoPagamento condicao) {
        this.condicao = condicao;
    }

    public int getQuantidadeParcelas() {
        return quantidadeParcelas;
    }

    public void setQuantidadeParcelas(int quantidadeParcelas) {
        this.quantidadeParcelas = quantidadeParcelas;
    }

    public LocalDate getDataTransacao() {
        return dataTransacao;
    }

    public void setDataTransacao(LocalDate dataTransacao) {
        this.dataTransacao = dataTransacao;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(LocalDate dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public LocalDate getDataLiquidacao() {
        return dataLiquidacao;
    }

    public void setDataLiquidacao(LocalDate dataLiquidacao) {
        this.dataLiquidacao = dataLiquidacao;
    }

    public SituacaoTransacao getSituacao() {
        return situacao;
    }

    public void setSituacao(SituacaoTransacao situacao) {
        this.situacao = situacao;
    }
}
