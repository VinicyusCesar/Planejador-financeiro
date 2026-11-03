package com.planejador.exception;

import com.planejador.controller.HealthController;
import com.planejador.dto.ErrorResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = HealthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HealthExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HealthExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseFailure(DataAccessException exception) {
        log.error("Falha na conectividade com o banco", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(false, "Não foi possível conectar ao banco de dados."));
    }
}
