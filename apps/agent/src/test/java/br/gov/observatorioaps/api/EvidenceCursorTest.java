package br.gov.observatorioaps.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §1.10.1 L405: cursor opaco, vinculado ao resultado/ordenação/escopo, e que não concede acesso
 * por si só. Pure unit test — no Spring context, no database.
 */
class EvidenceCursorTest {

    @Test
    void roundTripsTheSeqUnderTheSameResultOrderingAndScope() {
        String token = EvidenceCursor.of("result-1", "seq_asc", "3541307", 42L).encode();

        EvidenceCursor decoded = EvidenceCursor.decode(token, "result-1", "seq_asc", "3541307");

        assertThat(decoded.seq()).isEqualTo(42L);
    }

    @Test
    void isOpaqueNotARawNumber() {
        String token = EvidenceCursor.of("result-1", "seq_asc", "3541307", 42L).encode();

        assertThat(token).doesNotContain("42");
    }

    @Test
    void rejectsATamperedToken() {
        String token = EvidenceCursor.of("result-1", "seq_asc", "3541307", 42L).encode();
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertThatThrownBy(() -> EvidenceCursor.decode(tampered, "result-1", "seq_asc", "3541307"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsAMalformedToken() {
        assertThatThrownBy(() -> EvidenceCursor.decode("not-a-cursor", "result-1", "seq_asc", "3541307"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void aCursorMintedForADifferentResultIsRejected() {
        String token = EvidenceCursor.of("result-1", "seq_asc", "3541307", 42L).encode();

        assertThatThrownBy(() -> EvidenceCursor.decode(token, "result-2", "seq_asc", "3541307"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void aCursorMintedForADifferentScopeIsRejected() {
        // The "cursor não concede acesso" half of §1.10.1 L405: replaying a cursor minted for a
        // team-narrowed scope under a different (broader or different-team) scope must fail closed.
        String token = EvidenceCursor.of("result-1", "seq_asc", "3541307|0000346268|null", 42L).encode();

        assertThatThrownBy(() -> EvidenceCursor.decode(token, "result-1", "seq_asc", "3541307|null|null"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void aCursorMintedForADifferentOrderingIsRejected() {
        String token = EvidenceCursor.of("result-1", "seq_asc", "3541307", 42L).encode();

        assertThatThrownBy(() -> EvidenceCursor.decode(token, "result-1", "seq_desc", "3541307"))
                .isInstanceOf(InvalidCursorException.class);
    }
}
