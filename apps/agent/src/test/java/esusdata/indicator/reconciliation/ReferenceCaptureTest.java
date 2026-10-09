package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.ReferenceCapture.Action;
import esusdata.indicator.reconciliation.ReferenceCapture.FileReport;
import esusdata.indicator.reconciliation.ReferenceCapture.PackReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The capture of the official exports of a directory: CVAT reports skipped and anything else
 * unknown refused before a byte is written, eight references per team export, a re-run that
 * touches nothing, a changed export that becomes the next revision, and a report that carries no
 * INE. Synthetic exports only.
 */
class ReferenceCaptureTest {

    private static final String IBGE = SiapsTeamExportFixtures.MUNICIPALITY_IBGE;
    private static final String UF = "zz";
    private static final String PREFIX = "zz-9999990-2026q1-";
    private static final String LATER = "09 de outubro de 2026 - 08:15h";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);
    private static final List<String> EXPORT_INES = List.of(
            SiapsTeamExportFixtures.ESF_1,
            SiapsTeamExportFixtures.ESF_2,
            SiapsTeamExportFixtures.EAP_1,
            SiapsTeamExportFixtures.ESB_1,
            SiapsTeamExportFixtures.EMULTI_1);

    @TempDir
    Path exports;

    @TempDir
    Path artifacts;

    @TempDir
    Path workspace;

    private Path manifests() {
        return workspace.resolve("manifests");
    }

    private List<FileReport> capture() throws IOException {
        return new ReferenceCapture(artifacts, manifests(), UF, CLOCK).capture(exports, IBGE);
    }

    private static byte[] cvat() {
        return SiapsTeamExportFixtures.standard()
                .header(SiapsTeamExportFixtures.HEADER_LINE.replace("Indicador", "Dimensão"))
                .bytes();
    }

    /**
     * The standard export, generated later, with the C1 result of the first eSF team moved within its
     * band: C1 changes, its concept, note and the Nota Final do not.
     */
    private static byte[] laterWithOnlyAC1ResultChanged() {
        SiapsTeamExportFixtures.Export export =
                SiapsTeamExportFixtures.standard().generatedAt(LATER);
        export.row(SiapsTeamExportFixtures.ESF_1, SiapsTeamExportFixtures.INDICATOR_NAMES.getFirst())[
                SiapsTeamExportFixtures.RESULT_COLUMN] = "12.75";
        return export.bytes();
    }

    /** The standard export with the first concept of the first eSF team (C1) changed, generated later. */
    private static byte[] laterWithC1Changed() {
        return SiapsTeamExportFixtures.export()
                .generatedAt(LATER)
                .team(
                        SiapsTeamExportFixtures.ESF_1,
                        SiapsParser.ESF,
                        Classification.BOM,
                        Classification.OTIMO,
                        Classification.SUFICIENTE,
                        Classification.BOM,
                        Classification.OTIMO,
                        Classification.BOM,
                        Classification.SUFICIENTE,
                        Classification.REGULAR)
                .team(
                        SiapsTeamExportFixtures.ESF_2,
                        SiapsParser.ESF,
                        Classification.OTIMO,
                        SiapsTeamExportFixtures.all(Classification.OTIMO))
                .team(
                        SiapsTeamExportFixtures.EAP_1,
                        SiapsParser.EAP,
                        Classification.BOM,
                        SiapsTeamExportFixtures.all(Classification.BOM))
                .outOfScopeTeam(SiapsTeamExportFixtures.ESB_1, "eSB")
                .outOfScopeTeam(SiapsTeamExportFixtures.EMULTI_1, "eMulti")
                .bytes();
    }

    private void write(String name, byte[] bytes) throws IOException {
        Files.write(exports.resolve(name), bytes);
    }

    /** Every file under {@code directory} with its hash and its modification time: what "untouched" compares. */
    private static Map<String, String> stateOf(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return Map.of();
        }
        Map<String, String> state = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                state.put(
                        directory.relativize(path).toString(),
                        SummaryWriter.sha256(path) + "@" + Files.getLastModifiedTime(path));
            }
        }
        return state;
    }

    private SiapsReferenceManifest manifest(String suffix) throws IOException {
        return SiapsReferenceManifest.fromJson(Files.readString(manifests().resolve(PREFIX + suffix + ".json")));
    }

    private static List<String> idsOf(String c1Revision, String... absent) {
        List<String> ids = new ArrayList<>();
        for (GatePack pack : GatePack.all()) {
            String code = pack.code().toLowerCase(Locale.ROOT);
            if (!List.of(absent).contains(code)) {
                ids.add(PREFIX + code + "-team-" + ("c1".equals(code) ? c1Revision : "r1"));
            }
        }
        return ids;
    }

    private static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    private static String textOf(List<FileReport> reports) {
        return reports.stream().flatMap(report -> report.lines().stream()).collect(Collectors.joining("\n"));
    }

    private static List<Action> actionsOf(FileReport report) {
        return report.packs().stream().map(PackReport::action).toList();
    }

    // ---- what a team export becomes

    @Test
    void aTeamExportBecomesEightReferencesAndACvatReportBesideItIsSkippedExplicitly() throws IOException {
        write("a-team.csv", SiapsTeamExportFixtures.standard().bytes());
        write("b-cvat.csv", cvat());

        List<FileReport> reports = capture();

        assertThat(reports).hasSize(2);
        assertThat(reports.get(1).cvat()).isTrue();
        assertThat(reports.get(1).lines())
                .singleElement()
                .asString()
                .contains("skipped")
                .contains("CVAT");
        FileReport team = reports.getFirst();
        assertThat(team.quadrimestre()).isEqualTo(SiapsTeamExportFixtures.QUADRIMESTRE);
        assertThat(team.status()).isEqualTo(OfficialStatus.PRELIMINARY);
        assertThat(team.packs()).extracting(PackReport::pack).containsExactlyElementsOf(GatePack.allWithNotaFinal());
        assertThat(team.packs()).extracting(PackReport::action).containsOnly(Action.CAPTURED);
        assertThat(team.packs())
                .extracting(PackReport::referenceId)
                .containsExactly(
                        PREFIX + "c1-team-r1",
                        PREFIX + "c2-team-r1",
                        PREFIX + "c3-team-r1",
                        PREFIX + "c4-team-r1",
                        PREFIX + "c5-team-r1",
                        PREFIX + "c6-team-r1",
                        PREFIX + "c7-team-r1",
                        PREFIX + "ciii-team-r1");
        assertThat(namesIn(manifests())).hasSize(8).allMatch(name -> name.endsWith("-r1.json"));
    }

    @Test
    void everyManifestIsTheCardOfAnArtifactTheStoreLoadsBackWithItsHashesVerified() throws IOException {
        byte[] raw = SiapsTeamExportFixtures.standard().bytes();
        write("team.csv", raw);

        capture();

        ReferenceArtifactStore store = new ReferenceArtifactStore(artifacts);
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            Path file = manifests().resolve(PREFIX + pack.code().toLowerCase(Locale.ROOT) + "-team-r1.json");
            SiapsReferenceManifest manifest =
                    SiapsReferenceManifest.fromJson(Files.readString(file, StandardCharsets.UTF_8));

            assertThat(manifest.rawSha256()).isEqualTo(SummaryWriter.sha256(raw));
            assertThat(manifest.sourceDescription()).isEqualTo(ReferenceCapture.SOURCE_DESCRIPTION);
            assertThat(manifest.sourceFilename()).isEqualTo("team.csv");
            assertThat(manifest.officialStatus()).isEqualTo(OfficialStatus.PRELIMINARY);
            assertThat(store.load(manifest).indicatorCode()).isEqualTo(pack.siapsCode());
        }
    }

    @Test
    void theReportCarriesCountsReferenceIdsAndHashesButNeverATeam() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());

        String text = textOf(capture());

        assertThat(text).contains("c1-team-r1").contains("normalized=");
        assertThat(text).doesNotContain(EXPORT_INES.toArray(new String[0])).doesNotContain("team.csv");
        assertThat(text).doesNotContainPattern("\\d{10}");
    }

    // ---- registered references are never rewritten

    @Test
    void capturingAgainTouchesNeitherTheManifestsNorTheArtifacts() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());
        capture();
        Map<String, String> manifestsBefore = stateOf(manifests());
        Map<String, String> artifactsBefore = stateOf(artifacts);

        List<FileReport> again = capture();

        assertThat(actionsOf(again.getFirst())).containsOnly(Action.UNCHANGED);
        assertThat(stateOf(manifests())).isEqualTo(manifestsBefore).hasSize(8);
        assertThat(stateOf(artifacts)).isEqualTo(artifactsBefore);
    }

    @Test
    void aLostArtifactIsPutBackWithoutTouchingTheRegisteredManifest() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());
        capture();
        Map<String, String> manifestsBefore = stateOf(manifests());
        try (Stream<Path> paths = Files.walk(artifacts)) {
            for (Path path : paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(artifacts))
                    .toList()) {
                Files.delete(path);
            }
        }

        capture();

        assertThat(stateOf(manifests())).isEqualTo(manifestsBefore);
        assertThat(stateOf(artifacts)).isNotEmpty();
    }

    @Test
    void aLostArtifactIsNotPutBackFromAnotherDownloadButFromTheFileItsManifestPins() throws IOException {
        byte[] original = SiapsTeamExportFixtures.standard().bytes();
        write("team.csv", original);
        capture();
        Map<String, String> manifestsBefore = stateOf(manifests());
        deleteEverythingIn(artifacts);
        // the same contents downloaded again: only the generation line, and so the raw hash, differ
        write("team.csv", SiapsTeamExportFixtures.standard().generatedAt(LATER).bytes());

        List<FileReport> again = capture();

        assertThat(actionsOf(again.getFirst())).containsOnly(Action.ARTIFACT_MISSING);
        assertThat(textOf(again)).contains("restore the original file");
        assertThat(stateOf(artifacts)).isEmpty();
        assertThat(stateOf(manifests())).isEqualTo(manifestsBefore);

        write("team.csv", original);
        List<FileReport> restored = capture();

        assertThat(actionsOf(restored.getFirst())).containsOnly(Action.UNCHANGED);
        assertThat(stateOf(manifests())).isEqualTo(manifestsBefore);
        ReferenceArtifactStore store = new ReferenceArtifactStore(artifacts);
        for (String name : namesIn(manifests())) {
            SiapsReferenceManifest manifest =
                    SiapsReferenceManifest.fromJson(Files.readString(manifests().resolve(name)));
            assertThat(store.load(manifest)).as(name).isNotNull();
        }
    }

    private static void deleteEverythingIn(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void aChangedExportIsTheNextRevisionOfOnlyTheReferencesThatChangedAndTheOldOnesStay() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());
        capture();
        Map<String, String> firstRevision = stateOf(manifests());
        write("team.csv", laterWithC1Changed());

        List<FileReport> reports = capture();

        assertThat(reports.getFirst().packs())
                .extracting(PackReport::pack, PackReport::action, PackReport::referenceId)
                .containsExactly(
                        tuple(GatePack.all().get(0), Action.DRIFT, PREFIX + "c1-team-r2"),
                        tuple(GatePack.all().get(1), Action.UNCHANGED, PREFIX + "c2-team-r1"),
                        tuple(GatePack.all().get(2), Action.UNCHANGED, PREFIX + "c3-team-r1"),
                        tuple(GatePack.all().get(3), Action.UNCHANGED, PREFIX + "c4-team-r1"),
                        tuple(GatePack.all().get(4), Action.UNCHANGED, PREFIX + "c5-team-r1"),
                        tuple(GatePack.all().get(5), Action.UNCHANGED, PREFIX + "c6-team-r1"),
                        tuple(GatePack.all().get(6), Action.UNCHANGED, PREFIX + "c7-team-r1"),
                        tuple(GatePack.NOTA_FINAL, Action.DRIFT, PREFIX + "ciii-team-r2"));
        Map<String, String> secondRevision = stateOf(manifests());
        assertThat(secondRevision).hasSize(10).containsAllEntriesOf(firstRevision);
    }

    @Test
    void aPackThatChangesUnderAnUnchangedNotaFinalMakesANewRevisionOfTheNotaFinalWithItsSiblings() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());
        capture();
        write("later.csv", laterWithOnlyAC1ResultChanged());

        List<FileReport> reports = capture();

        assertThat(reports)
                .filteredOn(report -> report.position() == 0)
                .singleElement()
                .satisfies(later -> assertThat(later.packs())
                        .extracting(PackReport::pack, PackReport::action, PackReport::referenceId)
                        .contains(
                                tuple(GatePack.all().getFirst(), Action.DRIFT, PREFIX + "c1-team-r2"),
                                tuple(GatePack.NOTA_FINAL, Action.DRIFT, PREFIX + "ciii-team-r2")));
        SiapsReferenceManifest first = manifest("ciii-team-r1");
        SiapsReferenceManifest second = manifest("ciii-team-r2");
        // the same final classes beside another C1: another revision, filed apart from the first
        assertThat(second.normalizedSha256()).isEqualTo(first.normalizedSha256());
        assertThat(second.siblingReferenceIds()).containsExactlyElementsOf(idsOf("r2"));
        assertThat(first.siblingReferenceIds()).containsExactlyElementsOf(idsOf("r1"));
        ReferenceArtifactStore store = new ReferenceArtifactStore(artifacts);
        assertThat(store.directoryOf(second)).isNotEqualTo(store.directoryOf(first));
        assertThat(store.load(first)).isEqualTo(store.load(second));

        assertThat(capture())
                .flatExtracting(FileReport::packs)
                .extracting(PackReport::action)
                .containsOnly(Action.UNCHANGED);
    }

    @Test
    void aJsonFileNotNamedLikeAManifestRefusesTheCaptureBeforeAnythingIsWritten() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());
        capture();
        Files.move(manifests().resolve(PREFIX + "c3-team-r1.json"), manifests().resolve("backup.json"));
        Map<String, String> before = stateOf(manifests());
        Map<String, String> stored = stateOf(artifacts);

        assertThatThrownBy(this::capture)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not named like a team reference manifest");

        assertThat(stateOf(manifests())).isEqualTo(before);
        assertThat(stateOf(artifacts)).isEqualTo(stored);
    }

    @Test
    void theNotaFinalNamesTheRevisionOfEachPackItsDownloadHeldWhetherItChangedOrNot() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());
        capture();
        write("team.csv", laterWithC1Changed());

        capture();

        assertThat(manifest("ciii-team-r1").siblingReferenceIds()).containsExactlyElementsOf(idsOf("r1"));
        // the later file changed C1 and the Nota Final: C2 to C7 are the revisions the first file was
        // captured as, which name the first file's raw hash and not this one's
        assertThat(manifest("ciii-team-r2").siblingReferenceIds()).containsExactlyElementsOf(idsOf("r2"));
        assertThat(manifest("c2-team-r1").rawSha256())
                .isNotEqualTo(manifest("ciii-team-r2").rawSha256());
    }

    @Test
    void twoExportsOfOneQuadrimestreAreNumberedByWhenTheSiapsGeneratedThemNotByTheirNames() throws IOException {
        write("a-later.csv", laterWithC1Changed());
        write("b-earlier.csv", SiapsTeamExportFixtures.standard().bytes());

        List<FileReport> reports = capture();

        assertThat(actionsOf(reports.get(1)))
                .as("the earlier export, read second")
                .containsOnly(Action.CAPTURED);
        assertThat(reports.getFirst().packs())
                .filteredOn(pack -> pack.action() == Action.DRIFT)
                .extracting(PackReport::referenceId)
                .containsExactly(PREFIX + "c1-team-r2", PREFIX + "ciii-team-r2");
        SiapsReferenceManifest r1 = SiapsReferenceManifest.fromJson(
                Files.readString(manifests().resolve(PREFIX + "c1-team-r1.json"), StandardCharsets.UTF_8));
        SiapsReferenceManifest r2 = SiapsReferenceManifest.fromJson(
                Files.readString(manifests().resolve(PREFIX + "c1-team-r2.json"), StandardCharsets.UTF_8));
        assertThat(r1.officialGeneratedAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 17, 42));
        assertThat(r2.officialGeneratedAt()).isEqualTo(LocalDateTime.of(2026, 10, 9, 8, 15));
    }

    // ---- what is refused, and when

    @Test
    void aFileThatIsNeitherATeamExportNorACvatReportRefusesTheWholeCaptureBeforeAnythingIsWritten() throws IOException {
        write("notes.csv", "a;b\r\n1;2\r\n".getBytes(StandardCharsets.UTF_8));
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());

        assertThatThrownBy(this::capture)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file 0 of the export directory")
                .hasMessageContaining("neither a CVAT report nor a team export");

        assertThat(artifacts).isEmptyDirectory();
        assertThat(manifests()).doesNotExist();
    }

    @Test
    void anExportOfAnotherMunicipalityIsRefusedAndNothingIsWritten() throws IOException {
        write(
                "team.csv",
                SiapsTeamExportFixtures.standard().municipality("999998").bytes());

        assertThatThrownBy(this::capture)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("municipality");

        assertThat(artifacts).isEmptyDirectory();
        assertThat(manifests()).doesNotExist();
    }

    @Test
    void aDirectoryOfCvatReportsOnlyHasNothingToCapture() throws IOException {
        write("cvat.csv", cvat());

        assertThatThrownBy(this::capture)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no official team export");

        assertThat(manifests()).doesNotExist();
    }

    @Test
    void aPackSomeTeamHasNoRowForIsIncompleteAndNotCapturedWhileTheOthersAre() throws IOException {
        write(
                "team.csv",
                SiapsTeamExportFixtures.standard()
                        .dropRow(SiapsTeamExportFixtures.ESF_1, SiapsTeamExportFixtures.INDICATOR_NAMES.getFirst())
                        .bytes());

        List<FileReport> reports = capture();

        PackReport c1 = reports.getFirst().packs().getFirst();
        assertThat(c1.action()).isEqualTo(Action.INCOMPLETE);
        assertThat(c1.referenceId()).isNull();
        assertThat(c1.detail()).contains("1 of its 3 teams have no row");
        assertThat(reports.getFirst().packs().stream().skip(1))
                .extracting(PackReport::action)
                .containsOnly(Action.CAPTURED);
        assertThat(namesIn(manifests())).hasSize(7);
        assertThat(manifests().resolve(PREFIX + "c1-team-r1.json")).doesNotExist();
        assertThat(manifest("ciii-team-r1").siblingReferenceIds()).containsExactlyElementsOf(idsOf("r1", "c1"));
    }

    @Test
    void aManifestNotNamedByItsReferenceIdIsRefusedBeforeAnythingIsCaptured() throws IOException {
        write("team.csv", SiapsTeamExportFixtures.standard().bytes());
        capture();
        Files.move(manifests().resolve(PREFIX + "c2-team-r1.json"), manifests().resolve(PREFIX + "c3-team-r9.json"));

        assertThatThrownBy(this::capture)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not named by its reference id");
    }

    // ---- the CVAT detection and the state of a municipality

    @Test
    void aCvatReportIsRecognizedByItsDimensionColumnAndATeamExportIsNot() {
        assertThat(ReferenceCapture.isCvatReport(cvat())).isTrue();
        assertThat(ReferenceCapture.isCvatReport(
                        SiapsTeamExportFixtures.standard().bytes()))
                .isFalse();
        assertThat(ReferenceCapture.isCvatReport("nothing;to;see\r\n".getBytes(StandardCharsets.UTF_8)))
                .isFalse();
    }

    @Test
    void theStateOfAMunicipalityIsTheFirstTwoDigitsOfItsIbgeCode() {
        assertThat(SiapsFormats.uf("3541307")).isEqualTo("SP");
        assertThat(SiapsFormats.uf("354130")).isEqualTo("SP");
        assertThat(SiapsFormats.uf("1100015")).isEqualTo("RO");
        assertThat(SiapsFormats.uf("5300108")).isEqualTo("DF");
        assertThatThrownBy(() -> SiapsFormats.uf("9999990"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state");
    }
}
