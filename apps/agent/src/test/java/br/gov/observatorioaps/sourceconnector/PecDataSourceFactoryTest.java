package br.gov.observatorioaps.sourceconnector;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PecDataSourceFactoryTest {

    @Test
    void pinsTheValidatedLiteralAddressAndMapsTheTwoTimeoutsSeparately() {
        var properties = new PecConnectionProperties(
                "source-1", "127.0.0.1", 15433, "esus", "reader", "DB_PASSWORD", "3541307");
        var allowlist = new AllowedDestinations(
                Set.of(new AllowedDestinations.HostPort("127.0.0.1", 15433)));
        var budget = new ReadBudget(
                2, Duration.ofSeconds(3), Duration.ofSeconds(7), 30_000, 10_000, 30_000,
                200_000, 60_000);

        HikariDataSource dataSource = new PecDataSourceFactory(
                allowlist, ignored -> "secret".toCharArray()).create(properties, budget);
        try {
            assertThat(dataSource.getJdbcUrl())
                    .isEqualTo("jdbc:postgresql://127.0.0.1:15433/esus");
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(7_000);
            assertThat(dataSource.getDataSourceProperties().get("connectTimeout")).isEqualTo(3);
        } finally {
            dataSource.close();
        }
    }

    @Test
    void bracketsAValidatedIpv6AddressInTheJdbcUrl() throws Exception {
        var address = java.net.InetAddress.getByAddress(
                "v6.test", new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 1});
        var properties = new PecConnectionProperties(
                "source-v6", "v6.test", 5432, "esus", "reader", "DB_PASSWORD", "3541307");
        var allowlist = new AllowedDestinations(
                Set.of(
                        new AllowedDestinations.HostPort("v6.test", 5432),
                        new AllowedDestinations.HostPort(address.getHostAddress(), 5432)),
                ignored -> new java.net.InetAddress[]{address});

        HikariDataSource dataSource = new PecDataSourceFactory(
                allowlist, ignored -> "secret".toCharArray()).create(
                        properties, ReadBudget.initialEngineeringProposal());
        try {
            assertThat(dataSource.getJdbcUrl())
                    .isEqualTo("jdbc:postgresql://[" + address.getHostAddress() + "]:5432/esus");
        } finally {
            dataSource.close();
        }
    }
}
