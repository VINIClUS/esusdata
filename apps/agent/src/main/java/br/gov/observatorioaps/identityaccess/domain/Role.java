package br.gov.observatorioaps.identityaccess.domain;

/**
 * The four role labels the Tech Spec names verbatim (§1.12 L427): "administrador técnico, gestor,
 * profissional com escopo de equipe e auditor." The role→permission matrix behind these names is
 * this project's own decision — {@code docs/adr/0007-modelo-de-permissoes.md} — seeded in
 * {@code V3__identity_access.sql}'s {@code role_permissions} table, not the spec's text.
 */
public enum Role {
    TECHNICAL_ADMIN,
    MANAGER,
    TEAM_SCOPED_PROFESSIONAL,
    AUDITOR
}
