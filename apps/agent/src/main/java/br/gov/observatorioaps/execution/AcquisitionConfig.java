package br.gov.observatorioaps.execution;

import br.gov.observatorioaps.execution.domain.acquisition.AcquisitionPort;
import br.gov.observatorioaps.execution.domain.acquisition.AllowedDestinations;
import br.gov.observatorioaps.execution.domain.acquisition.PecSecretResolver;
import br.gov.observatorioaps.execution.adapter.out.pec.EnvFileSecretResolver;
import br.gov.observatorioaps.execution.adapter.out.pec.JdbcAcquisitionAdapter;
import br.gov.observatorioaps.execution.adapter.out.pec.PecDataSourceFactory;
import br.gov.observatorioaps.execution.adapter.out.process.SubprocessAcquisitionAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import br.gov.observatorioaps.platform.sqlite.SqliteProperties;
/**
 * Wires the PEC acquisition beans — deployment-administered destination allowlist, secret
 * resolution, and the PEC connection pool factory (ADR 0012: {@code execution.application} talks
 * to the PEC only through {@link AcquisitionPort}, never by importing this package's adapter
 * types directly).
 */
@Configuration
@EnableConfigurationProperties({SourceConnectionProperties.class, ExecutionPlaneProperties.class})
public class AcquisitionConfig {

    /**
     * §1.12.6/ENG-46: deployment-administered, never widened by a runtime call. Empty by default
     * — a fresh install authorizes no destination until an operator configures
     * {@code observatorio.source.allowed-destinations} (a list of {@code "host:port"} entries).
     */
    @Bean
    public AllowedDestinations allowedDestinations(SourceConnectionProperties properties) {
        Set<AllowedDestinations.HostPort> parsed = new HashSet<>();
        for (String entry : orEmpty(properties.allowedDestinations())) {
            int colon = entry.lastIndexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                throw new IllegalArgumentException(
                        "observatorio.source.allowed-destinations entry must be host:port, got: " + entry);
            }
            String host = entry.substring(0, colon);
            int port = Integer.parseInt(entry.substring(colon + 1));
            parsed.add(new AllowedDestinations.HostPort(host, port));
        }
        return new AllowedDestinations(parsed);
    }

    /**
     * Dev-only credential resolver (ADR-0002/0003) — the same env-file convention already used by
     * the live PEC tests. Production secret storage remains a documented pending item (§1.12.7).
     */
    @Bean
    public PecSecretResolver pecSecretResolver(SourceConnectionProperties properties) {
        String configured = properties.secretFile();
        Path secretFile = (configured == null || configured.isBlank())
                ? Path.of(System.getProperty("user.home"), ".config", "observatorio-aps", "pec.env")
                : Path.of(configured);
        return new EnvFileSecretResolver(secretFile);
    }

    @Bean
    public PecDataSourceFactory pecDataSourceFactory(
            AllowedDestinations allowedDestinations, PecSecretResolver pecSecretResolver) {
        return new PecDataSourceFactory(allowedDestinations, pecSecretResolver);
    }

    /**
     * Plan §2.1: an empty {@code observatorio.execution-plane.binary} keeps the in-process JDBC
     * adapter as the default — {@code mvn verify} and any deployment without the packaged Rust
     * binary must never depend on one existing.
     */
    @Bean
    public AcquisitionPort acquisitionPort(
            PecDataSourceFactory pecDataSourceFactory, SqliteProperties properties, Clock clock,
            ExecutionPlaneProperties executionPlaneProperties, PecSecretResolver pecSecretResolver,
            AllowedDestinations allowedDestinations) {
        String binary = executionPlaneProperties.binary();
        if (binary == null || binary.isBlank()) {
            return new JdbcAcquisitionAdapter(pecDataSourceFactory, properties.extractsDirectory(), clock);
        }
        return new SubprocessAcquisitionAdapter(
                List.of(binary), pecSecretResolver, allowedDestinations, properties.extractsDirectory(), clock,
                executionPlaneProperties.exitGrace());
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
