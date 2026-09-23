package esusdata.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.TreeSet;
import esusdata.auth.model.Permission;
import esusdata.auth.model.ScopeKind;
/**
 * Expands a user's active {@code user_grants} through {@code role_permissions} into effective
 * (permission, scope) pairs — read fresh from the database on every call, never cached in a
 * session (§1.7.3 L274). This is deliberately a live join against {@code role_permissions}
 * rather than a Java-side copy of the seed matrix: the database is the single source of truth for
 * the role→permission mapping, so the two can never drift apart.
 */
public final class ScopeResolver {

    public record EffectiveGrant(
            Permission permission, ScopeKind scopeKind, String municipalityIbge, String cnes, String ine) {
    }

    private static final RowMapper<EffectiveGrant> MAPPER = (rs, rowNum) -> new EffectiveGrant(
            Permission.fromDbValue(rs.getString("permission")),
            ScopeKind.valueOf(rs.getString("scope_kind")),
            rs.getString("municipality_ibge"), rs.getString("cnes"), rs.getString("ine"));

    private final JdbcTemplate jdbc;

    public ScopeResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EffectiveGrant> effectiveGrants(String userId) {
        return jdbc.query("""
                select rp.permission, g.scope_kind, g.municipality_ibge, g.cnes, g.ine
                  from user_grants g
                  join role_permissions rp on rp.role_id = g.role_id
                 where g.user_id = ? and g.revoked_at is null
                """, MAPPER, userId);
    }

    /**
     * §1.4.2 L140: municipality-scoped permissions require an active grant naming exactly that
     * municipality. Installation-scoped grants apply only to the permissions the spec treats as
     * purely technical (manage_source, manage_access, audit) — never to read_clinical/
     * run_indicator ("sem herdar acesso clínico"). A shortcut for {@code (municipalityIbge, null,
     * null)} below — an unscoped object, i.e. the municipal aggregate itself.
     */
    public boolean hasPermission(String userId, Permission permission, String municipalityIbge) {
        return hasPermission(userId, permission, municipalityIbge, null, null);
    }

    /**
     * §1.12 L427: apply município/CNES/INE scope, not just município. A grant with {@code cnes}
     * and {@code ine} both null is municipality-wide and covers every team, including the
     * unscoped municipal aggregate ({@code objectCnes}/{@code objectIne} both null). A grant
     * narrowed to a team only authorizes objects matching that same team — and, being narrower
     * than the municipality, never authorizes the unscoped aggregate: serving it would be exactly
     * the "job municipal parcialmente filtrado como se fosse job de equipe" §1.10.1 L403 forbids.
     */
    public boolean hasPermission(
            String userId, Permission permission, String municipalityIbge, String objectCnes, String objectIne) {
        if (userId == null || municipalityIbge == null) {
            return false;
        }
        for (EffectiveGrant grant : effectiveGrants(userId)) {
            if (grant.permission() != permission) {
                continue;
            }
            if (grant.scopeKind() == ScopeKind.INSTALLATION && isInstallationEligible(permission)) {
                return true;
            }
            if (grant.scopeKind() != ScopeKind.MUNICIPALITY || !municipalityIbge.equals(grant.municipalityIbge())) {
                continue;
            }
            boolean grantIsMunicipalityWide = grant.cnes() == null && grant.ine() == null;
            if (grantIsMunicipalityWide) {
                return true;
            }
            boolean objectIsMunicipalAggregate = objectCnes == null && objectIne == null;
            if (objectIsMunicipalAggregate) {
                continue;
            }
            boolean cnesMatches = grant.cnes() == null || grant.cnes().equals(objectCnes);
            boolean ineMatches = grant.ine() == null || grant.ine().equals(objectIne);
            if (cnesMatches && ineMatches) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the user holds ANY active grant for this permission covering the municipality,
     * team-scoped grants included — unlike {@link #hasPermission(String, Permission, String)},
     * which is deliberately stricter about the unscoped municipal aggregate. Used to gate
     * endpoints (evidence) where a team-scoped grant is legitimate and rows are narrowed
     * afterward, never rejected outright.
     */
    public boolean hasAnyMunicipalGrant(String userId, Permission permission, String municipalityIbge) {
        if (userId == null || municipalityIbge == null) {
            return false;
        }
        for (EffectiveGrant grant : effectiveGrants(userId)) {
            if (grant.permission() != permission) {
                continue;
            }
            if (grant.scopeKind() == ScopeKind.INSTALLATION && isInstallationEligible(permission)) {
                return true;
            }
            if (grant.scopeKind() == ScopeKind.MUNICIPALITY
                    && municipalityIbge.equals(grant.municipalityIbge())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The municipalities whose unscoped aggregate {@code permission} reaches — exactly the ones
     * {@link #hasPermission(String, Permission, String)} would accept, sorted. Team-scoped grants
     * are left out for the same reason they never authorize the aggregate.
     */
    public List<String> municipalitiesWithAggregateAccess(String userId, Permission permission) {
        TreeSet<String> municipalities = new TreeSet<>();
        for (EffectiveGrant grant : effectiveGrants(userId)) {
            if (grant.permission() == permission && grant.scopeKind() == ScopeKind.MUNICIPALITY
                    && grant.cnes() == null && grant.ine() == null) {
                municipalities.add(grant.municipalityIbge());
            }
        }
        return List.copyOf(municipalities);
    }

    public boolean hasInstallationPermission(String userId, Permission permission) {
        if (userId == null || !isInstallationEligible(permission)) {
            return false;
        }
        return effectiveGrants(userId).stream()
                .anyMatch(g -> g.permission() == permission && g.scopeKind() == ScopeKind.INSTALLATION);
    }

    private static boolean isInstallationEligible(Permission permission) {
        return permission == Permission.MANAGE_SOURCE
                || permission == Permission.MANAGE_ACCESS
                || permission == Permission.AUDIT;
    }
}
