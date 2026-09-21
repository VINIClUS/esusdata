package br.gov.observatorioaps.identityaccess.infrastructure.jdbc;

import br.gov.observatorioaps.identityaccess.domain.UserRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.Optional;
import br.gov.observatorioaps.identityaccess.domain.Role;
import br.gov.observatorioaps.identityaccess.domain.UserAccount;
import br.gov.observatorioaps.identityaccess.domain.UserState;
/**
 * Persists {@code users}. Every read returns the row exactly as stored — no caching, no session
 * copy — because §1.7.3 L274 requires authorization decisions to use the user's CURRENT state,
 * never a snapshot taken at login.
 */
public final class JdbcUserRepository implements UserRepository {

    private static final RowMapper<UserAccount> MAPPER = (rs, rowNum) -> new UserAccount(
            rs.getString("user_id"), rs.getString("username"), rs.getString("display_name"),
            rs.getString("password_hash"), rs.getString("password_algo"),
            rs.getString("password_params_json"), rs.getString("security_policy_version"),
            rs.getLong("authorization_version"), UserState.valueOf(rs.getString("state")),
            Instant.parse(rs.getString("created_at")), rs.getString("created_by"),
            rs.getString("last_login_at") == null ? null : Instant.parse(rs.getString("last_login_at")));

    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UserAccount user) {
        jdbc.update("""
                INSERT INTO users (user_id, username, display_name, password_hash, password_algo,
                    password_params_json, security_policy_version, authorization_version, state,
                    created_at, created_by, last_login_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                user.userId(), user.username(), user.displayName(), user.passwordHash(),
                user.passwordAlgo(), user.passwordParamsJson(), user.securityPolicyVersion(),
                user.authorizationVersion(), user.state().name(), user.createdAt().toString(),
                user.createdBy(), user.lastLoginAt() == null ? null : user.lastLoginAt().toString());
    }

    public Optional<UserAccount> findById(String userId) {
        return jdbc.query("select * from users where user_id = ?", MAPPER, userId)
                .stream().findFirst();
    }

    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.query("select * from users where username = ?", MAPPER, username)
                .stream().findFirst();
    }

    public boolean anyExistsWithRole(Role role) {
        Integer count = jdbc.queryForObject("""
                select count(*) from users u
                 join user_grants g on g.user_id = u.user_id and g.revoked_at is null
                 where g.role_id = ?
                """, Integer.class, role.name());
        return count != null && count > 0;
    }

    public void recordLogin(String userId, Instant at) {
        jdbc.update("update users set last_login_at = ? where user_id = ?", at.toString(), userId);
    }

    public void setPassword(String userId, String passwordHash, String passwordAlgo,
            String passwordParamsJson, String securityPolicyVersion) {
        jdbc.update("""
                update users set password_hash = ?, password_algo = ?, password_params_json = ?,
                       security_policy_version = ?
                 where user_id = ?
                """, passwordHash, passwordAlgo, passwordParamsJson, securityPolicyVersion, userId);
    }

    public void setState(String userId, UserState state) {
        jdbc.update("update users set state = ? where user_id = ?", state.name(), userId);
    }

    /** @return the new {@code authorization_version} */
    public long bumpAuthorizationVersion(String userId) {
        jdbc.update("update users set authorization_version = authorization_version + 1 where user_id = ?", userId);
        Long version = jdbc.queryForObject(
                "select authorization_version from users where user_id = ?", Long.class, userId);
        return version == null ? 0 : version;
    }
}
