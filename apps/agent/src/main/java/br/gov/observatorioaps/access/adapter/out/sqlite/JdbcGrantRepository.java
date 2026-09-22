package br.gov.observatorioaps.access.adapter.out.sqlite;

import br.gov.observatorioaps.access.domain.GrantRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import br.gov.observatorioaps.access.application.ScopeResolver;
import br.gov.observatorioaps.access.domain.Grant;
import br.gov.observatorioaps.access.domain.Role;
import br.gov.observatorioaps.access.domain.ScopeKind;
/**
 * Persists {@code user_grants}. {@link #activeGrantsForUser} is the only read path
 * {@link ScopeResolver} uses — "concessões atuais" (§1.7.3 L274) means {@code revoked_at is null},
 * queried fresh every time, never cached.
 */
public final class JdbcGrantRepository implements GrantRepository {

    private static final RowMapper<Grant> MAPPER = (rs, rowNum) -> new Grant(
            rs.getString("grant_id"), rs.getString("user_id"), Role.valueOf(rs.getString("role_id")),
            ScopeKind.valueOf(rs.getString("scope_kind")), rs.getString("municipality_ibge"),
            rs.getString("cnes"), rs.getString("ine"), Instant.parse(rs.getString("granted_at")),
            rs.getString("granted_by"),
            rs.getString("revoked_at") == null ? null : Instant.parse(rs.getString("revoked_at")),
            rs.getString("revoked_by"));

    private final JdbcTemplate jdbc;

    public JdbcGrantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Grant grant) {
        jdbc.update("""
                INSERT INTO user_grants (grant_id, user_id, role_id, scope_kind, municipality_ibge,
                    cnes, ine, granted_at, granted_by, revoked_at, revoked_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                grant.grantId(), grant.userId(), grant.role().name(), grant.scopeKind().name(),
                grant.municipalityIbge(), grant.cnes(), grant.ine(), grant.grantedAt().toString(),
                grant.grantedBy(), grant.revokedAt() == null ? null : grant.revokedAt().toString(),
                grant.revokedBy());
    }

    public List<Grant> activeGrantsForUser(String userId) {
        return jdbc.query(
                "select * from user_grants where user_id = ? and revoked_at is null",
                MAPPER, userId);
    }

    /** @return {@code true} if a grant with this id was active and is now revoked by this call. */
    public boolean revoke(String grantId, Instant at, String revokedBy) {
        int updated = jdbc.update(
                "update user_grants set revoked_at = ?, revoked_by = ? where grant_id = ? and revoked_at is null",
                at.toString(), revokedBy, grantId);
        return updated == 1;
    }

    public void revokeAllForUser(String userId, Instant at, String revokedBy) {
        jdbc.update(
                "update user_grants set revoked_at = ?, revoked_by = ? where user_id = ? and revoked_at is null",
                at.toString(), revokedBy, userId);
    }
}
