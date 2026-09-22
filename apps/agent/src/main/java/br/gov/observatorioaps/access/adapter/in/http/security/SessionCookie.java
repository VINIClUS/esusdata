package br.gov.observatorioaps.access.adapter.in.http.security;

/** Name of the opaque session cookie — never {@code JSESSIONID}, this is not {@code HttpSession}. */
public final class SessionCookie {
    public static final String NAME = "OBS_SESSION";

    private SessionCookie() {
    }
}
