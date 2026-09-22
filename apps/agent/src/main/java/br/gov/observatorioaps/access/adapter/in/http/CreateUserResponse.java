package br.gov.observatorioaps.access.adapter.in.http;

/** {@code activationToken} is returned exactly once — see {@code UserProvisioning} javadoc. */
public record CreateUserResponse(String userId, String activationToken, String expiresAt) {
}
