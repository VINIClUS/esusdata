package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.source.pec.AllowedDestinations.DestinationNotAllowedException;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcquisitionInputsTest {

    private static final String NAME = "pec.example.test";
    private static final String LOCALHOST = "localhost";

    private static final Map<String, String> ENVIRONMENT = Map.of(
            AcquisitionInputs.HOST_KEY,
            NAME,
            "PEC_DB_PORT",
            "15432",
            "PEC_DB_NAME",
            "esus",
            "PEC_DB_USER",
            "reader",
            "PEC_SOURCE_ID",
            "pec-test",
            "PEC_VERSION",
            "5.5.28",
            "PEC_MUNICIPALITY_IBGE",
            "3541307");

    private static Map<String, String> withHost(String host) {
        Map<String, String> environment = new HashMap<>(ENVIRONMENT);
        environment.put(AcquisitionInputs.HOST_KEY, host);
        return environment;
    }

    @Test
    void aNameIsPinnedToTheFirstAddressItResolvesToAndNothingElseChanges() throws UnknownHostException {
        InetAddress first = InetAddress.getByAddress(new byte[] {10, 0, 0, 7});
        InetAddress second = InetAddress.getByAddress(new byte[] {10, 0, 0, 8});

        Map<String, String> pinned = AcquisitionInputs.pinned(ENVIRONMENT, host -> new InetAddress[] {first, second});

        assertThat(pinned).isEqualTo(withHost("10.0.0.7"));
    }

    @Test
    void anIpv6AddressIsPinnedInBracketsSoTheUrlOfThePreflightStaysValid() throws UnknownHostException {
        byte[] loopback = new byte[16];
        loopback[15] = 1;
        InetAddress address = InetAddress.getByAddress(loopback);

        Map<String, String> pinned = AcquisitionInputs.pinned(ENVIRONMENT, host -> new InetAddress[] {address});

        assertThat(pinned).containsEntry(AcquisitionInputs.HOST_KEY, "[0:0:0:0:0:0:0:1]");
        assertThat(ReadOnlyPecPreflight.url(pinned)).isEqualTo("jdbc:postgresql://[0:0:0:0:0:0:0:1]:15432/esus");
    }

    @Test
    void aHostThatResolvesToNoAddressIsRefusedBeforeAnyConnection() {
        assertThatThrownBy(() -> AcquisitionInputs.pinned(ENVIRONMENT, host -> {
                    throw new UnknownHostException(host);
                }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AcquisitionInputs.pinned(ENVIRONMENT, host -> new InetAddress[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLiveAcquisitionOfANameAllowsTheAddressTheNameIsPinnedTo() throws UnknownHostException {
        AcquisitionInputs.Live live =
                AcquisitionInputs.live(withHost(LOCALHOST), Path.of("pec.env"), "observatorio-execplane");

        List<String> addresses = Arrays.stream(InetAddress.getAllByName(LOCALHOST))
                .map(InetAddress::getHostAddress)
                .toList();
        assertThat(live.validatedHost()).isIn(addresses);
        assertThat(live.connection().host()).isNotEqualTo(LOCALHOST);
    }

    @Test
    void aNameThatWasNotPinnedIsNeverHandedToAChildAsAnAddress() {
        AcquisitionInputs inputs = AcquisitionInputs.of(
                new PecConnectionProperties(
                        "pec-test", LOCALHOST, 15_432, "esus", "reader", AcquisitionInputs.PASSWORD_KEY, "3541307"),
                new PecSourceIdentity("pec-test", "5.5.28", AcquisitionInputs.READ_MODEL, AcquisitionInputs.ROLE),
                directory -> {
                    throw new AssertionError("nothing is acquired here");
                });

        assertThatThrownBy(((AcquisitionInputs.Live) inputs)::validatedHost)
                .isInstanceOf(DestinationNotAllowedException.class);
    }
}
