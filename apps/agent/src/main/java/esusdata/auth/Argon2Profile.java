package esusdata.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * §1.12.7 L534: "Argon2id com salt individual, parâmetros versionados... calibrar custo no
 * SO-alvo e registrar parâmetros efetivos. Não usar hash rápido nem criptografia reversível para
 * senha." Spring Security's {@link Argon2PasswordEncoder} delegates to BouncyCastle's
 * {@code Argon2BytesGenerator} (verified against the resolved jar via {@code javap} in this
 * project's ADR) — {@code bcprov-jdk18on} is a hard runtime dependency of this class, not
 * incidental.
 *
 * <p>The encoded hash string is self-describing (embeds its own m/t/p per the standard Argon2
 * encoding), so verification never depends on the CURRENT baseline in {@link SecurityProperties}
 * — a later baseline change cannot silently reinterpret an old hash. {@link #effectiveParamsJson()}
 * exists only so {@code users.password_params_json} records what was actually used, per L534.
 */
public final class Argon2Profile {

    public static final String ALGO = "ARGON2ID";

    private final SecurityProperties properties;
    private final Argon2PasswordEncoder encoder;

    public Argon2Profile(SecurityProperties properties) {
        this.properties = properties;
        this.encoder = new Argon2PasswordEncoder(
                properties.argon2SaltLength(), properties.argon2HashLength(),
                properties.argon2Parallelism(), properties.argon2MemoryKib(),
                properties.argon2Iterations());
    }

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedHash) {
        return encoder.matches(rawPassword, encodedHash);
    }

    public String effectiveParamsJson() {
        return "{\"saltLength\":" + properties.argon2SaltLength()
                + ",\"hashLength\":" + properties.argon2HashLength()
                + ",\"parallelism\":" + properties.argon2Parallelism()
                + ",\"memoryKib\":" + properties.argon2MemoryKib()
                + ",\"iterations\":" + properties.argon2Iterations() + "}";
    }
}
