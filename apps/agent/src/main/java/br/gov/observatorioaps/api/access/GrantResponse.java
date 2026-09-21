package br.gov.observatorioaps.api.access;

public record GrantResponse(
        String grantId, String userId, String role, String scopeKind, String municipalityIbge,
        String cnes, String ine, String grantedAt, String grantedBy) {
}
