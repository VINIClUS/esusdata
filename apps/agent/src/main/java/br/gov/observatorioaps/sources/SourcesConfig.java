package br.gov.observatorioaps.sources;

import br.gov.observatorioaps.sources.adapter.out.sqlite.JdbcSourceRepository;
import br.gov.observatorioaps.sources.domain.SourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the source registry. {@code sourceRepository} depends on {@code flywayMigration} (V2) —
 * migrations run before any source configuration is read.
 */
@Configuration
public class SourcesConfig {

    @Bean
    @DependsOn("flywayMigration")
    public SourceRepository sourceRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcSourceRepository(sqliteJdbcTemplate);
    }
}
