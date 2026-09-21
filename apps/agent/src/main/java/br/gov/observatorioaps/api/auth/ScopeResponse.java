package br.gov.observatorioaps.api.auth;

/**
 * §1.12 L427 município/CNES/INE scope of a result. {@code cnes}/{@code ine} are always null for
 * now — the {@code results} table (V1/V2) only carries {@code municipality_ibge}; team-grained
 * aggregates would need a schema migration plus a rule-pack change, out of this recorte (see the
 * plan's CNES/INE section).
 */
public record ScopeResponse(String municipalityIbge, String cnes, String ine) {
}
