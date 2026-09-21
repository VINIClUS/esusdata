package br.gov.observatorioaps.identityaccess.domain;

/**
 * §1.4.2 L140: "Configuração e auditoria puramente técnicas podem ter escopo {@code INSTALLATION}
 * explícito, sem herdar acesso clínico." A {@code user_grants} row is one or the other, never
 * both — enforced structurally by the table's CHECK constraint, not just in Java.
 */
public enum ScopeKind {
    MUNICIPALITY,
    INSTALLATION
}
