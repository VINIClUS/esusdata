package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AllowedDestinationsTest {

    @Test
    void allowsAnExplicitlyApprovedLoopbackDestination() {
        var allowlist = new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 15433)));
        assertThatCode(() -> allowlist.assertAllowed("127.0.0.1", 15433)).doesNotThrowAnyException();
    }

    @Test
    void refusesADestinationNotOnTheAllowlist() {
        var allowlist = new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 15433)));
        assertThatThrownBy(() -> allowlist.assertAllowed("192.168.1.209", 5432))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class);
    }

    @Test
    void refusesTheSameHostOnADifferentPort() {
        var allowlist = new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("127.0.0.1", 15433)));
        assertThatThrownBy(() -> allowlist.assertAllowed("127.0.0.1", 5432))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class);
    }

    @Test
    void refusesAnUnresolvableHostEvenIfLiterallyAllowlisted() {
        var allowlist =
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort("nonexistent.invalid.test", 5432)));
        assertThatThrownBy(() -> allowlist.assertAllowed("nonexistent.invalid.test", 5432))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class);
    }

    @Test
    void requiresEveryResolvedAddressAndReturnsThePinnedAddress() throws Exception {
        InetAddress ipv4 = InetAddress.getByAddress("dual.test", new byte[] {127, 0, 0, 2});
        InetAddress ipv6 = InetAddress.getByAddress(
                "dual.test", new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        var allowlist = new AllowedDestinations(
                Set.of(
                        new AllowedDestinations.HostPort("dual.test", 5432),
                        new AllowedDestinations.HostPort(ipv4.getHostAddress(), 5432),
                        new AllowedDestinations.HostPort(ipv6.getHostAddress(), 5432)),
                ignored -> new InetAddress[] {ipv4, ipv6});

        assertThat(allowlist.assertAllowed("dual.test", 5432)).isEqualTo(ipv4);
    }

    @Test
    void rejectsAHostWhenOneOfItsResolvedAddressesIsNotApproved() throws Exception {
        InetAddress approved = InetAddress.getByAddress("dual.test", new byte[] {127, 0, 0, 2});
        InetAddress unapproved = InetAddress.getByAddress(
                "dual.test", new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2});
        var allowlist = new AllowedDestinations(
                Set.of(
                        new AllowedDestinations.HostPort("dual.test", 5432),
                        new AllowedDestinations.HostPort(approved.getHostAddress(), 5432)),
                ignored -> new InetAddress[] {approved, unapproved});

        assertThatThrownBy(() -> allowlist.assertAllowed("dual.test", 5432))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class)
                .hasMessageContaining(unapproved.getHostAddress());
    }

    @Test
    void acceptsAnExplicitlyAllowlistedIpv6Literal() throws Exception {
        InetAddress ipv6 = InetAddress.getByAddress(
                "v6.test", new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        var allowlist = new AllowedDestinations(
                Set.of(new AllowedDestinations.HostPort(ipv6.getHostAddress(), 5432)),
                ignored -> new InetAddress[] {ipv6});

        assertThat(allowlist.assertAllowed(ipv6.getHostAddress(), 5432)).isEqualTo(ipv6);
    }
}
