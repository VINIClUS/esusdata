package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PecDataSourceFactoryTest {

    private static final PecSourceIdentity CT133_IDENTITY =
            new PecSourceIdentity("bound-identity-source", "5.4.37", "PEC_DW", "PRONTUARIO");

    @Test
    void rawPoolCreationIsNotPublic() throws Exception {
        var create =
                PecDataSourceFactory.class.getDeclaredMethod("create", PecConnectionProperties.class, ReadBudget.class);

        assertThat(Modifier.isPublic(create.getModifiers())).isFalse();
    }

    @Test
    void acquisitionEntryPointRequiresTheDeploymentIdentity() {
        assertThat(Arrays.stream(PecDataSourceFactory.class.getDeclaredMethods())
                        .filter(method -> "open".equals(method.getName()))
                        .anyMatch(method ->
                                Arrays.asList(method.getParameterTypes()).contains(PecSourceIdentity.class)))
                .isTrue();
    }

    @Test
    void sourceConnectionExposesItsBoundDeploymentIdentity() throws Exception {
        assertThat(PecSourceConnection.class.getDeclaredMethod("sourceIdentity"))
                .isNotNull();
    }

    @Test
    void sourceConnectionCreatesAnImmutablePeriodBoundAcquisition() {
        assertThat(Arrays.stream(PecSourceConnection.class.getDeclaredMethods())
                        .anyMatch(method ->
                                "acquire".equals(method.getName()) && method.getReturnType() == PecAcquisition.class))
                .isTrue();
    }

    @Test
    void missingDeploymentIdentityBlocksOpeningBeforeDestinationValidation() {
        var properties = new PecConnectionProperties(
                "missing-identity-source", "127.0.0.1", 15_433, "esus", "reader", "DB_PASSWORD", "3541307");
        var factory = new PecDataSourceFactory(new AllowedDestinations(Set.of()), ignored -> "secret".toCharArray());

        assertThatThrownBy(() -> factory.open(properties, null, ReadBudget.initialEngineeringProposal()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PecSourceIdentity");
    }

    @Test
    void identityForAnotherSourceCannotBePairedWithThisSourceProperties() {
        var properties = new PecConnectionProperties(
                "source-a", "127.0.0.1", 15_433, "esus", "reader", "DB_PASSWORD", "3541307");
        var factory = new PecDataSourceFactory(new AllowedDestinations(Set.of()), ignored -> "secret".toCharArray());

        assertThatThrownBy(() -> factory.open(
                        properties,
                        new PecSourceIdentity("source-b", "5.4.37", "PEC_DW", "PRONTUARIO"),
                        ReadBudget.initialEngineeringProposal()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void periodAcquisitionCreatesItsGuardFromTheOpenedReadBudget() {
        var budget = new ReadBudget(
                1, Duration.ofSeconds(1), Duration.ofSeconds(1), 10_000, 10_000, 10_000, 10, 10_000, 10_000, 10_000);
        var properties = new PecConnectionProperties(
                "bound-budget-source", "127.0.0.1", 15_433, "esus", "reader", "DB_PASSWORD", "3541307");
        // binds a Mockito mock connection: nothing to release
        @SuppressWarnings("PMD.CloseResource")
        var sourceConnection = PecSourceConnectionTestSupport.bind(
                Mockito.mock(java.sql.Connection.class),
                properties,
                new PecSourceIdentity("bound-budget-source", "5.4.37", "PEC_DW", "PRONTUARIO"),
                budget);

        var acquisition =
                sourceConnection.acquire(java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 4, 1));

        assertThat(acquisition.budgetGuard().budget()).isSameAs(budget);
    }

    @Test
    void sourceConnectionReturnsTheIdentityWithWhichItWasBound() {
        var connection = Mockito.mock(java.sql.Connection.class);
        var properties = new PecConnectionProperties(
                "bound-identity-source", "127.0.0.1", 15_433, "esus", "reader", "DB_PASSWORD", "3541307");

        // binds a Mockito mock connection: nothing to release
        @SuppressWarnings("PMD.CloseResource")
        var bound = PecSourceConnectionTestSupport.bind(connection, properties, CT133_IDENTITY);

        assertThat(bound.sourceIdentity()).isSameAs(CT133_IDENTITY);
    }

    @Test
    void pinsTheValidatedLiteralAddressAndMapsTheTwoTimeoutsSeparately() {
        var properties = new PecConnectionProperties(
                "source-1", "127.0.0.1", 15_433, "esus", "reader", "DB_PASSWORD", "3541307");
        var allowlist = new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 15_433)));
        var budget = new ReadBudget(
                2, Duration.ofSeconds(3), Duration.ofSeconds(7), 30_000, 10_000, 30_000, 200_000, 60_000);

        try (HikariDataSource dataSource =
                new PecDataSourceFactory(allowlist, ignored -> "secret".toCharArray()).create(properties, budget)) {
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:15433/esus");
            assertThat(dataSource.getTransactionIsolation()).isEqualTo("TRANSACTION_REPEATABLE_READ");
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(7_000);
            assertThat(dataSource.getDataSourceProperties().get("connectTimeout"))
                    .isEqualTo(3);
            assertThat(dataSource.getDataSourceProperties().get("socketTimeout"))
                    .isEqualTo(60);
            assertThat(dataSource.getDataSourceProperties().get("cancelSignalTimeout"))
                    .isEqualTo(3);
        }
    }

    @Test
    void bracketsAValidatedIpv6AddressInTheJdbcUrl() throws Exception {
        var address = java.net.InetAddress.getByAddress(
                "v6.test", new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        var properties =
                new PecConnectionProperties("source-v6", "v6.test", 5432, "esus", "reader", "DB_PASSWORD", "3541307");
        var allowlist = new AllowedDestinations(
                Set.of(
                        new AllowedDestinations.HostPort("v6.test", 5432),
                        new AllowedDestinations.HostPort(address.getHostAddress(), 5432)),
                ignored -> new java.net.InetAddress[] {address});

        try (HikariDataSource dataSource = new PecDataSourceFactory(allowlist, ignored -> "secret".toCharArray())
                .create(properties, ReadBudget.initialEngineeringProposal())) {
            assertThat(dataSource.getJdbcUrl())
                    .isEqualTo("jdbc:postgresql://[" + address.getHostAddress() + "]:5432/esus");
        }
    }

    @Test
    void rejectsTimeoutsThatCannotBeRepresentedByPgJdbc() {
        var properties = new PecConnectionProperties(
                "source-large-timeout", "127.0.0.1", 15_433, "esus", "reader", "DB_PASSWORD", "3541307");
        var allowlist = new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 15_433)));
        var budget = new ReadBudget(
                2, Duration.ofSeconds(3), Duration.ofSeconds(7), 30_000, 10_000, 30_000, 200_000, Long.MAX_VALUE);

        assertThatThrownBy(() -> new PecDataSourceFactory(allowlist, ignored -> "secret".toCharArray())
                        .create(properties, budget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDurationMs");
    }

    @Test
    void releasesTheSourcePermitWhenPoolCreationFailsBeforeAConnectionIsOpened() {
        var properties = new PecConnectionProperties(
                "permit-release-source", "127.0.0.1", 15_433, "esus", "reader", "DB_PASSWORD", "3541307");
        var factory = new PecDataSourceFactory(new AllowedDestinations(Set.of()), ignored -> "secret".toCharArray());

        assertThatThrownBy(() -> factory.open(
                        properties,
                        new PecSourceIdentity("permit-release-source", "5.4.37", "PEC_DW", "PRONTUARIO"),
                        ReadBudget.initialEngineeringProposal()))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class);
        assertThatThrownBy(() -> factory.open(
                        properties,
                        new PecSourceIdentity("permit-release-source", "5.4.37", "PEC_DW", "PRONTUARIO"),
                        ReadBudget.initialEngineeringProposal()))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class);
    }
}
