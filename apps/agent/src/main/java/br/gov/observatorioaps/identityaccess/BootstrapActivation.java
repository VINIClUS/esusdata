package br.gov.observatorioaps.identityaccess;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * §1.4 L110 / §1.12.7 L536: "Primeiro acesso por procedimento local de ativação, sem senha padrão
 * distribuída... ativação local de uso único." No default account or password is ever created —
 * the bootstrap user starts {@link UserState#PENDING_ACTIVATION} with an unusable placeholder
 * hash, and only {@link #activate} can turn it into a real, logged-in-capable account, by
 * consuming a single-use token written to a file only the service account can read.
 *
 * <p>§1.12.7 L548: the bootstrap admin's only grant is {@code TECHNICAL_ADMIN} — it never receives
 * {@code read_clinical} (see {@code V3__identity_access.sql}'s seed data), so activating it does
 * not, by itself, grant clinical access to anyone.
 */
public final class BootstrapActivation {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UNUSABLE_PASSWORD_HASH = "UNSET";
    private static final RowMapper<TokenRow> TOKEN_MAPPER = (rs, rowNum) -> new TokenRow(
            rs.getString("token_hash"), rs.getString("user_id"),
            Instant.parse(rs.getString("expires_at")),
            rs.getString("consumed_at") == null ? null : Instant.parse(rs.getString("consumed_at")));

    private final UserRepository userRepository;
    private final GrantRepository grantRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecurityProperties properties;
    private final PasswordPolicy passwordPolicy;
    private final Argon2Profile argon2Profile;
    private final Path tokenFile;

    public BootstrapActivation(
            UserRepository userRepository, GrantRepository grantRepository, JdbcTemplate jdbc,
            Clock clock, SecurityProperties properties, PasswordPolicy passwordPolicy,
            Argon2Profile argon2Profile, Path dataDirectory) {
        this.userRepository = userRepository;
        this.grantRepository = grantRepository;
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
        this.passwordPolicy = passwordPolicy;
        this.argon2Profile = argon2Profile;
        this.tokenFile = dataDirectory.resolve("bootstrap-activation.token");
    }

    /**
     * Idempotent: does nothing once any {@code TECHNICAL_ADMIN} user exists, so a restart never
     * creates a second bootstrap account or overwrites an unconsumed token.
     *
     * @return the token file path if a new bootstrap admin was created this call, empty otherwise
     */
    public Optional<Path> ensureBootstrapAdmin() throws IOException {
        if (userRepository.anyExistsWithRole(Role.TECHNICAL_ADMIN)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        String userId = "user-" + UUID.randomUUID();
        userRepository.insert(new UserAccount(
                userId, "admin", "Administrador técnico (ativação pendente)",
                UNUSABLE_PASSWORD_HASH, "NONE", "{}", properties.securityPolicyVersion(), 1,
                UserState.PENDING_ACTIVATION, now, "bootstrap", null));
        grantRepository.insert(new Grant(
                "grant-" + UUID.randomUUID(), userId, Role.TECHNICAL_ADMIN, ScopeKind.INSTALLATION,
                null, null, null, now, "bootstrap", null, null));

        String rawToken = newOpaqueToken();
        Instant expiresAt = now.plus(Duration.ofHours(properties.activationTokenValidityHours()));
        jdbc.update("""
                INSERT INTO activation_tokens (token_hash, user_id, expires_at, consumed_at)
                VALUES (?,?,?,null)
                """, hash(rawToken), userId, expiresAt.toString());

        writeTokenFile(rawToken, expiresAt);
        return Optional.of(tokenFile);
    }

    /** Consumes a single-use activation token, setting a real password and activating the account. */
    public UserAccount activate(String rawToken, String newRawPassword, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ActivationFailedException("activation token is required");
        }
        TokenRow row = jdbc.query("select * from activation_tokens where token_hash = ?",
                TOKEN_MAPPER, hash(rawToken)).stream().findFirst()
                .orElseThrow(() -> new ActivationFailedException("invalid activation token"));
        if (row.consumedAt() != null) {
            throw new ActivationFailedException("activation token already used");
        }
        if (!row.expiresAt().isAfter(now)) {
            throw new ActivationFailedException("activation token expired");
        }
        passwordPolicy.validate(newRawPassword);
        String encodedHash = argon2Profile.encode(newRawPassword);
        userRepository.setPassword(row.userId(), encodedHash, Argon2Profile.ALGO,
                argon2Profile.effectiveParamsJson(), properties.securityPolicyVersion());
        userRepository.setState(row.userId(), UserState.ACTIVE);
        jdbc.update("update activation_tokens set consumed_at = ? where token_hash = ?",
                now.toString(), hash(rawToken));
        return userRepository.findById(row.userId())
                .orElseThrow(() -> new IllegalStateException("activated user vanished mid-activation"));
    }

    private void writeTokenFile(String rawToken, Instant expiresAt) throws IOException {
        String contents = rawToken + "\n# expira em " + expiresAt
                + " — uso único; ver Tech Spec §1.12.7 e docs/adr/0008-superficie-http-de-autenticacao.md\n";
        PosixFileAttributeView posixView =
                Files.getFileAttributeView(tokenFile.getParent(), PosixFileAttributeView.class);
        if (posixView != null) {
            Files.deleteIfExists(tokenFile);
            Path created = Files.createFile(tokenFile, PosixFilePermissions.asFileAttribute(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
            Files.writeString(created, contents, StandardCharsets.UTF_8);
        } else {
            Files.writeString(tokenFile, contents, StandardCharsets.UTF_8);
        }
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record TokenRow(String tokenHash, String userId, Instant expiresAt, Instant consumedAt) {
    }

    public static final class ActivationFailedException extends RuntimeException {
        public ActivationFailedException(String message) {
            super(message);
        }
    }
}
