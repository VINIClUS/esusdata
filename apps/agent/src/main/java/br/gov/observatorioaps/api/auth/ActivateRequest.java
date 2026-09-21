package br.gov.observatorioaps.api.auth;

public record ActivateRequest(String token, String password) {
}
