package esusdata.indicator.reconciliation;

import static esusdata.indicator.reconciliation.CompatibilityFixtures.CONVENTION_PROBE;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.FIRST_PROBE;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.evidence;
import static esusdata.indicator.reconciliation.CompatibilityFixtures.profile;
import static esusdata.indicator.reconciliation.OfficialReading.DIFFERENT;
import static esusdata.indicator.reconciliation.OfficialReading.SAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.reconciliation.CompatibilityDossierWriter.WrittenDossier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CompatibilityDossierWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INE = "0000000011";
    private static final String CNES = "2750325";

    @TempDir
    Path directory;

    private static CompatibilityDossier dossier() {
        return evidence(profile(DIFFERENT, SAME, true))
                .dimension(ProbeResult.complete(FIRST_PROBE, 3, 0, List.of("team " + INE + " changes in Feb")))
                .convention(ProbeResult.complete(CONVENTION_PROBE, 25, 4, List.of("team " + INE + " differs")))
                .dossier();
    }

    private WrittenDossier write(CompatibilityDossier dossier) throws IOException {
        return CompatibilityDossierWriter.write(
                directory.resolve("out/dossier.json"), directory.resolve("out/dossier.md"), dossier);
    }

    private static JsonNode read(Path json) throws IOException {
        return MAPPER.readTree(Files.readString(json, StandardCharsets.UTF_8));
    }

    @Test
    void theJsonCarriesTheFieldsOfTheSpecUnderTheirNames() throws IOException {
        JsonNode tree = read(write(dossier()).json());

        assertThat(tree.propertyNames())
                .contains(
                        "schema_version",
                        "reference_id",
                        "pack",
                        "rule_version",
                        "reference_manifest_sha256",
                        "local_source_fingerprint",
                        "official_methodology_sources",
                        "normative_deltas",
                        "probe_results",
                        "official_field_comparison",
                        "coverage",
                        "verdict",
                        "reason",
                        "declared_limitations",
                        "declared_conventions");
        assertThat(tree.get("verdict").stringValue()).isEqualTo("EQUIVALENT_FOR_REFERENCE");
        assertThat(tree.get("probe_results").get(0).get("role").stringValue()).isEqualTo("dimension");
        assertThat(tree.get("probe_results").get(2).get("role").stringValue()).isEqualTo("convention");
        assertThat(tree.get("normative_deltas")
                        .get(0)
                        .get("official_reading_text")
                        .stringValue())
                .isEqualTo("the official text");
    }

    @Test
    void theJsonPassesTheBindingCheckOfTheReferencePolicy() throws IOException {
        JsonNode tree = read(write(dossier()).json());

        assertThat(tree.get("schema_version").stringValue()).isEqualTo(ReferencePolicy.DOSSIER_SCHEMA_VERSION);
        assertThat(tree.get("rule_version").stringValue()).isEqualTo(CompatibilityFixtures.RULE_VERSION);
        assertThat(tree.get("reference_manifest_sha256").stringValue()).isEqualTo(CompatibilityFixtures.MANIFEST_SHA);
        assertThat(tree.get("local_source_fingerprint").stringValue()).matches("sha256:[0-9a-f]{64}");
        assertThat(tree.get("official_methodology_sources").isArray()).isTrue();
        assertThat(tree.get("official_methodology_sources")).isNotEmpty();
        assertThat(tree.get("normative_deltas").isArray()).isTrue();
        assertThat(tree.get("probe_results").isArray()).isTrue();
        assertThat(tree.get("official_field_comparison").isObject()).isTrue();
        assertThat(tree.get("coverage").isObject()).isTrue();
        assertThat(tree.get("reason").isString()).isTrue();
        assertThat(ReferenceCompatibility.valueOf(tree.get("verdict").stringValue())
                        .isDecided())
                .isTrue();
    }

    @Test
    void theJsonBytesAreTheSameAcrossTwoWritesAndUseUnixLineEnds() throws IOException {
        CompatibilityDossier dossier = dossier();

        WrittenDossier first = write(dossier);
        byte[] firstBytes = Files.readAllBytes(first.json());
        WrittenDossier second = CompatibilityDossierWriter.write(
                directory.resolve("again.json"), directory.resolve("again.md"), dossier);

        assertThat(Files.readAllBytes(second.json())).isEqualTo(firstBytes);
        assertThat(second.jsonSha256()).isEqualTo(first.jsonSha256());
        assertThat(new String(firstBytes, StandardCharsets.UTF_8))
                .doesNotContain("\r")
                .endsWith("}\n");
    }

    @Test
    void theKeysOfEveryObjectAreSorted() throws IOException {
        String json = Files.readString(write(dossier()).json(), StandardCharsets.UTF_8);

        assertThat(json.indexOf("\"coverage\"")).isLessThan(json.indexOf("\"declared_conventions\""));
        assertThat(json.indexOf("\"declared_limitations\"")).isLessThan(json.indexOf("\"verdict\""));
        assertThat(json.indexOf("\"pack\"")).isLessThan(json.indexOf("\"reference_id\""));
    }

    @Test
    void dossierContainsNoIneOrPersonIdentifiers() throws IOException {
        WrittenDossier written = write(dossier());

        String json = Files.readString(written.json(), StandardCharsets.UTF_8);
        String markdown = Files.readString(written.markdown(), StandardCharsets.UTF_8);

        assertThat(json).doesNotContain(INE).doesNotContain("local_detail");
        assertThat(markdown).doesNotContain(INE);
        assertThat(json).doesNotContainPattern("(?<!\\d)\\d{10}(?!\\d)");
    }

    @Test
    void anIneInAFreeTextFieldIsRefusedAndNothingIsWritten() {
        ProbeResult leaking = ProbeResult.none(FIRST_PROBE, "no data for team " + INE);
        CompatibilityDossier dossier =
                evidence(profile(DIFFERENT, SAME, false)).dimension(leaking).dossier();

        assertThatThrownBy(() -> write(dossier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probe_results")
                .hasMessageNotContaining(INE);
        assertThat(directory.resolve("out")).doesNotExist();
    }

    @Test
    void aCnesAUuidAndAHexDigestInAnyValueAreRefused() {
        for (String leak :
                List.of("clinic " + CNES, "record 123e4567-e89b-12d3-a456-426614174000", "key " + "c".repeat(40))) {
            CompatibilityDossier dossier = evidence(profile(DIFFERENT, SAME, false))
                    .dimension(ProbeResult.none(FIRST_PROBE, leak))
                    .dossier();

            assertThatThrownBy(() -> write(dossier)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void theSeiNumberOfAPublicDocumentIsNotTakenForAnIneButADigitRunNextToItStillIs() throws IOException {
        CompatibilityDossier cited = evidence(profile(DIFFERENT, SAME, false))
                .dimension(ProbeResult.none(FIRST_PROBE, "read as the NT 8/2026 (SEI 0055690090) says"))
                .dossier();
        CompatibilityDossier leaking = evidence(profile(DIFFERENT, SAME, false))
                .dimension(ProbeResult.none(FIRST_PROBE, "SEI 0055690090 and team " + INE))
                .dossier();

        assertThat(read(write(cited).json()).toString()).contains("SEI 0055690090");
        assertThatThrownBy(() -> write(leaking)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theReferenceIdWithItsMunicipalityCodeIsNotTakenForACnes() throws IOException {
        JsonNode tree = read(write(dossier()).json());

        assertThat(tree.get("reference_id").stringValue()).isEqualTo(CompatibilityFixtures.REFERENCE_ID);
    }

    @Test
    void markdownMasksCountsBelowTen() throws IOException {
        String markdown = Files.readString(write(dossier()).markdown(), StandardCharsets.UTF_8);

        assertThat(markdown).contains("<10");
        assertThat(markdown).contains("EQUIVALENT_FOR_REFERENCE");
        assertThat(markdown).contains("25");
        assertThat(markdown).doesNotContain("| 3 |").doesNotContain("| 4 |");
        assertThat(markdown).doesNotContain("\r");
    }

    @Test
    void theLocalDetailIsWrittenOnlyWhenAskedAndNeverUnderDocs() throws IOException {
        CompatibilityDossier dossier = dossier();
        Path detail = directory.resolve("target/portao-d/compatibilidade/detail.txt");

        WrittenDossier asked = CompatibilityDossierWriter.write(
                directory.resolve("a.json"), directory.resolve("a.md"), dossier, Optional.of(detail));
        WrittenDossier notAsked = write(dossier);

        assertThat(asked.localDetail()).contains(detail);
        assertThat(Files.readString(detail, StandardCharsets.UTF_8))
                .contains(INE)
                .contains(FIRST_PROBE);
        assertThat(notAsked.localDetail()).isEmpty();
        assertThatThrownBy(() -> CompatibilityDossierWriter.write(
                        directory.resolve("b.json"),
                        directory.resolve("b.md"),
                        dossier,
                        Optional.of(directory.resolve("docs/indicadores/detail.txt"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(directory.resolve("docs")).doesNotExist();
    }
}
