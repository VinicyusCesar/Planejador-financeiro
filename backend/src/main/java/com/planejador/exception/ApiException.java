package com.planejador.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() { return status; }
    public String getCodigo() { return codigo; }

    public static ApiException naoEncontrado(String recurso) {
        return new ApiException(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", recurso + " não encontrado.");
    }

    public static ApiException invalido(String codigo, String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, codigo, mensagem);
    }

    public static ApiException conflito(String codigo, String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensagem);
    }
}
