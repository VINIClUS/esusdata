package br.gov.observatorioaps.access.application;

/**
 * §1.12.7 L536: "Senhas entre 15 e 128 caracteres, sem truncamento silencioso; aceitar frases."
 * Length is measured in Unicode code points, not UTF-16 chars or bytes — a passphrase with
 * characters outside the BMP must not be silently mismeasured.
 */
public final class PasswordPolicy {

    private final SecurityProperties properties;

    public PasswordPolicy(SecurityProperties properties) {
        this.properties = properties;
    }

    public void validate(String rawPassword) {
        if (rawPassword == null) {
            throw new WeakPasswordException("password is required");
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        if (length < properties.passwordMinLength()) {
            throw new WeakPasswordException(
                    "password must be at least " + properties.passwordMinLength() + " characters");
        }
        if (length > properties.passwordMaxLength()) {
            throw new WeakPasswordException(
                    "password must be at most " + properties.passwordMaxLength() + " characters");
        }
    }

    public static final class WeakPasswordException extends RuntimeException {
        public WeakPasswordException(String message) {
            super(message);
        }
    }
}
