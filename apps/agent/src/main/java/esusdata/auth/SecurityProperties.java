package esusdata.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * §1.12.7 L531, verbatim: "Os parâmetros abaixo são uma baseline de engenharia a aprovar no
 * piloto, não exigências normativas do SUS." The CONTROLS these parameters gate (inactivity
 * timeout, absolute duration, Argon2id-class hashing, reauth-for-sensitive-actions, progressive
 * login delay) are mandatory; the specific numbers below are the spec's own proposed baseline
 * (pendência P20) — not something this build invented, but also not yet institutionally approved.
 */
@ConfigurationProperties(prefix = "observatorio.security")
public record SecurityProperties(
        @DefaultValue("15") long inactivityMinutes,
        @DefaultValue("8") long absoluteDurationHours,
        @DefaultValue("5") long reauthWindowMinutes,
        @DefaultValue("5") int maxLoginAttempts,
        @DefaultValue("15") long throttleWindowMinutes,
        @DefaultValue("15") long throttleCeilingMinutes,
        @DefaultValue("15") int passwordMinLength,
        @DefaultValue("128") int passwordMaxLength,
        @DefaultValue("19456") int argon2MemoryKib,
        @DefaultValue("2") int argon2Iterations,
        @DefaultValue("1") int argon2Parallelism,
        @DefaultValue("16") int argon2SaltLength,
        @DefaultValue("32") int argon2HashLength,
        @DefaultValue("v1") String securityPolicyVersion,
        @DefaultValue("30") long authorizationRevalidationIntervalSeconds,
        @DefaultValue("24") long activationTokenValidityHours) {}
