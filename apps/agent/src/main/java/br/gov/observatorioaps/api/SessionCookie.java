package br.gov.observatorioaps.api;

/** Name of the opaque session cookie — never {@code JSESSIONID}, this is not {@code HttpSession}. */
final class SessionCookie {
    static final String NAME = "OBS_SESSION";

    private SessionCookie() {
    }
}
