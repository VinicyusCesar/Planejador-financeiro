package com.planejador.service;

import com.planejador.dto.DatabaseHealthResponse;
import com.planejador.repository.DatabaseHealthRepository;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final DatabaseHealthRepository databaseHealthRepository;

    public HealthService(DatabaseHealthRepository databaseHealthRepository) {
        this.databaseHealthRepository = databaseHealthRepository;
    }

    public DatabaseHealthResponse checkDatabase() {
        return databaseHealthRepository.checkConnection();
    }
}
