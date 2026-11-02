package com.planejador.exception;

import com.planejador.dto.ApiErro;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErro> negocio(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErro(exception.getCodigo(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErro> validacao(MethodArgumentNotValidException exception) {
        Map<String, String> campos = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiErro("DADOS_INVALIDOS", "Verifique os campos informados.", campos));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiErro> entrada(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErro("REQUISICAO_INVALIDA",
                "Verifique o JSON, os parâmetros, os tipos e o cabeçalho X-Usuario-Id."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErro> integridade(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErro("CONFLITO_DADOS", "O registro já existe ou possui relacionamentos que impedem a operação."));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiErro> concorrencia(PessimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErro("OPERACAO_CONCORRENTE", "Não foi possível concluir a operação concorrente. Tente novamente."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErro> banco(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErro("BANCO_INDISPONIVEL", "Não foi possível acessar o banco de dados."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErro> rota(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErro("ROTA_NAO_ENCONTRADA", "Rota não encontrada."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErro> metodo(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiErro("METODO_NAO_PERMITIDO", "Método HTTP não permitido para esta rota."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErro> formato(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ApiErro("FORMATO_NAO_SUPORTADO", "Utilize Content-Type application/json."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErro> inesperado(Exception exception) {
        log.error("Erro inesperado ao processar a requisição.", exception);
        return ResponseEntity.internalServerError().body(new ApiErro("ERRO_INTERNO", "Não foi possível concluir a operação."));
    }
}
