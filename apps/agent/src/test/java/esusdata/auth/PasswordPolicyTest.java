package esusdata.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * §1.12.7 L536: "Senhas entre 15 e 128 caracteres, sem truncamento silencioso; aceitar frases."
 * Uses the production baseline (15..128) via the same {@link SecurityProperties} defaults every
 * other component sees — not a test-only shortened range.
 */
class PasswordPolicyTest {

    private final SecurityProperties properties =
            new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 19_456, 2, 1, 16, 32, "v1", 30, 24);
    private final PasswordPolicy policy = new PasswordPolicy(properties);

    @Test
    void acceptsExactlyTheMinimumLength() {
        String password = "a".repeat(15);
        assertThat(password.codePointCount(0, password.length())).isEqualTo(15);
        assertThatCode(() -> policy.validate(password)).doesNotThrowAnyException();
    }

    @Test
    void acceptsExactlyTheMaximumLength() {
        String password = "a".repeat(128);
        assertThatCode(() -> policy.validate(password)).doesNotThrowAnyException();
    }

    @Test
    void rejectsOneCharacterUnderTheMinimum() {
        String password = "a".repeat(14);
        assertThatThrownBy(() -> policy.validate(password)).isInstanceOf(PasswordPolicy.WeakPasswordException.class);
    }

    @Test
    void rejectsOneCharacterOverTheMaximum() {
        String password = "a".repeat(129);
        assertThatThrownBy(() -> policy.validate(password)).isInstanceOf(PasswordPolicy.WeakPasswordException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(PasswordPolicy.WeakPasswordException.class);
    }

    @Test
    void acceptsAPassphraseWithSpaces() {
        // "aceitar frases" — a passphrase built from ordinary words, not a single dense token.
        String passphrase = "correto cavalo bateria grampo azul";
        assertThat(passphrase.length()).isGreaterThanOrEqualTo(15);
        policy.validate(passphrase);
    }

    @Test
    void measuresLengthInCodePointsNotUtf16CharsOrBytes() {
        // Each of these is one Unicode code point outside the BMP (a UTF-16 SURROGATE PAIR, two
        // chars) and four UTF-8 bytes — measuring by char count or byte count would overstate the
        // length relative to what the spec means by "caracteres" (code points).
        String fifteenSupplementaryCodePoints = "😀".repeat(15);
        assertThat(fifteenSupplementaryCodePoints.length()).isEqualTo(30); // 2 UTF-16 chars each
        assertThat(fifteenSupplementaryCodePoints.codePointCount(0, fifteenSupplementaryCodePoints.length()))
                .isEqualTo(15);

        policy.validate(fifteenSupplementaryCodePoints); // exactly at the minimum by code points

        String fourteenSupplementaryCodePoints = "😀".repeat(14);
        assertThatThrownBy(() -> policy.validate(fourteenSupplementaryCodePoints))
                .isInstanceOf(PasswordPolicy.WeakPasswordException.class);
    }
}
