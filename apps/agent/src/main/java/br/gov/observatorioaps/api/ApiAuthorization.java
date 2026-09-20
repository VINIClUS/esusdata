package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthAuditWriter;
import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.Permission;
import br.gov.observatorioaps.identityaccess.ReauthenticationGuard;
import br.gov.observatorioaps.identityaccess.ScopeKind;
import br.gov.observatorioaps.identityaccess.ScopeResolver;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * The one place an HTTP controller asks "is this session currently authorized for this object?"
 * — always resolved fresh against {@link ScopeResolver}, never cached (§1.7.3 L274). Every denial
 * is audited without clinical data (permission/municipality only) and surfaces to the controller
 * as {@link ScopeDeniedException}, which {@link ScopeCheckedAdvice} maps to the SAME 404 an
 * out-of-scope object gets as a genuinely nonexistent one (§1.10.1 L403).
 */
@Component
public final class ApiAuthorization {

    private final ScopeResolver scopeResolver;
    private final ReauthenticationGuard reauthenticationGuard;
    private final AuthAuditWriter authAuditWriter;
    private final Clock clock;

    public ApiAuthorization(
            ScopeResolver scopeResolver, ReauthenticationGuard reauthenticationGuard,
            AuthAuditWriter authAuditWriter, Clock clock) {
        this.scopeResolver = scopeResolver;
        this.reauthenticationGuard = reauthenticationGuard;
        this.authAuditWriter = authAuditWriter;
        this.clock = clock;
    }

    /** The municipal aggregate itself — a team-scoped grant never authorizes this (see ScopeResolver). */
    public void requireObjectScope(AuthenticatedSession session, Permission permission, String municipalityIbge) {
        requireObjectScope(session, permission, municipalityIbge, null, null);
    }

    public void requireObjectScope(
            AuthenticatedSession session, Permission permission, String municipalityIbge,
            String cnes, String ine) {
        if (!scopeResolver.hasPermission(session.userId(), permission, municipalityIbge, cnes, ine)) {
            deny(session, permission, municipalityIbge);
        }
    }

    /**
     * §1.4.2 L140: the purely-technical permissions (manage_source, manage_access, audit) an
     * {@code INSTALLATION}-scoped grant carries, with no municipality of their own — user/grant
     * administration is an installation-wide action, not a per-municipality one.
     */
    public void requireInstallationPermission(AuthenticatedSession session, Permission permission) {
        if (!scopeResolver.hasInstallationPermission(session.userId(), permission)) {
            authAuditWriter.record(clock.instant(), session.userId(), "ACCESS_DENIED",
                    "installation", "DENIED", "{\"permission\":\"" + permission.dbValue() + "\"}");
            throw new ScopeDeniedException(
                    "principal " + session.userId() + " lacks installation-scoped " + permission.dbValue());
        }
    }

    /** §1.12.7 L539: reauthentication within the last few minutes, for grant/source-secret mutations. */
    public void requireRecentReauth(AuthenticatedSession session) {
        reauthenticationGuard.requireRecentReauth(session, clock.instant());
    }

    /**
     * Gates endpoints (evidence) where a team-scoped grant is legitimate and rows are narrowed
     * afterward by {@link #resolveEvidenceTeamFilter}, never rejected outright.
     */
    public void requireAnyMunicipalScope(AuthenticatedSession session, Permission permission, String municipalityIbge) {
        if (!scopeResolver.hasAnyMunicipalGrant(session.userId(), permission, municipalityIbge)) {
            deny(session, permission, municipalityIbge);
        }
    }

    /**
     * §1.12 L427 CNES/INE narrowing of evidence rows: if the caller holds a municipality-wide
     * grant (cnes and ine both null) for this permission+municipality, evidence is not filtered
     * further. Otherwise the first team-scoped grant found narrows the rows returned. A caller
     * with more than one team-scoped grant for the same municipality — a configuration this
     * recorte does not model — only sees the first team matched; that is a known limitation, not
     * a defect this method hides.
     */
    public TeamScopeFilter resolveEvidenceTeamFilter(
            AuthenticatedSession session, Permission permission, String municipalityIbge) {
        TeamScopeFilter narrowed = null;
        for (ScopeResolver.EffectiveGrant grant : scopeResolver.effectiveGrants(session.userId())) {
            if (grant.permission() != permission
                    || grant.scopeKind() != ScopeKind.MUNICIPALITY
                    || !municipalityIbge.equals(grant.municipalityIbge())) {
                continue;
            }
            if (grant.cnes() == null && grant.ine() == null) {
                return TeamScopeFilter.unrestricted();
            }
            if (narrowed == null) {
                narrowed = new TeamScopeFilter(grant.cnes(), grant.ine());
            }
        }
        // Reached only when NO municipality-scoped grant matched at all — meaning the gate this
        // method is always called after (requireAnyMunicipalScope) must already have thrown for
        // an INSTALLATION-only grant, since that gate does not accept INSTALLATION for
        // READ_CLINICAL (only MANAGE_SOURCE/MANAGE_ACCESS/AUDIT are installation-eligible). This
        // is NOT a safe "no grant → show everything" default; it is dead code kept because a
        // caller bug that skips the gate would otherwise throw an NPE here instead of failing
        // loudly. Guarded by ScopeIsolationApiTest.anInstallationOnlyAuditGrantCannotReadEvidence.
        return narrowed == null ? TeamScopeFilter.unrestricted() : narrowed;
    }

    public record TeamScopeFilter(String cnes, String ine) {
        public static TeamScopeFilter unrestricted() {
            return new TeamScopeFilter(null, null);
        }
    }

    private void deny(AuthenticatedSession session, Permission permission, String municipalityIbge) {
        authAuditWriter.record(clock.instant(), session.userId(), "ACCESS_DENIED",
                municipalityIbge, "DENIED", "{\"permission\":\"" + permission.dbValue() + "\"}");
        throw new ScopeDeniedException(
                "principal " + session.userId() + " lacks " + permission.dbValue()
                        + " for the requested scope");
    }
}
