package br.gov.observatorioaps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Two DataSources exist in this application — SQLite (own persistence) and PostgreSQL (PEC,
 * read-only). Neither is autoconfigured: this project depends only on {@code spring-boot-jdbc}
 * (JdbcTemplate/transaction infrastructure) and HikariCP, not on any
 * {@code spring-boot-*-autoconfigure} module for JDBC/Flyway/DataSource, so there is no implicit
 * DataSource for Spring to guess about. Both DataSources, and Flyway, are wired explicitly in
 * {@code config} — which is what makes ENG-29 ("Flyway must never touch the PEC DataSource")
 * provable rather than incidental.
 */
@SpringBootApplication
public class ObservatorioApsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ObservatorioApsApplication.class, args);
    }
}
