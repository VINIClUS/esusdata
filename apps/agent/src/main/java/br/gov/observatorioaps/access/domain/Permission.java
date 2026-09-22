package br.gov.observatorioaps.access.domain;

/**
 * The permission vocabulary this build recognizes. {@code MANAGE_SOURCE}, {@code MANAGE_ACCESS}
 * and {@code READ_CLINICAL} are named verbatim by the Tech Spec (§1.12.6 L513). {@code
 * RUN_INDICATOR} and {@code AUDIT} are a PROJECT DECISION filling a gap the spec leaves open (it
 * names permissions but never a complete list) — see {@code docs/adr/0007-modelo-de-permissoes.md}.
 */
public enum Permission {
    MANAGE_SOURCE("manage_source"),
    MANAGE_ACCESS("manage_access"),
    READ_CLINICAL("read_clinical"),
    RUN_INDICATOR("run_indicator"),
    AUDIT("audit");

    private final String dbValue;

    Permission(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Permission fromDbValue(String value) {
        for (Permission permission : values()) {
            if (permission.dbValue.equals(value)) {
                return permission;
            }
        }
        throw new IllegalArgumentException("unknown permission: " + value);
    }
}
