package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Cell;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Row;
import esusdata.indicator.reconciliation.PeriodExecutionPlan.Decision;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.worker.AcquisitionInputs;
import esusdata.run.worker.FixturePec;
import esusdata.run.worker.ReferenceScopedExtracts;
import esusdata.run.worker.SourceIdentity;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The diagnostic run, end to end and offline: synthetic official exports are captured, a fixture
 * PEC stands in for the execution plane, and the matrix is what comes out. What is proved: every
 * captured period and pack is a row in order, a period the PEC lacks months of is a row that names
 * them and reads nothing, the Nota Final never reads the PEC, a second run is served by the cache,
 * a cell that cannot be computed is an ERROR row and not an exception, {@code periods} selects and
 * a period that was never captured is refused, and the written matrix is masked and carries no INE.
 */
class PortaoDDiagnosticRunTest {

    private static final String IBGE = SiapsTeamExportFixtures.MUNICIPALITY_IBGE;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);
    private static final Quadrimestre Q3_2025 = new Quadrimestre(2025, 3);
    private static final Quadrimestre Q1_2026 = new Quadrimestre(2026, 1);
    private static final Set<YearMonth> ALL_MONTHS = months(YearMonth.of(2025, 9), YearMonth.of(2026, 4));
    private static final String LATER = "09 de outubro de 2026 - 08:15h";
    private static final String CIII = "CIII";
    private static final List<String> FIXTURE_INES = List.of(
            SiapsTeamExportFixtures.ESF_1,
            SiapsTeamExportFixtures.ESF_2,
            SiapsTeamExportFixtures.EAP_1,
            SiapsTeamExportFixtures.ESB_1,
            SiapsTeamExportFixtures.EMULTI_1,
            "0000346268");

    /** Ten digits that are not part of a hash: an INE, as the output must never have one. */
    private static final Pattern INE_SHAPE = Pattern.compile("(?<![0-9a-f])\\d{10}(?![0-9a-f])");

    @TempDir
    Path workspace;

    private static Set<YearMonth> months(YearMonth from, YearMonth to) {
        Set<YearMonth> months = new HashSet<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    private Path exports() {
        return workspace.resolve("exports");
    }

    private Path artifacts() {
        return workspace.resolve("artifacts");
    }

    private Path manifests() {
        return workspace.resolve("manifests");
    }

    /** The synthetic official exports of 2025Q3 and 2026Q1, captured: sixteen references. */
    private void captureTwoQuadrimestres() throws IOException {
        Files.createDirectories(exports());
        Files.write(
                exports().resolve("q3-2025.csv"),
                SiapsTeamExportFixtures.export()
                        .competence("Q3/25")
                        .standardTeams()
                        .bytes());
        Files.write(
                exports().resolve("q1-2026.csv"),
                SiapsTeamExportFixtures.standard().bytes());
        new ReferenceCapture(artifacts(), manifests(), "zz", CLOCK).capture(exports(), IBGE);
    }

    private PortaoDDiagnosticRun open(Set<Quadrimestre> requested) throws IOException {
        return PortaoDDiagnosticRun.open(
                manifests(),
                artifacts(),
                requested,
                new ReferenceScopedExtracts(artifacts().resolve("extratos"), CLOCK),
                CLOCK);
    }

    private DiagnosticMatrix runAll(FixturePec pec) throws IOException {
        return open(Set.of()).run(pec.sourceIdentity(), ALL_MONTHS, pec.inputs());
    }

    private static List<Row> rowsOf(DiagnosticMatrix matrix, Quadrimestre period) {
        return matrix.rows().stream().filter(row -> row.period().equals(period)).toList();
    }

    private static List<String> packsInOrder() {
        return GatePack.allWithNotaFinal().stream().map(GatePack::code).toList();
    }

    // ---- the matrix

    @Test
    void everyCapturedPeriodAndPackIsARowInOrderAndNothingIsComputedAsEvidence() throws IOException {
        captureTwoQuadrimestres();
        FixturePec pec = new FixturePec(IBGE);

        DiagnosticMatrix matrix = runAll(pec);

        assertThat(matrix.plan())
                .extracting(PeriodExecutionPlan::quadrimestre, PeriodExecutionPlan::decision)
                .containsExactly(tuple(Q3_2025, Decision.RUN), tuple(Q1_2026, Decision.RUN));
        List<String> expected = new ArrayList<>();
        for (Quadrimestre period : List.of(Q3_2025, Q1_2026)) {
            packsInOrder().forEach(pack -> expected.add(SiapsFormats.quadrimestre(period) + " " + pack));
        }
        assertThat(matrix.rows())
                .extracting(row -> SiapsFormats.quadrimestre(row.period()) + " " + row.pack())
                .containsExactlyElementsOf(expected);
        assertThat(matrix.errors()).isEmpty();
        assertThat(matrix.purpose()).isEqualTo(ReferencePurpose.DIAGNOSTIC);
    }

    @Test
    void everyCellNamesTheLocalSourceItWasComputedFrom() throws IOException {
        captureTwoQuadrimestres();

        DiagnosticMatrix matrix = runAll(new FixturePec(IBGE));

        assertThat(matrix.rows())
                .allSatisfy(row -> assertThat(row.fingerprint()).matches("sha256:[0-9a-f]{64}"));
        assertThat(matrix.rows()).extracting(Row::referenceId).allMatch(id -> id.startsWith("zz-9999990-"));
    }

    @Test
    void aPeriodTheLocalSourceLacksMonthsOfIsARowThatNamesThemAndReadsNothing() throws IOException {
        captureTwoQuadrimestres();
        FixturePec pec = new FixturePec(IBGE);
        Set<YearMonth> coverage = new HashSet<>(ALL_MONTHS);
        coverage.remove(YearMonth.of(2026, 3));

        DiagnosticMatrix matrix = open(Set.of()).run(pec.sourceIdentity(), coverage, pec.inputs());

        assertThat(matrix.plan())
                .extracting(PeriodExecutionPlan::decision)
                .containsExactly(Decision.RUN, Decision.MISSING_LOCAL_MONTHS);
        assertThat(rowsOf(matrix, Q1_2026))
                .hasSize(GatePack.allWithNotaFinal().size())
                .allSatisfy(row -> {
                    assertThat(row.status()).isEqualTo(Cell.MISSING_LOCAL_MONTHS);
                    assertThat(row.reason()).contains("2026-03");
                    assertThat(row.figures()).isEmpty();
                });
        assertThat(rowsOf(matrix, Q3_2025)).extracting(Row::status).doesNotContain(Cell.MISSING_LOCAL_MONTHS);
        assertThat(pec.requests())
                .isNotEmpty()
                .extracting(AcquisitionCommand::extractionId)
                .allMatch(id -> id.contains("-2025-"));
    }

    @Test
    void aSecondRunIsServedByTheCacheAndTheNotaFinalNeverReadsThePec() throws IOException {
        captureTwoQuadrimestres();
        FixturePec pec = new FixturePec(IBGE);
        DiagnosticMatrix first = runAll(pec);
        int requested = pec.requests().size();

        DiagnosticMatrix second = open(Set.of()).run(pec.sourceIdentity(), ALL_MONTHS, AcquisitionInputs.none());

        assertThat(requested).isPositive();
        assertThat(pec.requests()).hasSize(requested);
        assertThat(second.rows()).isEqualTo(first.rows());
        assertThat(second.errors()).isEmpty();
    }

    @Test
    void withoutACacheOrAPecEveryCellSaysWhyAndNothingIsRead() throws IOException {
        captureTwoQuadrimestres();
        FixturePec pec = new FixturePec(IBGE);

        DiagnosticMatrix matrix = open(Set.of()).run(pec.sourceIdentity(), ALL_MONTHS, AcquisitionInputs.none());

        assertThat(pec.requests()).isEmpty();
        assertThat(matrix.rows()).hasSize(2 * GatePack.allWithNotaFinal().size());
        assertThat(matrix.errors()).hasSameSizeAs(matrix.rows()).allSatisfy(row -> {
            assertThat(row.reason()).isNotBlank();
            assertThat(row.figures()).isEmpty();
        });
    }

    @Test
    void aCellThatCannotBeComputedIsAnErrorRowAndTheRestOfTheMatrixIsStillThere() throws IOException {
        captureTwoQuadrimestres();
        FixturePec wrongMunicipality = new FixturePec(IBGE).writingMunicipality("1234567");

        DiagnosticMatrix matrix = runAll(wrongMunicipality);

        assertThat(matrix.rows()).hasSize(2 * GatePack.allWithNotaFinal().size());
        assertThat(matrix.count(Cell.ERROR)).isEqualTo(matrix.rows().size());
        assertThat(matrix.errors())
                .extracting(Row::reason)
                .allSatisfy(reason -> assertThat(reason).doesNotContainPattern("\\d{10}"));
    }

    // ---- which periods, which revisions

    @Test
    void periodsOnlyChooseWhichCapturedPeriodsRun() throws IOException {
        captureTwoQuadrimestres();
        FixturePec pec = new FixturePec(IBGE);
        PortaoDDiagnosticRun run = open(Set.of(Q1_2026));

        DiagnosticMatrix matrix = run.run(pec.sourceIdentity(), ALL_MONTHS, pec.inputs());

        assertThat(run.periods()).containsExactly(Q1_2026);
        assertThat(matrix.rows()).extracting(Row::period).containsOnly(Q1_2026);
        assertThat(matrix.rows()).hasSize(GatePack.allWithNotaFinal().size());
    }

    @Test
    void aRequestedPeriodNobodyCapturedIsRefusedAndNeverSilentlyDropped() throws IOException {
        captureTwoQuadrimestres();
        Set<Quadrimestre> requested = Set.of(Q1_2026, new Quadrimestre(2025, 2));

        assertThatThrownBy(() -> open(requested))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2025-Q2");
    }

    @Test
    void everyCapturedRevisionIsItsOwnRowAndTheNotaFinalOfADriftedExportReadsWhatItsDownloadHeld() throws IOException {
        captureTwoQuadrimestres();
        Files.write(exports().resolve("q1-2026-later.csv"), laterWithC1Changed());
        new ReferenceCapture(artifacts(), manifests(), "zz", CLOCK).capture(exports(), IBGE);

        DiagnosticMatrix matrix = runAll(new FixturePec(IBGE));

        List<Row> first = rowsOf(matrix, Q1_2026);
        assertThat(first)
                .filteredOn(row -> "C1".equals(row.pack()))
                .extracting(Row::referenceId)
                .containsExactly("zz-9999990-2026q1-c1-team-r1", "zz-9999990-2026q1-c1-team-r2");
        // the later file changed C1 and the Nota Final only: its Nota Final reads C1 r2 and the revisions
        // of C2 to C7 the first file was captured as, though their manifests name the first file's bytes
        assertThat(first)
                .filteredOn(row -> CIII.equals(row.pack()))
                .extracting(Row::referenceId)
                .contains("zz-9999990-2026q1-ciii-team-r1", "zz-9999990-2026q1-ciii-team-r2");
        assertThat(first)
                .filteredOn(row -> CIII.equals(row.pack()))
                .allSatisfy(row -> assertThat(row.status()).isNotIn(Cell.PENDING, Cell.ERROR));
    }

    @Test
    void aPackWithoutAReferenceIsAPendingRowAndTheNotaFinalOfThatExportIsPending() throws IOException {
        captureTwoQuadrimestres();
        Files.delete(manifests().resolve("zz-9999990-2026q1-c3-team-r1.json"));

        DiagnosticMatrix matrix = runAll(new FixturePec(IBGE));

        List<Row> rows = rowsOf(matrix, Q1_2026);
        assertThat(rows)
                .filteredOn(row -> "C3".equals(row.pack()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo(Cell.PENDING);
                    assertThat(row.referenceId()).isEqualTo("-");
                    assertThat(row.reason()).contains("nenhuma referência capturada");
                });
        assertThat(rows)
                .filteredOn(row -> CIII.equals(row.pack()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo(Cell.PENDING);
                    assertThat(row.reason()).contains("C3");
                });
        assertThat(rowsOf(matrix, Q3_2025)).extracting(Row::status).doesNotContain(Cell.PENDING, Cell.ERROR);
    }

    // ---- what is refused

    @Test
    void aPecOfAnotherMunicipalityThanTheManifestsIsRefusedBeforeAnythingIsRead() throws IOException {
        captureTwoQuadrimestres();
        FixturePec other = new FixturePec("3541307");
        SourceIdentity foreign = other.sourceIdentity();
        AcquisitionInputs inputs = other.inputs();
        PortaoDDiagnosticRun run = open(Set.of());

        assertThatThrownBy(() -> run.run(foreign, ALL_MONTHS, inputs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another municipality");
        assertThat(other.requests()).isEmpty();
    }

    @Test
    void aJsonFileThatIsNotATeamReferenceManifestRefusesTheRun() throws IOException {
        captureTwoQuadrimestres();
        Files.writeString(manifests().resolve("notes.json"), "{}");
        Set<Quadrimestre> everyPeriod = Set.of();

        assertThatThrownBy(() -> open(everyPeriod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not named like a team reference manifest");
    }

    @Test
    void anEmptyManifestsDirectoryAsksForTheCaptureFirst() throws IOException {
        Files.createDirectories(manifests());
        Set<Quadrimestre> everyPeriod = Set.of();

        assertThatThrownBy(() -> open(everyPeriod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("run the capture first");
    }

    // ---- what is written

    @Test
    void theMatrixIsWrittenMaskedWithoutAnIneAndNeverOverwritten() throws IOException {
        captureTwoQuadrimestres();
        DiagnosticMatrix matrix = runAll(new FixturePec(IBGE));

        Path store = artifacts();
        Path directory = DiagnosticMatrixWriter.write(matrix, store);

        assertThat(directory).isEqualTo(store.resolve("diagnostico").resolve("20261008T150000Z"));
        String markdown = Files.readString(directory.resolve(DiagnosticMatrixWriter.MARKDOWN));
        String json = Files.readString(directory.resolve(DiagnosticMatrixWriter.JSON));
        assertThat(markdown).contains("não é evidência do Portão D").contains("MISSING_LOCAL_MONTHS");
        for (String written : List.of(markdown, json)) {
            FIXTURE_INES.forEach(ine -> assertThat(written).doesNotContain(ine));
            assertThat(INE_SHAPE.matcher(written).find()).isFalse();
        }
        assertThatThrownBy(() -> DiagnosticMatrixWriter.write(matrix, store))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void everyCountOfTeamsInTheJsonIsMaskedAndTheRowsFollowThePlan() throws IOException {
        captureTwoQuadrimestres();
        DiagnosticMatrix matrix = runAll(new FixturePec(IBGE));

        JsonNode root = new ObjectMapper().readTree(DiagnosticMatrixWriter.json(matrix));

        assertThat(root.get("purpose").stringValue()).isEqualTo("DIAGNOSTIC");
        assertThat(root.get("periods")).hasSize(2);
        assertThat(root.get("rows")).hasSameSizeAs(matrix.rows());
        for (JsonNode row : root.get("rows")) {
            assertThat(row.get("not_in_siaps").stringValue()).isIn("<10", "-");
            for (JsonNode type : row.get("team_types")) {
                assertThat(type.get("n_s").stringValue()).isEqualTo("<10");
                assertThat(type.get("n_l").stringValue()).isEqualTo("<10");
                assertThat(type.get("without_local_class").stringValue()).isEqualTo("<10");
            }
        }
    }

    @Test
    void theMatrixOfAPeriodTheSourceCannotRunStillListsItInTheMarkdownWithItsMissingMonths() throws IOException {
        captureTwoQuadrimestres();
        FixturePec pec = new FixturePec(IBGE);
        Set<YearMonth> coverage = new HashSet<>(ALL_MONTHS);
        coverage.remove(YearMonth.of(2026, 2));

        String markdown =
                DiagnosticMatrixWriter.markdown(open(Set.of()).run(pec.sourceIdentity(), coverage, pec.inputs()));

        assertThat(markdown).contains("| 2026Q1 | MISSING_LOCAL_MONTHS | 2026-02 |");
        assertThat(markdown).contains("| 2025Q3 | RUN | - |");
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
}
