package esusdata.run.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ManifestChecksumsTest {

    @Test
    void paramsKeepCodeListsInBindOrderAndDatesAsOneIsoString() {
        SortedMap<String, List<String>> params = ManifestChecksums.params(
                Map.of("procedure_codes", List.of("0301010080", "0202010503")),
                Map.of("birth_date_from", LocalDate.of(2024, 1, 1)));

        assertThat(params)
                .containsExactly(
                        Map.entry("birth_date_from", List.of("2024-01-01")),
                        Map.entry("procedure_codes", List.of("0301010080", "0202010503")));
    }

    @Test
    void aBindDeclaredBothWaysIsAContractError() {
        assertThatThrownBy(() ->
                        ManifestChecksums.params(Map.of("x", List.of("1")), Map.of("x", LocalDate.of(2024, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("x");
    }

    @Test
    void paramsChecksumHashesTheCanonicalJson() {
        SortedMap<String, List<String>> params = new TreeMap<>();
        params.put("procedure_codes", List.of("0301010080", "0202010503"));
        params.put("birth_date_from", List.of("2024-01-01"));

        assertThat(ManifestChecksums.canonicalJson(params))
                .isEqualTo(
                        "{\"birth_date_from\":[\"2024-01-01\"],\"procedure_codes\":[\"0301010080\",\"0202010503\"]}");
        // printf '%s' '<json above>' | sha256sum
        assertThat(ManifestChecksums.paramsChecksum(params))
                .isEqualTo("sha256:3446dd11f946ab68ef4eb34ef90ca94df8366126e12e581c976c898ff87b69c9");
    }

    @Test
    void emptyParamsHashTheEmptyObject() {
        assertThat(ManifestChecksums.canonicalJson(new TreeMap<>())).isEqualTo("{}");
        assertThat(ManifestChecksums.paramsChecksum(new TreeMap<>()))
                .isEqualTo("sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a");
    }

    @Test
    void quotesBackslashesAndControlCharactersAreEscapedAsJson() {
        SortedMap<String, List<String>> params = new TreeMap<>();
        params.put("a\"b\\c", List.of("x\u0001"));

        assertThat(ManifestChecksums.canonicalJson(params)).isEqualTo("{\"a\\\"b\\\\c\":[\"x\\u0001\"]}");
        assertThat(ManifestChecksums.paramsChecksum(params))
                .isEqualTo("sha256:2e978bdcad9ab6ae115352a46f1e625f1193c4d9bbb61413198aa6510bec6566");
    }

    @Test
    void compositeQueryChecksumDoesNotDependOnPartOrder() {
        ManifestPart citizen = part(0, "citizen", "sha256:cc", "sha256:dd");
        ManifestPart care = part(1, "care_encounter", "sha256:aa", "sha256:bb");

        // printf '%s\n%s' 'care_encounter@0.1.0:sha256:aa:sha256:bb' 'citizen@0.1.0:sha256:cc:sha256:dd' | sha256sum
        String expected = "sha256:5d619de8a86fa3dfa0eecaaaa5c0a9cd416e7a5ccd1e8ccd51d594ca52e72442";
        assertThat(ManifestChecksums.compositeQueryChecksum(List.of(citizen, care)))
                .isEqualTo(expected);
        assertThat(ManifestChecksums.compositeQueryChecksum(List.of(care, citizen)))
                .isEqualTo(expected);
    }

    private static ManifestPart part(int index, String capability, String queryChecksum, String paramsChecksum) {
        return new ManifestPart(
                index,
                capability,
                "0.1.0",
                queryChecksum,
                "person",
                "2026-01-01",
                "2026-02-01",
                new TreeMap<>(),
                paramsChecksum,
                0);
    }
}
