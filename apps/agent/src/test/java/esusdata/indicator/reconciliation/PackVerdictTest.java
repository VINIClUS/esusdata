package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.PackVerdict.Mode;
import esusdata.indicator.reconciliation.PackVerdict.Status;
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

class PackVerdictTest {

    private static final GatePack C1 = GatePack.bySiapsCode(110).orElseThrow();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    private static final List<SiapsSnapshot.Team> TEAMS = List.of(
            new SiapsSnapshot.Team("0000000011", "eSF"),
            new SiapsSnapshot.Team("0000000012", "eSF"),
            new SiapsSnapshot.Team("0000000013", "eAP"));

    private static SiapsSnapshot snapshot(ClassCounts esf, ClassCounts eap, List<SiapsSnapshot.Team> teams) {
        return new SiapsSnapshot(
                "999999",
                "2026Q2",
                List.of("2026Q2"),
                List.of(
                        new SiapsSnapshot.Row("999999", "2026Q2", 110, "eSF", esf),
                        new SiapsSnapshot.Row("999999", "2026Q2", 110, "eAP", eap)),
                Map.of(110, teams));
    }

    private static LocalClasses local(Map<String, Classification> classes, String... alsoSeen) {
        Set<String> seen = new HashSet<>(classes.keySet());
        seen.addAll(List.of(alsoSeen));
        return new LocalClasses(classes, seen);
    }

    @Test
    void passesWhenEveryEvaluatedRowIsWithinTheThreshold() {
        SiapsSnapshot snapshot = snapshot(new ClassCounts(0, 0, 1, 1), new ClassCounts(0, 0, 1, 0), TEAMS);
        LocalClasses local = local(Map.of(
                "0000000011", Classification.BOM,
                "0000000012", Classification.OTIMO,
                "0000000013", Classification.BOM));

        PackVerdict verdict = PackVerdict.evaluate(C1, "c1@x", Mode.GATE, snapshot, local);

        assertThat(verdict.status()).isEqualTo(Status.PASSED);
        assertThat(verdict.isGateEvidence()).isTrue();
        assertThat(verdict.rows()).allMatch(row -> row.distance() == 0);
    }

    @Test
    void failsWhenOneRowIsOverTheThreshold() {
        SiapsSnapshot snapshot = snapshot(new ClassCounts(0, 0, 1, 1), new ClassCounts(0, 0, 1, 0), TEAMS);
        LocalClasses local = local(Map.of(
                "0000000011", Classification.REGULAR,
                "0000000012", Classification.REGULAR,
                "0000000013", Classification.BOM));

        PackVerdict verdict = PackVerdict.evaluate(C1, "c1@x", Mode.GATE, snapshot, local);

        assertThat(verdict.status()).isEqualTo(Status.FAILED);
        assertThat(verdict.rows().getFirst().passed()).isFalse();
        assertThat(verdict.isGateEvidence()).isTrue();
    }

    @Test
    void isPendingWithoutLinesToEvaluateNeverAVacuousPass() {
        SiapsSnapshot snapshot = snapshot(ClassCounts.EMPTY, ClassCounts.EMPTY, List.of());

        PackVerdict verdict = PackVerdict.evaluate(C1, "c1@x", Mode.GATE, snapshot, local(Map.of()));

        assertThat(verdict.status()).isEqualTo(Status.PENDING);
        assertThat(verdict.reason()).isEqualTo("sem linhas avaliáveis");
        assertThat(verdict.isGateEvidence()).isFalse();
    }

    @Test
    void reportsTeamsWithoutLocalClassAndLocalTeamsOutsideTheList() {
        SiapsSnapshot snapshot = snapshot(new ClassCounts(0, 0, 1, 1), new ClassCounts(0, 0, 1, 0), TEAMS);
        LocalClasses local =
                local(Map.of("0000000011", Classification.BOM, "0000000099", Classification.BOM), "0000000098");

        PackVerdict verdict = PackVerdict.evaluate(C1, "c1@x", Mode.GATE, snapshot, local);

        assertThat(verdict.localNotInSiaps()).isEqualTo(2);
        assertThat(verdict.rows().getFirst().semClasseLocal()).isEqualTo(1);
        assertThat(verdict.rows().getFirst().localTeams()).isEqualTo(1);
        assertThat(verdict.teamLines())
                .extracting(PackVerdict.TeamLine::note)
                .contains("sem classe local", "fora da lista do SIAPS");
    }

    @Test
    void informativeModeIsNeverEvidence() {
        SiapsSnapshot snapshot = snapshot(new ClassCounts(0, 0, 1, 1), new ClassCounts(0, 0, 1, 0), TEAMS);

        PackVerdict verdict = PackVerdict.evaluate(C1, "c1@x", Mode.INFORMATIVO, snapshot, local(Map.of()));

        assertThat(verdict.status()).isEqualTo(Status.FAILED);
        assertThat(verdict.isGateEvidence()).isFalse();
    }

    @Test
    void summaryMasksSmallCountsAndNeverShowsClassesOrInes(@TempDir Path directory) throws IOException {
        SiapsSnapshot snapshot = snapshot(new ClassCounts(0, 0, 1, 1), new ClassCounts(0, 0, 1, 0), TEAMS);
        LocalClasses local = local(Map.of("0000000011", Classification.BOM));
        PackVerdict verdict = PackVerdict.evaluate(C1, "c1@x", Mode.GATE, snapshot, local);

        Path file = SummaryWriter.write(directory, verdict, DAY).orElseThrow();
        String text = Files.readString(file);

        assertThat(file.getFileName().toString()).isEqualTo("portao-d-" + C1.packId() + "-2026Q2.md");
        assertThat(text)
                .contains("siaps-distribuicao-por-classe@1", "c1@x", "2026Q2", "2026-10-07", "<10")
                .doesNotContain("0000000011", "OTIMO", "BOM");
        assertThat(RawWriter.write(directory, verdict)).hasSize(2);
        assertThat(Files.readString(directory.resolve("portao-d-" + C1.packId() + "-2026Q2-equipes.csv")))
                .contains("0000000011;eSF;BOM");
    }

    @Test
    void largeCountsAreShownAndInformativeSummariesAreLabelled(@TempDir Path directory) throws IOException {
        assertThat(SummaryWriter.mask(9)).isEqualTo("<10");
        assertThat(SummaryWriter.mask(10)).isEqualTo("10");
        PackVerdict verdict = PackVerdict.evaluate(
                C1,
                "c1@x",
                Mode.INFORMATIVO,
                snapshot(new ClassCounts(0, 0, 10, 2), ClassCounts.EMPTY, List.of()),
                local(Map.of()));

        Path file = SummaryWriter.write(directory, verdict, DAY).orElseThrow();

        assertThat(file.getFileName().toString()).startsWith("informativo-");
        assertThat(Files.readString(file))
                .contains("não é evidência do Portão D")
                .contains("| 12 |");
    }

    @Test
    void aPendingVerdictWithoutReferenceWritesNothing(@TempDir Path directory) throws IOException {
        PackVerdict pending = PackVerdict.pending(C1, "c1@x", Mode.GATE, Eligibility.waitingFor(C1));

        assertThat(SummaryWriter.write(directory, pending, DAY)).isEmpty();
        assertThat(RawWriter.write(directory, pending)).isEmpty();
        assertThat(pending.reason()).isEqualTo("aguardando 2026Q2 no SIAPS");
    }
}
