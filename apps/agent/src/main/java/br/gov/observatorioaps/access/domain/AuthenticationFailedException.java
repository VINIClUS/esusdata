package br.gov.observatorioaps.access.domain;

/** Deliberately the same exception/message for "no such user" and "wrong password" (§1.12.7). */
public final class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
