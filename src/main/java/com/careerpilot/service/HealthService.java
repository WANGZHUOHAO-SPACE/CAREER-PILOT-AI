package com.careerpilot.service;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private final DataSource dataSource;
    private final DataSource vectorDataSource;

    public HealthService(@Qualifier("dataSource") DataSource dataSource,
            @Qualifier("vectorDataSource") DataSource vectorDataSource) {
        this.dataSource = dataSource;
        this.vectorDataSource = vectorDataSource;
    }

    public boolean databaseAvailable() {
        return available(dataSource, "mysql");
    }

    public boolean vectorDatabaseAvailable() {
        return available(vectorDataSource, "pgvector");
    }

    private boolean available(DataSource source, String name) {
        try (var connection = source.getConnection()) {
            return connection.isValid(2);
        }
        catch (SQLException | RuntimeException exception) {
            log.warn("Health check could not connect to {}: {}", name, exception.getClass().getSimpleName());
            return false;
        }
    }
}
