package br.gov.observatorioaps.sourceconnector.infrastructure.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * §1.12.6 / ENG-46: the destination allowlist is "administrada por procedimento de implantação
 * confiável, não ampliada livremente pela mesma chamada que testa uma conexão" — so it is
 * deployment-time configuration (this properties class), never a runtime API. Entries are
 * {@code "host:port"} strings. {@code secretFile} defaults to the dev-only path already used by
 * ADR-0002/0003; production secret storage remains a documented pending item (§1.12.7).
 */
@ConfigurationProperties(prefix = "observatorio.source")
public record SourceConnectionProperties(
        List<String> allowedDestinations,
        @DefaultValue("") String secretFile
) {
}
