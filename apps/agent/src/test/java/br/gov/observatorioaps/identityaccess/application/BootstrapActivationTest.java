package br.gov.observatorioaps.identityaccess.application;

import br.gov.observatorioaps.identityaccess.infrastructure.jdbc.JdbcGrantRepository;

import br.gov.observatorioaps.identityaccess.infrastructure.jdbc.JdbcUserRepository;

import br.gov.observatorioaps.platform.sqlite.SqliteDataSourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.gov.observatorioaps.identityaccess.domain.Role;
import br.gov.observatorioaps.identityaccess.domain.UserAccount;
import br.gov.observatorioaps.identityaccess.domain.UserState;
import br.gov.observatorioaps.identityaccess.domain.GrantRepository;
import br.gov.observatorioaps.identityaccess.domain.UserRepository;
import br.gov.observatorioaps.identityaccess.infrastructure.spring.Argon2Profile;
import br.gov.observatorioaps.identityaccess.infrastructure.spring.SecurityProperties;
/**
 * Proves two fixes found in PR review: a token-file write failure never leaves a
 * {@code TECHNICAL_ADMIN} grant whose only activation token is unreachable ({@link
 * BootstrapActivation#ensureBootstrapAdmin}), and racing {@link BootstrapActivation#activate}
 * calls against the same token can never both win.
 */
class BootstrapActivationTest {

    @TempDir
    Path dataDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactionTemplate;
    private UserRepository userRepository;
    private GrantRepository grantRepository;
    private Clock clock;
    private SecurityProperties properties;
    private PasswordPolicy passwordPolicy;
    private Argon2Profile argon2Profile;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        context = new AnnotationConfigApplicationContext();
        context.register(SqliteDataSourceConfig.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("observatorio.data.directory", dataDir.resolve("db").toString())));
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        transactionTemplate = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        userRepository = new JdbcUserRepository(jdbc);
        grantRepository = new JdbcGrantRepository(jdbc);
        properties = new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 19456, 2, 1, 16, 32, "v1", 30, 24);
        passwordPolicy = new PasswordPolicy(properties);
        argon2Profile = new Argon2Profile(properties);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private BootstrapActivation activationOver(Path tokenDirectory) {
        return new BootstrapActivation(userRepository, grantRepository, jdbc, transactionTemplate,
                clock, properties, passwordPolicy, argon2Profile, tokenDirectory);
    }

    @Test
    void aTokenFileWriteFailureLeavesNoOrphanedGrant() {
        // The parent directory does not exist, so writeTokenFile's Files.createFile fails before
        // any database row is written.
        Path unwritableDirectory = dataDir.resolve("missing-parent").resolve("nested");
        BootstrapActivation activation = activationOver(unwritableDirectory);

        assertThatThrownBy(activation::ensureBootstrapAdmin).isInstanceOf(IOException.class);

        assertThat(userRepository.anyExistsWithRole(Role.TECHNICAL_ADMIN)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from activation_tokens", Integer.class)).isZero();
    }

    @Test
    void ensureBootstrapAdminIsRecoverableAfterAFailedAttempt() throws IOException {
        Path unwritableDirectory = dataDir.resolve("missing-parent").resolve("nested");
        assertThatThrownBy(activationOver(unwritableDirectory)::ensureBootstrapAdmin)
                .isInstanceOf(IOException.class);

        Path tokenDirectory = Files.createDirectory(dataDir.resolve("tokens"));
        BootstrapActivation recovered = activationOver(tokenDirectory);
        Path tokenFile = recovered.ensureBootstrapAdmin().orElseThrow();

        assertThat(Files.exists(tokenFile)).isTrue();
        assertThat(userRepository.anyExistsWithRole(Role.TECHNICAL_ADMIN)).isTrue();
    }

    @Test
    void aSecondActivationOfTheSameTokenNeverChangesTheAlreadyActivatedAccount() throws IOException {
        Path tokenDirectory = Files.createDirectory(dataDir.resolve("tokens"));
        BootstrapActivation activation = activationOver(tokenDirectory);
        Path tokenFile = activation.ensureBootstrapAdmin().orElseThrow();
        String rawToken = Files.readAllLines(tokenFile).get(0).trim();

        UserAccount firstActivation = activation.activate(rawToken, "a-strong-enough-passphrase-1", clock.instant());

        assertThatThrownBy(() ->
                activation.activate(rawToken, "a-different-strong-passphrase-2", clock.instant()))
                .isInstanceOf(BootstrapActivation.ActivationFailedException.class)
                .hasMessageContaining("already used");

        // The claim (consumed_at) and the account mutation (password/state) commit as one unit —
        // a rejected second call must never overwrite the first activation's password.
        UserAccount current = userRepository.findById(firstActivation.userId()).orElseThrow();
        assertThat(current.passwordHash()).isEqualTo(firstActivation.passwordHash());
        assertThat(current.state()).isEqualTo(UserState.ACTIVE);
        assertThat(jdbc.queryForObject(
                "select count(*) from activation_tokens where consumed_at is not null", Integer.class))
                .isEqualTo(1);
    }
}
