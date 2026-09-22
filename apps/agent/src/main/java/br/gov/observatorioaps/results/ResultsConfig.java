package br.gov.observatorioaps.results;

import br.gov.observatorioaps.access.application.GrantRevalidator;
import br.gov.observatorioaps.execution.domain.job.JobRepository;
import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcEvidenceRepository;
import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcExtractionManifestRepository;
import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcResultRepository;
import br.gov.observatorioaps.results.adapter.out.sqlite.JdbcResultStagingArea;
import br.gov.observatorioaps.results.application.PublicationService;
import br.gov.observatorioaps.results.application.ReproducibilityCheck;
import br.gov.observatorioaps.results.domain.EvidenceRepository;
import br.gov.observatorioaps.results.domain.ExtractionManifestRepository;
import br.gov.observatorioaps.results.domain.ResultRepository;
import br.gov.observatorioaps.results.domain.ResultStagingArea;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import br.gov.observatorioaps.platform.sqlite.SqliteProperties;

/**
 * Wires the result-store beans. Every bean here that touches {@code results}/{@code
 * result_staging}/{@code evidence}/{@code extraction_manifests} depends, directly or
 * transitively, on {@code flywayMigration} — migrations run before any job is accepted (§1.12.2).
 * {@code publicationService} closes the referenced job row in the same SQLite transaction that
 * publishes the result (ENG-23), so this config depends on {@link JobRepository} — the one
 * documented {@code results} → {@code execution.domain} edge.
 */
@Configuration
public class ResultsConfig {

    @Bean
    @DependsOn("flywayMigration")
    public ExtractionManifestRepository extractionManifestRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcExtractionManifestRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ResultStagingArea resultStagingArea(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcResultStagingArea(sqliteJdbcTemplate);
    }

    @Bean
    public ReproducibilityCheck reproducibilityCheck(SqliteProperties properties) {
        return new ReproducibilityCheck(properties.extractsDirectory());
    }

    @Bean
    @DependsOn("flywayMigration")
    public PublicationService publicationService(
            JdbcTemplate sqliteJdbcTemplate,
            TransactionTemplate sqliteTransactionTemplate,
            JobRepository jobRepository,
            ExtractionManifestRepository extractionManifestRepository,
            ReproducibilityCheck reproducibilityCheck,
            SqliteProperties properties,
            GrantRevalidator grantRevalidator) {
        return new PublicationService(sqliteJdbcTemplate, sqliteTransactionTemplate,
                jobRepository,
                extractionManifestRepository, reproducibilityCheck, properties.extractsDirectory(),
                grantRevalidator);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ResultRepository resultRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcResultRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public EvidenceRepository evidenceRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcEvidenceRepository(sqliteJdbcTemplate);
    }
}
