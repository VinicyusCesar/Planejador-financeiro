package com.planejador.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cartao_credito", schema = "public")
public class CartaoCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "conta_pagamento_id", nullable = true)
    private Conta contaPagamento;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "final_cartao", nullable = true, length = 4, columnDefinition = "char(4)")
    private String finalCartao;

    @Column(name = "limite", nullable = false, precision = 15, scale = 2)
    private BigDecimal limite;

    @Column(name = "dia_fechamento", nullable = false)
    private int diaFechamento;

    @Column(name = "dia_vencimento", nullable = false)
    private int diaVencimento;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

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

    public Conta getContaPagamento() {
        return contaPagamento;
    }

    public void setContaPagamento(Conta contaPagamento) {
        this.contaPagamento = contaPagamento;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getFinalCartao() {
        return finalCartao;
    }

    public void setFinalCartao(String finalCartao) {
        this.finalCartao = finalCartao;
    }

    public BigDecimal getLimite() {
        return limite;
    }

    public void setLimite(BigDecimal limite) {
        this.limite = limite;
    }

    public int getDiaFechamento() {
        return diaFechamento;
    }

    public void setDiaFechamento(int diaFechamento) {
        this.diaFechamento = diaFechamento;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public void setDiaVencimento(int diaVencimento) {
        this.diaVencimento = diaVencimento;
    }

    public boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
