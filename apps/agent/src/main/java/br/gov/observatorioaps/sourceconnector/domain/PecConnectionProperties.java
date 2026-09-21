package br.gov.observatorioaps.sourceconnector.domain;

/**
 * Structured connection descriptor for a PEC source — Tech Spec §1.12.6 (SSRF control):
 * "Aceitar host/porta/database estruturados, não uma URL JDBC arbitrária." There is deliberately
 * no {@code jdbcUrl} field anywhere in this type; {@code PecDataSourceFactory} is the only place
 * the JDBC URL string is assembled, and only from these fields.
 *
 * @param sourceId          persistent identity of the source (§1.4.1), independent of host changes
 * @param host              validated against {@link AllowedDestinations} before any connection attempt
 * @param port
 * @param database
 * @param user              the PEC role used for reads — never {@code postgres} (§1.12.7)
 * @param secretRef         opaque reference to where the password is stored; never the password itself
 * @param municipalityIbge  7-digit IBGE code this source is authorized for (§1.4.2)
 */
public record PecConnectionProperties(
        String sourceId,
        String host,
        int port,
        String database,
        String user,
        String secretRef,
        String municipalityIbge
) {
    public PecConnectionProperties {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("database is required");
        }
        if (!isValidPostgresIdentifier(database)) {
            throw new IllegalArgumentException(
                    "database name is not a valid PostgreSQL identifier: " + database
                            + " — must be alphanumeric or underscore, no JDBC URL parameter injection allowed");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user is required");
        }
        if ("postgres".equalsIgnoreCase(user)) {
            throw new IllegalArgumentException(
                    "Refusing to configure a source with the 'postgres' superuser (Tech Spec §1.12.7)");
        }
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException(
                    "municipality_ibge must be a 7-digit code, got: " + municipalityIbge);
        }
    }

    private static boolean isValidPostgresIdentifier(String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
