package com.planejador.dto;

public record DatabaseHealthResponse(boolean success, String database, String schema, String user) {
}
