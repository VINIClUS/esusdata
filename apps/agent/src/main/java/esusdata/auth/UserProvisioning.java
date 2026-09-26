package esusdata.auth;

import esusdata.auth.model.ActivationTokens;
import esusdata.auth.model.AuthAuditWriter;
import esusdata.auth.model.UserAccount;
import esusdata.auth.model.UserRepository;
import esusdata.auth.model.UserState;
import esusdata.web.ApiNotFoundException;
import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * §1.12.7 L536 applied uniformly, not only to the bootstrap admin: every account starts {@code
 * PENDING_ACTIVATION} with an unusable password, and only a single-use token — never a default
 * password — turns it into a usable account. {@link BootstrapActivation#activate} is the SAME
 * consumption path used here; there is exactly one way any account in this system is ever
 * activated.
 *
 * <p>Unlike the bootstrap token (written to a local file only the service account can read,
 * because no authenticated caller exists yet at first boot), this token is returned ONCE in the
 * response to an already-authenticated {@code manage_access} caller — the equivalent handoff for
 * a caller that has already crossed the trust boundary. This HTTP-facing behavior is a project
 * decision the spec does not define, registered in {@code docs/adr/0008-superficie-http-de-autenticacao.md}.
 */
public final class UserProvisioning {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SecurityProperties properties;
    private final AuthAuditWriter auditWriter;

    public UserProvisioning(
            UserRepository userRepository,
            JdbcTemplate jdbc,
            TransactionTemplate transactionTemplate,
            Clock clock,
            SecurityProperties properties,
            AuthAuditWriter auditWriter) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.properties = properties;
        this.auditWriter = auditWriter;
    }

    public record ProvisionedUser(String userId, String activationToken, Instant expiresAt) {}

    public record PendingUser(String userId, String username, String displayName, Instant expiresAt) {}

    public List<PendingUser> pendingActivation() {
        return jdbc.query(
                """
                select u.user_id, u.username, u.display_name, max(t.expires_at) as expires_at
                  from users u left join activation_tokens t
                    on t.user_id = u.user_id and t.consumed_at is null
                 where u.state = 'PENDING_ACTIVATION'
                 group by u.user_id, u.username, u.display_name
                 order by u.username
                """,
                (rs, row) -> new PendingUser(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("expires_at") == null ? null : Instant.parse(rs.getString("expires_at"))));
    }

    public ProvisionedUser reissue(String userId, String actorUserId) {
        Instant now = clock.instant();
        String rawToken = ActivationTokens.newOpaqueToken();
        Instant expiresAt = now.plus(Duration.ofHours(properties.activationTokenValidityHours()));
        return transactionTemplate.execute(status -> {
            UserAccount user =
                    userRepository.findById(userId).orElseThrow(() -> new ApiNotFoundException("user not found"));
            if (user.state() != UserState.PENDING_ACTIVATION) {
                throw new IllegalArgumentException("user is not pending activation");
            }
            jdbc.update("delete from activation_tokens where user_id = ?", userId);
            jdbc.update("""
                    insert into activation_tokens (token_hash, user_id, expires_at, consumed_at)
                    values (?,?,?,null)
                    """, ActivationTokens.hash(rawToken), userId, expiresAt.toString());
            auditWriter.record(now, actorUserId, "ACTIVATION_TOKEN_REISSUED", userId, "SUCCESS", "{}");
            return new ProvisionedUser(userId, rawToken, expiresAt);
        });
    }

    public ProvisionedUser provision(String username, String displayName, String createdBy) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException("username already exists: " + username);
        }
        Instant now = clock.instant();
        String userId = "user-" + UUID.randomUUID();
        String rawToken = ActivationTokens.newOpaqueToken();
        Instant expiresAt = now.plus(Duration.ofHours(properties.activationTokenValidityHours()));

        transactionTemplate.executeWithoutResult(status -> {
            userRepository.insert(new UserAccount(
                    userId,
                    username,
                    displayName,
                    "UNSET",
                    "NONE",
                    "{}",
                    properties.securityPolicyVersion(),
                    1,
                    UserState.PENDING_ACTIVATION,
                    now,
                    createdBy,
                    null));
            jdbc.update("""
                    INSERT INTO activation_tokens (token_hash, user_id, expires_at, consumed_at)
                    VALUES (?,?,?,null)
                    """, ActivationTokens.hash(rawToken), userId, expiresAt.toString());
        });
        return new ProvisionedUser(userId, rawToken, expiresAt);
    }

    public static final class UsernameAlreadyExistsException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public UsernameAlreadyExistsException(String message) {
            super(message);
        }
    }
}
