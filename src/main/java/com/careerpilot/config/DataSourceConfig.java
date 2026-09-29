package com.careerpilot.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** MyBatis uses the primary MySQL pool; the vector store has its own PostgreSQL pool. */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    public DataSource mysqlDataSource(DataSourceProperties mysqlDataSourceProperties) {
        return mysqlDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    @Bean(name = "vectorDataSource")
    public DataSource vectorDataSource(
            @Value("${app.pgvector.host}") String host,
            @Value("${app.pgvector.port}") int port,
            @Value("${app.pgvector.database}") String database,
            @Value("${app.pgvector.username}") String username,
            @Value("${app.pgvector.password}") String password) {
        HikariDataSource config = new HikariDataSource();
        config.setPoolName("pgvector-pool");
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?connectTimeout=5&socketTimeout=30");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(10_000);
        return config;
    }

    @Bean(name = "vectorJdbcTemplate")
    public JdbcTemplate vectorJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("vectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "mysqlJdbcTemplate")
    @Primary
    public JdbcTemplate mysqlJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
