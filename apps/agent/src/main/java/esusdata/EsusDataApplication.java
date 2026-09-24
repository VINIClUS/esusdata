package esusdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Two DataSources exist in this application — SQLite (own persistence) and PostgreSQL (PEC,
 * read-only). Spring Boot's general auto-configuration module is present for application
 * bootstrap, but this dependency set has no Flyway auto-configuration module or JDBC DataSource
 * auto-configuration. SQLite and Flyway are wired explicitly in {@code config}, while PEC pools
 * are created by {@code sourceconnector} — which makes ENG-29 ("Flyway must never touch the PEC
 * DataSource") provable rather than incidental.
 */
@SpringBootApplication
public final class EsusDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(EsusDataApplication.class, args);
    }

    private EsusDataApplication() {}
}
