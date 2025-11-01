package com.talya.searchanalytics.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class DataSourceFallbackConfig {
    private static final Logger log = LoggerFactory.getLogger(DataSourceFallbackConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        String url = env.getProperty("spring.datasource.url", "jdbc:h2:file:./data/statsdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        String username = env.getProperty("spring.datasource.username", "sa");
        String password = env.getProperty("spring.datasource.password", "");
        String driver = env.getProperty("spring.datasource.driver-class-name", "org.h2.Driver");

        // Try primary (configured) datasource first
        try {
            log.info("Attempting to use configured datasource: {}", url);
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName(driver);
            // quick test connection
            try (Connection c = ds.getConnection()) {
                log.info("Successfully obtained connection from configured datasource");
            }
            return ds;
        } catch (Exception e) {
            log.warn("Failed to connect to configured datasource ({}). Falling back to in-memory H2. Cause: {}", url, e.toString());
            // Fallback to in-memory H2
            DriverManagerDataSource fallback = new DriverManagerDataSource();
            fallback.setDriverClassName("org.h2.Driver");
            fallback.setUrl("jdbc:h2:mem:fallbackdb;DB_CLOSE_DELAY=-1");
            fallback.setUsername("sa");
            fallback.setPassword("");
            return fallback;
        }
    }
}

