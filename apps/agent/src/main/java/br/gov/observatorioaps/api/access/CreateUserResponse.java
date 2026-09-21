package br.gov.observatorioaps.api.access;

/** {@code activationToken} is returned exactly once — see {@code UserProvisioning} javadoc. */
public record CreateUserResponse(String userId, String activationToken, String expiresAt) {
}
