package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.result.dto.EvidenceCursor;
import esusdata.result.model.InvalidCursorException;
import org.junit.jupiter.api.Test;

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
        // Tamper the FIRST character of the MAC segment, not the last character of the whole
        // token: base64's final character of a group can encode as few as 4 real bits (the rest
        // is padding the encoder zeroes but decode() never re-checks) — with a 32-byte HMAC-SHA256
        // digest (2 bytes left over in the final group), flipping specifically the last character
        // has a real chance of only touching those unchecked padding bits and round-tripping to
        // the SAME bytes, since the process's HMAC key (and so the digest) is random per test run.
        // A group's first character always carries a full 6 bits of real data.
        int tamperIndex = token.indexOf('.') + 1;
        char original = token.charAt(tamperIndex);
        char replacement = original == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, tamperIndex) + replacement + token.substring(tamperIndex + 1);

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
        String token = EvidenceCursor.of("result-1", "seq_asc", "3541307|0000346268|null", 42L)
                .encode();

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
