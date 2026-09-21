package br.gov.observatorioaps.sourceconnector.infrastructure.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Plan §2.1: an empty {@code binary} means no Rust execution plane is installed, and
 * {@link SourceConnectorConfig} falls back to the in-process {@code JdbcAcquisitionAdapter} — a
 * fresh install or a machine without the packaged binary must never fail to acquire.
 */
@ConfigurationProperties(prefix = "observatorio.execution-plane")
public record ExecutionPlaneProperties(
        @DefaultValue("") String binary,
        @DefaultValue("30s") Duration exitGrace
) {
}
