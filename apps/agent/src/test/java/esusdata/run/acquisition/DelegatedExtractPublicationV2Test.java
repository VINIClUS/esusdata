package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.run.extract.DelegatedExtractPublication;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.ManifestChecksums;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DelegatedExtractPublication}'s canonical v2 side (ADR 0030) through its public API: what
 * it refuses before any child runs, the counts it requires of the child, and that the v1 and v2
 * publications never cross.
 */
class DelegatedExtractPublicationV2Test {

    private static final String ZONE = "America/Sao_Paulo";

    @TempDir
    Path extractsDir;

    private static AcquisitionPart part(
            String capability, SortedMap<String, List<String>> codes, SortedMap<String, LocalDate> dates) {
        return new AcquisitionPart(
                capability,
                "0.1.0",
                "sha256:" + "c".repeat(64),
                "person",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                codes,
                dates);
    }

    private static AcquisitionPart citizen() {
        SortedMap<String, LocalDate> dates = new TreeMap<>();
        dates.put("birth_date_from", LocalDate.of(2024, 1, 1));
        return part("citizen", new TreeMap<>(), dates);
    }

    private static AcquisitionCommand command(List<AcquisitionPart> parts) {
        return new AcquisitionCommand(
                new PecConnectionProperties(
                        "src-1", "127.0.0.1", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD", "3541307"),
                new PecSourceIdentity("src-1", "5.4.37", "PEC_DW", "PRONTUARIO"),
                ReadBudget.initialEngineeringProposal(),
                "job-1-g1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 1),
                ZONE,
                parts);
    }

    private DelegatedExtractPublication open(List<AcquisitionPart> parts) throws IOException {
        return new DelegatedExtractPublication(extractsDir, "job-1-g1", command(parts));
    }

    private record Written(String checksum, long bytes) {}

    /** What the child would leave at the reserved path, and what it would report about it. */
    private static Written write(Path tempFile, String... lines) throws IOException, NoSuchAlgorithmException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
            for (String line : lines) {
                gzip.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        Files.write(tempFile, raw.toByteArray());
        String checksum =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()));
        return new Written(checksum, raw.size());
    }

    @Test
    void theManifestTakesEverythingButTheCountsFromTheCommand() throws Exception {
        SortedMap<String, List<String>> codes = new TreeMap<>();
        codes.put("procedure_codes", List.of("0301010080"));
        try (DelegatedExtractPublication publication =
                open(List.of(citizen(), part("procedure_performed", codes, new TreeMap<>())))) {
            Written written = write(publication.tempFile(), "{\"part\":0}", "{\"part\":1}", "{\"part\":1}");

            ExtractionManifest manifest = publication.publishV2(
                    3, 0, written.checksum(), written.bytes(), Instant.now(), ZONE, List.of(1L, 2L));

            assertThat(manifest.rowCount()).isEqualTo(3);
            assertThat(manifest.parts().get(0).params())
                    .containsExactly(Map.entry("birth_date_from", List.of("2024-01-01")));
            assertThat(manifest.parts().get(1).params())
                    .containsExactly(Map.entry("procedure_codes", List.of("0301010080")));
            assertThat(manifest.parts().get(1).paramsChecksum())
                    .isEqualTo(ManifestChecksums.paramsChecksum(
                            manifest.parts().get(1).params()));
            assertThat(manifest.queryChecksum())
                    .isEqualTo(publication.canonicalV2QueryChecksum())
                    .isEqualTo(ManifestChecksums.compositeQueryChecksum(manifest.parts()));
            assertThat(extractsDir.resolve("job-1-g1.jsonl.gz")).exists();
        }
    }

    @Test
    void aBindDeclaredBothAsCodesAndAsADateFailsBeforeAnyChildRuns() {
        SortedMap<String, List<String>> codes = new TreeMap<>();
        codes.put("birth_date_from", List.of("x"));
        SortedMap<String, LocalDate> dates = new TreeMap<>();
        dates.put("birth_date_from", LocalDate.of(2024, 1, 1));

        assertThatThrownBy(() -> open(List.of(part("citizen", codes, dates))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("birth_date_from");
    }

    @Test
    void anEmptyPartWindowFailsBeforeAnyChildRuns() {
        AcquisitionPart empty = new AcquisitionPart(
                "citizen",
                "0.1.0",
                "sha256:" + "c".repeat(64),
                "person",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 1),
                new TreeMap<>(),
                new TreeMap<>());

        assertThatThrownBy(() -> open(List.of(empty)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("part 0 (citizen) window [2026-03-01, 2026-03-01)");
    }

    @Test
    void theChildMustCountEveryPartWithANonNegativeCount() throws Exception {
        try (DelegatedExtractPublication publication = open(List.of(citizen(), citizen()))) {
            Written written = write(publication.tempFile(), "{\"part\":0}");
            for (List<Long> counts : List.of(List.of(1L), Arrays.asList(null, 1L), List.of(-1L, 2L))) {
                assertThatThrownBy(() -> publication.publishV2(
                                1, 0, written.checksum(), written.bytes(), Instant.now(), ZONE, counts))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("execution plane reported");
            }
            assertThatThrownBy(() ->
                            publication.publishV2(1, 0, written.checksum(), written.bytes(), Instant.now(), ZONE, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("0 part row counts for 2 parts");
            assertThat(extractsDir.resolve("job-1-g1.jsonl.gz")).doesNotExist();
        }
    }

    @Test
    void aStartAfterTheEndIsNeverPublished() throws Exception {
        try (DelegatedExtractPublication publication = open(List.of(citizen()))) {
            Written written = write(publication.tempFile(), "{\"part\":0}");
            Instant tomorrow = Instant.now().plus(Duration.ofDays(1));

            assertThatThrownBy(() -> publication.publishV2(
                            1, 0, written.checksum(), written.bytes(), tomorrow, ZONE, List.of(1L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not ordered");
        }
    }

    /** A v1 publication writes C1's manifest only, and a v2 one only the v2 manifest. */
    @Test
    void theV1AndV2PublicationsNeverCross() throws Exception {
        try (DelegatedExtractPublication v1 = open(List.of())) {
            assertThatThrownBy(v1::canonicalV2QueryChecksum).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> v1.publishV2(0, 0, "0".repeat(64), 0, Instant.now(), ZONE, List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("canonical v1");
        }
        try (DelegatedExtractPublication v2 = open(List.of(citizen()))) {
            assertThatThrownBy(() -> v2.publish(
                            0,
                            0,
                            "0".repeat(64),
                            0,
                            Instant.now(),
                            ZONE,
                            "sha256:" + "d".repeat(64),
                            "0.1.0",
                            "COMPLETE",
                            "SNAPSHOT"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("publishV2");
        }
    }
}
