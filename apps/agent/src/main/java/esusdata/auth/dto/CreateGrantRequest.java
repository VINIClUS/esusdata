package esusdata.auth.dto;

/** {@code cnes}/{@code ine} are optional — null grants municipality-wide (§1.12 L427). */
public record CreateGrantRequest(String role, String scopeKind, String municipalityIbge, String cnes, String ine) {
}
