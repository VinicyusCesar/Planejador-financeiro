package com.planejador;

import com.planejador.repository.DatabaseHealthRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
)
class BackendApplicationTests {

    @MockitoBean
    private DatabaseHealthRepository databaseHealthRepository;

    @LocalServerPort
    private int port;

    @Test
    void contextLoadsAndHealthRespondsOverHttp() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/health")).GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type")).hasValue("application/json");
            assertThat(response.body()).contains("\"success\":true", "\"status\":\"UP\"");
        }
    }

}
