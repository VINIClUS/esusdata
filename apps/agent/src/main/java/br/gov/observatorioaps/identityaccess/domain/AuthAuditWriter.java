package br.gov.observatorioaps.identityaccess.domain;

import java.time.Instant;

/**
 * Writes {@code auth_audit}. §1.5 L167: "Logs técnicos sem nomes, CPF, CNS, senhas ou conteúdo
 * clínico" — callers pass only actor/target/outcome identifiers, never secret material or
 * clinical data; this class does not attempt to sanitize a caller's mistake, it only never
 * receives one by construction (its parameter list has no room for a password or a patient
 * field).
 */
public interface AuthAuditWriter {
    void record(Instant at, String actorUserId, String eventType, String target,
            String outcome, String detailJson);
}
