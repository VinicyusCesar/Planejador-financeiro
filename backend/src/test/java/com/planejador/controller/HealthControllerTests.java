package com.planejador.controller;

import com.planejador.config.CorsConfig;
import com.planejador.dto.DatabaseHealthResponse;
import com.planejador.exception.HealthExceptionHandler;
import com.planejador.repository.DatabaseHealthRepository;
import com.planejador.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({CorsConfig.class, HealthService.class, HealthExceptionHandler.class})
class HealthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthRepository databaseHealthRepository;

    @Test
    void healthReturnsSuccessWithoutQueryingDatabase() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success": true, "status": "UP"}
                        """));

        verifyNoInteractions(databaseHealthRepository);
    }

    @Test
    void databaseReturnsConnectionInformation() throws Exception {
        when(databaseHealthRepository.checkConnection()).thenReturn(
                new DatabaseHealthResponse(true, "planejador_financeiro", "public", "postgres"));

        mockMvc.perform(get("/api/health/database"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success": true, "database": "planejador_financeiro", "schema": "public", "user": "postgres"}
                        """));
    }

    @Test
    void databaseFailureReturns503WithoutExposingExceptionDetails() throws Exception {
        when(databaseHealthRepository.checkConnection()).thenThrow(
                new DataAccessResourceFailureException("Detalhes internos da conexão"));

        mockMvc.perform(get("/api/health/database"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"success": false, "message": "Não foi possível conectar ao banco de dados."}
                        """))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Detalhes internos da conexão"))));
    }

    @Test
    void allowsAngularDevelopmentOrigin() throws Exception {
        mockMvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void allowsAngularPreflightForGet() throws Exception {
        mockMvc.perform(options("/api/health/database")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("GET")));

        verifyNoInteractions(databaseHealthRepository);
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mockMvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, "http://outro-host:4200"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
