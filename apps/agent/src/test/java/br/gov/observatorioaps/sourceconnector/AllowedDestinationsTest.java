package br.gov.observatorioaps.sourceconnector;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

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
        var allowlist = new AllowedDestinations(
                Set.of(new AllowedDestinations.HostPort("nonexistent.invalid.test", 5432)));
        assertThatThrownBy(() -> allowlist.assertAllowed("nonexistent.invalid.test", 5432))
                .isInstanceOf(AllowedDestinations.DestinationNotAllowedException.class);
    }
}
