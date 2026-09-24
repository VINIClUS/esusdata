package esusdata.auth;

import esusdata.auth.model.AuthAuditWriter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes {@code auth_audit}. §1.5 L167: "Logs técnicos sem nomes, CPF, CNS, senhas ou conteúdo
 * clínico" — callers pass only actor/target/outcome identifiers, never secret material or
 * clinical data; this class does not attempt to sanitize a caller's mistake, it only never
 * receives one by construction (its parameter list has no room for a password or a patient
 * field).
 */
public final class JdbcAuthAuditWriter implements AuthAuditWriter {

    private final JdbcTemplate jdbc;

    public JdbcAuthAuditWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(
            Instant at, String actorUserId, String eventType, String target, String outcome, String detailJson) {
        jdbc.update(
                """
                INSERT INTO auth_audit (event_id, at, actor_user_id, event_type, target, outcome, detail_json)
                VALUES (?,?,?,?,?,?,?)
                """,
                "audit-" + UUID.randomUUID(),
                at.toString(),
                actorUserId,
                eventType,
                target,
                outcome,
                detailJson == null ? "{}" : detailJson);
    }
}
