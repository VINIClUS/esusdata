package br.gov.observatorioaps.config;

import br.gov.observatorioaps.identityaccess.AccessAdministrationService;
import br.gov.observatorioaps.identityaccess.Argon2Profile;
import br.gov.observatorioaps.identityaccess.AuthenticationService;
import br.gov.observatorioaps.identityaccess.AuthAuditWriter;
import br.gov.observatorioaps.identityaccess.AuthorizationVersionGuard;
import br.gov.observatorioaps.identityaccess.BootstrapActivation;
import br.gov.observatorioaps.identityaccess.GrantRepository;
import br.gov.observatorioaps.identityaccess.GrantRevalidator;
import br.gov.observatorioaps.identityaccess.LoginThrottle;
import br.gov.observatorioaps.identityaccess.PasswordPolicy;
import br.gov.observatorioaps.identityaccess.ReauthenticationGuard;
import br.gov.observatorioaps.identityaccess.ScopeResolver;
import br.gov.observatorioaps.identityaccess.SecurityProperties;
import br.gov.observatorioaps.identityaccess.SessionService;
import br.gov.observatorioaps.identityaccess.UserProvisioning;
import br.gov.observatorioaps.identityaccess.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

/**
 * Wires {@code identityaccess}. Every bean here that touches {@code users}/{@code user_grants}/
 * {@code sessions}/etc. depends, directly or transitively, on {@code flywayMigration} (V3).
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class IdentityAccessConfig {

    private static final Logger log = LoggerFactory.getLogger(IdentityAccessConfig.class);

    @Bean
    @DependsOn("flywayMigration")
    public UserRepository userRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new UserRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public GrantRepository grantRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new GrantRepository(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public ScopeResolver scopeResolver(JdbcTemplate sqliteJdbcTemplate) {
        return new ScopeResolver(sqliteJdbcTemplate);
    }

    @Bean
    public GrantRevalidator grantRevalidator(ScopeResolver scopeResolver, UserRepository userRepository) {
        return new GrantRevalidator(scopeResolver, userRepository);
    }

    @Bean
    public PasswordPolicy passwordPolicy(SecurityProperties properties) {
        return new PasswordPolicy(properties);
    }

    @Bean
    public Argon2Profile argon2Profile(SecurityProperties properties) {
        return new Argon2Profile(properties);
    }

    @Bean
    @DependsOn("flywayMigration")
    public SessionService sessionService(
            JdbcTemplate sqliteJdbcTemplate, UserRepository userRepository, SecurityProperties properties) {
        return new SessionService(sqliteJdbcTemplate, userRepository, properties);
    }

    @Bean
    public ReauthenticationGuard reauthenticationGuard(SessionService sessionService) {
        return new ReauthenticationGuard(sessionService);
    }

    @Bean
    @DependsOn("flywayMigration")
    public AuthenticationService authenticationService(
            UserRepository userRepository, Argon2Profile argon2Profile, SessionService sessionService,
            LoginThrottle loginThrottle, AuthAuditWriter authAuditWriter, Clock clock) {
        return new AuthenticationService(
                userRepository, argon2Profile, sessionService, loginThrottle, authAuditWriter, clock);
    }

    @Bean
    @DependsOn("flywayMigration")
    public AuthAuditWriter authAuditWriter(JdbcTemplate sqliteJdbcTemplate) {
        return new AuthAuditWriter(sqliteJdbcTemplate);
    }

    @Bean
    @DependsOn("flywayMigration")
    public AuthorizationVersionGuard authorizationVersionGuard(
            JdbcTemplate sqliteJdbcTemplate, TransactionTemplate sqliteTransactionTemplate,
            UserRepository userRepository, AuthAuditWriter authAuditWriter, Clock clock) {
        return new AuthorizationVersionGuard(
                sqliteJdbcTemplate, sqliteTransactionTemplate, userRepository, authAuditWriter, clock);
    }

    @Bean
    @DependsOn("flywayMigration")
    public LoginThrottle loginThrottle(JdbcTemplate sqliteJdbcTemplate, SecurityProperties properties) {
        return new LoginThrottle(sqliteJdbcTemplate, properties);
    }

    @Bean
    @DependsOn("flywayMigration")
    public UserProvisioning userProvisioning(
            UserRepository userRepository, JdbcTemplate sqliteJdbcTemplate,
            TransactionTemplate sqliteTransactionTemplate, Clock clock, SecurityProperties properties) {
        return new UserProvisioning(userRepository, sqliteJdbcTemplate, sqliteTransactionTemplate, clock, properties);
    }

    @Bean
    @DependsOn("flywayMigration")
    public AccessAdministrationService accessAdministrationService(
            UserRepository userRepository, GrantRepository grantRepository,
            AuthorizationVersionGuard authorizationVersionGuard, TransactionTemplate sqliteTransactionTemplate,
            Clock clock) {
        return new AccessAdministrationService(
                userRepository, grantRepository, authorizationVersionGuard, sqliteTransactionTemplate, clock);
    }

    @Bean
    @DependsOn("flywayMigration")
    public BootstrapActivation bootstrapActivation(
            UserRepository userRepository, GrantRepository grantRepository, JdbcTemplate sqliteJdbcTemplate,
            TransactionTemplate sqliteTransactionTemplate, Clock clock, SecurityProperties properties,
            PasswordPolicy passwordPolicy, Argon2Profile argon2Profile, SqliteProperties sqliteProperties) {
        return new BootstrapActivation(userRepository, grantRepository, sqliteJdbcTemplate,
                sqliteTransactionTemplate, clock, properties, passwordPolicy, argon2Profile,
                sqliteProperties.resolvedDirectory());
    }

    /**
     * Runs at boot, like {@code jobRecoveryReport} — eager {@code @Bean} factories execute during
     * {@code finishBeanFactoryInitialization}, strictly before Tomcat's own {@code SmartLifecycle}
     * starts accepting connections. Idempotent: does nothing once a TECHNICAL_ADMIN already
     * exists. Logs only the activation token's file path — never its contents.
     */
    @Bean
    @DependsOn({"flywayMigration", "userRepository"})
    public Optional<Path> bootstrapActivationResult(BootstrapActivation bootstrapActivation) throws java.io.IOException {
        Optional<Path> tokenFile = bootstrapActivation.ensureBootstrapAdmin();
        tokenFile.ifPresentOrElse(
                path -> log.info("Bootstrap admin created; activation token written to {}", path),
                () -> log.info("Bootstrap admin already exists; no new activation token issued"));
        return tokenFile;
    }
}
