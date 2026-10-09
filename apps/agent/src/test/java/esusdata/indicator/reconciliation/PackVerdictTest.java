package esusdata.indicator.reconciliation;

import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.EAP_1;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.ESF_1;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.ESF_2;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.INDICATOR_NAMES;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.all;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.indicator.reconciliation.OfficialTeamExportCsvParser.ExpectedScope;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import esusdata.indicator.reconciliation.SiapsTeamExportFixtures.Export;
import esusdata.indicator.reconciliation.ValidatedReference.UniverseConfidence;
import esusdata.result.model.InputFingerprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The verdict of one pack on invented data. A GATE verdict needs an official reference (a team
 * export, parsed as in production); the public aggregate can only be compared as a DIAGNOSTIC, and
 * the tests that show a missing row or list is PENDING run the aggregate that way too, so that they
 * would fail if the comparison still filled the gap in (a GATE run over an aggregate is PENDING
 * for its unknown universe whatever else it lacks).
 */
class PackVerdictTest {

    private static final GatePack C1 = GatePack.bySiapsCode(110).orElseThrow();
    private static final GatePack C2 = GatePack.bySiapsCode(108).orElseThrow();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final String ESF = SiapsParser.ESF;
    private static final String EAP = SiapsParser.EAP;
    private static final String RULE = "c1@x";
    private static final String OUTSIDER = "0000000099";
    private static final String FINGERPRINT = InputFingerprint.compute(Map.of("sensibilidade-c1-2026-01", "abc"));
    private static final ExpectedScope SCOPE =
            new ExpectedScope(SiapsTeamExportFixtures.MUNICIPALITY_IBGE, SiapsTeamExportFixtures.QUADRIMESTRE);

    private static final List<SiapsSnapshot.Team> TEAMS = List.of(
            new SiapsSnapshot.Team(ESF_1, ESF), new SiapsSnapshot.Team(ESF_2, ESF), new SiapsSnapshot.Team(EAP_1, EAP));

    private static SiapsSnapshot.Row row(String type, ClassCounts counts) {
        return new SiapsSnapshot.Row("999999", "2026Q2", 110, type, counts);
    }

    private static SiapsSnapshot snapshot(List<SiapsSnapshot.Row> rows, Map<Integer, List<SiapsSnapshot.Team>> teams) {
        return new SiapsSnapshot("999999", "2026Q2", List.of("2026Q2"), rows, teams);
    }

    /** The public answer for C1: both rows, and today's team directory. */
    private static ValidatedReference aggregate(ClassCounts esf, ClassCounts eap, List<SiapsSnapshot.Team> teams) {
        return ValidatedReference.fromPublicAggregate(
                snapshot(List.of(row(ESF, esf), row(EAP, eap)), Map.of(110, teams)), C1);
    }

    /** An export with two eSF teams and one eAP team, each with the same concept in all seven indicators. */
    private static Export export(Classification esf1, Classification esf2, Classification eap1) {
        return SiapsTeamExportFixtures.export()
                .team(ESF_1, ESF, esf1, all(esf1))
                .team(ESF_2, ESF, esf2, all(esf2))
                .team(EAP_1, EAP, eap1, all(eap1));
    }

    private static ValidatedReference official(Export export, GatePack pack) {
        return ValidatedReference.from(OfficialTeamExportCsvParser.parse(export.bytes(), SCOPE), pack);
    }

    private static LocalClasses local(Map<String, Classification> classes, String... alsoSeen) {
        Set<String> seen = new HashSet<>(classes.keySet());
        seen.addAll(List.of(alsoSeen));
        return new LocalClasses(classes, seen);
    }

    private static LocalClasses perfectLocal() {
        return local(Map.of(ESF_1, Classification.BOM, ESF_2, Classification.OTIMO, EAP_1, Classification.BOM));
    }

    private static PackVerdict gate(ValidatedReference reference, LocalClasses local) {
        return PackVerdict.evaluate(reference.pack(), RULE, ReferencePurpose.GATE, reference, local, FINGERPRINT);
    }

    private static PackVerdict diagnostic(ValidatedReference reference, LocalClasses local) {
        return PackVerdict.evaluate(reference.pack(), RULE, ReferencePurpose.DIAGNOSTIC, reference, local, FINGERPRINT);
    }

    @Test
    void passesWhenEveryEvaluatedRowIsWithinTheThreshold() {
        ValidatedReference reference =
                official(export(Classification.BOM, Classification.OTIMO, Classification.BOM), C1);

        PackVerdict verdict = gate(reference, perfectLocal());

        assertThat(verdict.status()).isEqualTo(Status.PASSED);
        assertThat(verdict.isGateEvidence()).isTrue();
        assertThat(verdict.rows()).allMatch(row -> row.distance() == 0);
        assertThat(verdict.quadrimestre()).isEqualTo("2026Q1");
        assertThat(verdict.localSourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void failsWhenOneRowIsOverTheThreshold() {
        ValidatedReference reference =
                official(export(Classification.BOM, Classification.OTIMO, Classification.BOM), C1);
        LocalClasses local =
                local(Map.of(ESF_1, Classification.REGULAR, ESF_2, Classification.REGULAR, EAP_1, Classification.BOM));

        PackVerdict verdict = gate(reference, local);

        assertThat(verdict.status()).isEqualTo(Status.FAILED);
        assertThat(verdict.rows().getFirst().passed()).isFalse();
        assertThat(verdict.isGateEvidence()).isTrue();
    }

    @Test
    void aReferenceWithNoRowToEvaluateIsPendingNeverAVacuousPass() {
        // no factory builds this (a row of zeros is a gap): it is the guard of the comparison itself
        ValidatedReference reference = new ValidatedReference(
                C1,
                SourceKind.PUBLIC_AGGREGATE,
                "999999",
                "2026Q2",
                UniverseConfidence.UNKNOWN,
                List.of(),
                Map.of(ESF, ClassCounts.EMPTY, EAP, ClassCounts.EMPTY),
                List.of(),
                Map.of());

        PackVerdict verdict = diagnostic(reference, local(Map.of()));

        assertThat(verdict.status()).isEqualTo(Status.PENDING);
        assertThat(verdict.reason()).isEqualTo("sem linhas avaliáveis");
        assertThat(verdict.isGateEvidence()).isFalse();
    }

    @Test
    void reportsTeamsWithoutLocalClassAndLocalTeamsOutsideTheOfficialUniverse() {
        ValidatedReference reference =
                official(export(Classification.BOM, Classification.OTIMO, Classification.BOM), C1);
        LocalClasses local = local(Map.of(ESF_1, Classification.BOM, OUTSIDER, Classification.BOM), "0000000098");

        PackVerdict verdict = gate(reference, local);

        assertThat(verdict.localNotInSiaps()).isEqualTo(2);
        assertThat(verdict.rows().getFirst().semClasseLocal()).isEqualTo(1);
        assertThat(verdict.rows().getFirst().localTeams()).isEqualTo(1);
        assertThat(verdict.teamLines())
                .extracting(PackVerdict.TeamLine::note)
                .contains("sem classe local", "fora da lista do SIAPS");
    }

    @Test
    void aDiagnosticReferenceIsNeverEvidence() {
        ValidatedReference reference =
                official(export(Classification.BOM, Classification.OTIMO, Classification.BOM), C1);

        PackVerdict verdict = diagnostic(reference, local(Map.of()));

        assertThat(verdict.status()).isEqualTo(Status.FAILED);
        assertThat(verdict.purpose()).isEqualTo(ReferencePurpose.DIAGNOSTIC);
        assertThat(verdict.isGateEvidence()).isFalse();
    }

    @Test
    void missingOfficialEsfRowIsPendingNotZero() {
        // an eSF team of the export has no C1 row; every other indicator of every team is there
        Export holes = export(Classification.BOM, Classification.OTIMO, Classification.BOM)
                .dropRow(ESF_2, INDICATOR_NAMES.getFirst());
        // the public answer has the eAP row only
        SiapsSnapshot noEsfRow = snapshot(List.of(row(EAP, new ClassCounts(0, 0, 1, 0))), Map.of(110, TEAMS));

        PackVerdict c1 = gate(official(holes, C1), perfectLocal());
        PackVerdict c2 = gate(official(holes, C2), perfectLocal());
        PackVerdict aggregate = diagnostic(ValidatedReference.fromPublicAggregate(noEsfRow, C1), perfectLocal());

        assertThat(c1.status()).isEqualTo(Status.PENDING);
        assertThat(c1.reason()).contains("referência incompleta", "sem a linha de C1");
        assertThat(c1.rows()).isEmpty();
        assertThat(c2.status()).as("the hole is C1's alone").isEqualTo(Status.PASSED);
        assertThat(aggregate.status()).isEqualTo(Status.PENDING);
        assertThat(aggregate.reason()).contains("referência incompleta", "linha de eSF");
        assertThat(aggregate.rows()).isEmpty();
    }

    @Test
    void missingOfficialEapRowRequiredByUniverseIsPending() {
        // the eAP team is in the universe of the export (it has the other six rows and its Total row)
        Export holes = export(Classification.BOM, Classification.OTIMO, Classification.BOM)
                .dropRow(EAP_1, INDICATOR_NAMES.getFirst());
        // the public answer lists an eAP team but has no eAP row
        SiapsSnapshot noEapRow = snapshot(List.of(row(ESF, new ClassCounts(0, 0, 1, 1))), Map.of(110, TEAMS));

        PackVerdict official = gate(official(holes, C1), perfectLocal());
        PackVerdict aggregate = diagnostic(ValidatedReference.fromPublicAggregate(noEapRow, C1), perfectLocal());

        // the eSF row alone would pass: the verdict still waits for the eAP row the universe requires
        assertThat(official.status()).isEqualTo(Status.PENDING);
        assertThat(official.reason()).contains("referência incompleta");
        assertThat(aggregate.status()).isEqualTo(Status.PENDING);
        assertThat(aggregate.reason()).contains("linha de eAP");
    }

    @Test
    void explicitZeroRowIsEvaluatedAsZero() {
        // official: the universe of the file is complete and has no eAP team, so eAP is a stated zero
        Export onlyEsf = SiapsTeamExportFixtures.export()
                .team(ESF_1, ESF, Classification.BOM, all(Classification.BOM))
                .team(ESF_2, ESF, Classification.OTIMO, all(Classification.OTIMO));
        LocalClasses withAnOutsider =
                local(Map.of(ESF_1, Classification.BOM, ESF_2, Classification.OTIMO, OUTSIDER, Classification.BOM));

        PackVerdict decided = gate(official(onlyEsf, C1), withAnOutsider);

        RowResult eap = decided.rows().get(1);
        assertThat(decided.status())
                .as("decided by eSF, not PENDING for a missing eAP row")
                .isEqualTo(Status.PASSED);
        assertThat(eap.siaps()).isEqualTo(ClassCounts.EMPTY);
        assertThat(eap.evaluated()).isFalse();
        assertThat(eap.localTeams()).isZero();
        assertThat(decided.localNotInSiaps())
                .as("the outsider is reported, not counted")
                .isEqualTo(1);
    }

    @Test
    void aRowOfZerosOfThePublicAnswerIsAGapNotAStatedZero() {
        // a row of zeros for eAP while today's directory lists three eAP teams, all BOM locally: read as
        // a zero it would be a distance of 6 against T = 2, but the answer has no universe to prove it
        List<SiapsSnapshot.Team> teams = List.of(
                new SiapsSnapshot.Team(ESF_1, ESF),
                new SiapsSnapshot.Team(ESF_2, ESF),
                new SiapsSnapshot.Team("0000000021", EAP),
                new SiapsSnapshot.Team("0000000022", EAP),
                new SiapsSnapshot.Team("0000000023", EAP));
        LocalClasses local = local(Map.of(
                ESF_1,
                Classification.BOM,
                ESF_2,
                Classification.OTIMO,
                "0000000021",
                Classification.BOM,
                "0000000022",
                Classification.BOM,
                "0000000023",
                Classification.BOM));

        ValidatedReference reference = aggregate(new ClassCounts(0, 0, 1, 1), ClassCounts.EMPTY, teams);

        PackVerdict asDiagnostic = diagnostic(reference, local);
        PackVerdict asGate = gate(reference, local);

        assertThat(reference.gaps()).hasSize(1);
        assertThat(asDiagnostic.status()).isEqualTo(Status.PENDING);
        assertThat(asDiagnostic.reason()).contains("referência incompleta", "linha de eAP de C1", "só de zeros");
        assertThat(asDiagnostic.rows()).isEmpty();
        assertThat(asGate.status()).isEqualTo(Status.PENDING);
        assertThat(asGate.isGateEvidence()).isFalse();
    }

    @Test
    void currentTeamDirectoryCannotSupplyHistoricalUniverse() {
        // the directory lists exactly the teams of the counts and every local class agrees
        ValidatedReference reference = aggregate(new ClassCounts(0, 0, 1, 1), new ClassCounts(0, 0, 1, 0), TEAMS);

        PackVerdict asGate = gate(reference, perfectLocal());
        PackVerdict asDiagnostic = diagnostic(reference, perfectLocal());

        assertThat(reference.sourceKind()).isEqualTo(SourceKind.PUBLIC_AGGREGATE);
        assertThat(reference.universe()).isEqualTo(UniverseConfidence.UNKNOWN);
        assertThat(asGate.status()).isEqualTo(Status.PENDING);
        assertThat(asGate.reason()).contains("universo histórico");
        assertThat(asGate.rows()).isEmpty();
        assertThat(asGate.isGateEvidence()).isFalse();
        assertThat(asDiagnostic.status()).isEqualTo(Status.PASSED);
    }

    @Test
    void missingTeamListIsPendingNotAVacuousPass() {
        // 2 ÓTIMO eSF teams in the answer, no team list, nothing local: D = 2 = T, which a missing list
        // used to pass as an empty one
        SiapsSnapshot noList = snapshot(
                List.of(row(ESF, new ClassCounts(0, 0, 0, 2)), row(EAP, new ClassCounts(0, 0, 1, 0))), Map.of());

        PackVerdict verdict = diagnostic(ValidatedReference.fromPublicAggregate(noList, C1), local(Map.of()));

        assertThat(noList.teamsOf(110)).isEmpty();
        assertThat(verdict.status()).isEqualTo(Status.PENDING);
        assertThat(verdict.reason()).contains("referência incompleta", "lista de equipes de C1");
        assertThat(verdict.rows()).isEmpty();
    }

    @Test
    void aReferenceIsOnlyEvaluatedForThePackItWasValidatedFor() {
        ValidatedReference reference =
                official(export(Classification.BOM, Classification.OTIMO, Classification.BOM), C1);
        LocalClasses local = perfectLocal();

        assertThatThrownBy(() -> PackVerdict.evaluate(C2, RULE, ReferencePurpose.GATE, reference, local, FINGERPRINT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validated for C1");
    }

    @Test
    void aVerdictCarriesAnInputFingerprintOrNoneAtAll() {
        List<RowResult> noRows = List.of();
        List<PackVerdict.TeamLine> noLines = List.of();

        assertThatThrownBy(() -> new PackVerdict(
                        C1,
                        RULE,
                        ReferencePurpose.GATE,
                        Status.PENDING,
                        "",
                        null,
                        noRows,
                        0,
                        noLines,
                        "/home/me/extracts"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256:<64 hex>");
        assertThat(PackVerdict.pending(C1, RULE, ReferencePurpose.GATE, "x").localSourceFingerprint())
                .isEqualTo(PackVerdict.NO_LOCAL_SOURCE);
    }

    @Test
    void summaryMasksSmallCountsAndNeverShowsClassesOrInes(@TempDir Path directory) throws IOException {
        ValidatedReference reference =
                official(export(Classification.BOM, Classification.OTIMO, Classification.BOM), C1);
        PackVerdict verdict = gate(reference, local(Map.of(ESF_1, Classification.BOM)));

        Path file = SummaryWriter.write(directory, verdict, DAY).orElseThrow();
        String text = Files.readString(file);

        assertThat(file.getFileName().toString()).isEqualTo("portao-d-" + C1.packId() + "-2026Q1.md");
        assertThat(text)
                .contains(
                        "siaps-distribuicao-por-classe@1",
                        RULE,
                        "2026Q1",
                        "2026-10-07",
                        "<10",
                        "- Fingerprint da fonte local: `" + FINGERPRINT + "`")
                .doesNotContain(ESF_1, "OTIMO", "BOM");
        assertThat(RawWriter.write(directory, verdict)).hasSize(2);
        assertThat(Files.readString(directory.resolve("portao-d-" + C1.packId() + "-2026Q1-equipes.csv")))
                .contains(ESF_1 + ";eSF;BOM");
    }

    @Test
    void largeCountsAreShownAndDiagnosticSummariesAreLabelled(@TempDir Path directory) throws IOException {
        assertThat(SummaryWriter.mask(9)).isEqualTo("<10");
        assertThat(SummaryWriter.mask(10)).isEqualTo("10");
        PackVerdict verdict = PackVerdict.evaluate(
                C1,
                RULE,
                ReferencePurpose.DIAGNOSTIC,
                aggregate(new ClassCounts(0, 0, 10, 2), new ClassCounts(0, 0, 1, 0), List.of()),
                local(Map.of()),
                PackVerdict.NO_LOCAL_SOURCE);

        Path file = SummaryWriter.write(directory, verdict, DAY).orElseThrow();

        assertThat(file.getFileName().toString()).startsWith("diagnostico-");
        assertThat(Files.readString(file))
                .contains("não é evidência do Portão D")
                .contains("| 12 |")
                .doesNotContain("Fingerprint");
    }

    @Test
    void aPendingVerdictWithoutReferenceWritesNothing(@TempDir Path directory) throws IOException {
        PackVerdict pending = PackVerdict.pending(C1, RULE, ReferencePurpose.GATE, Eligibility.waitingFor(C1));

        assertThat(SummaryWriter.write(directory, pending, DAY)).isEmpty();
        assertThat(RawWriter.write(directory, pending)).isEmpty();
        assertThat(pending.reason()).isEqualTo("aguardando 2026Q2 no SIAPS");
    }
}
