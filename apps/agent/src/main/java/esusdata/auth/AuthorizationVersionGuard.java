package esusdata.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import esusdata.auth.model.AuthAuditWriter;
import esusdata.auth.model.UserRepository;
/**
 * §1.12.7 L541: "Mudança de papel/escopo, reset e bloqueio: invalidar sessões e atualizar versão
 * de autorização no servidor; encerrar SSE, cancelar novos acessos e impedir uso de exportação
 * pendente fora do escopo." Both effects (bump + revoke) happen in one short transaction so a
 * session can never observe the old {@code authorization_version} with sessions still valid, or
 * vice versa.
 */
public final class AuthorizationVersionGuard {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final UserRepository userRepository;
    private final AuthAuditWriter auditWriter;
    private final Clock clock;

    public AuthorizationVersionGuard(
            JdbcTemplate jdbc, TransactionTemplate transactionTemplate,
            UserRepository userRepository, AuthAuditWriter auditWriter, Clock clock) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.userRepository = userRepository;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    public void bumpAndRevokeSessions(String userId, String eventType, String reason, String actorUserId) {
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            userRepository.bumpAuthorizationVersion(userId);
            jdbc.update("""
                    update sessions set revoked_at = ?, revoked_reason = ?
                     where user_id = ? and revoked_at is null
                    """, now.toString(), reason, userId);
        });
        auditWriter.record(now, actorUserId, eventType, userId, "SUCCEEDED",
                "{\"reason\":\"" + reason.replace("\"", "'") + "\"}");
    }
}
