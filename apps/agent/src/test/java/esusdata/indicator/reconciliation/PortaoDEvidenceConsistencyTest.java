package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import esusdata.indicator.reconciliation.PortaoDEvidenceFixtures.Tree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The manifests, dossiers and set summaries committed under {@code docs/indicadores/portoes} are
 * what the policy pins and what the writers emit. On the real repository the checks hold vacuously
 * today (nothing is committed yet); on a tree built by the real writers each is shown to refuse what
 * it is for. Regenerating a dossier from the raw artifacts is {@code PortaoDEvidenceReplayLiveTest}.
 */
class PortaoDEvidenceConsistencyTest {

    private static final Path REPO = Path.of("..", "..");

    @TempDir
    Path workspace;

    private Tree tree() throws IOException {
        return PortaoDEvidenceFixtures.decided(workspace, new C4Pack().descriptor(), Status.PASSED);
    }

    private static List<String> problemsOf(Tree tree) throws IOException {
        return PortaoDEvidenceChecks.fileProblems(tree.loadedPolicy(), tree.root());
    }

    @Test
    void theRealRepositoryHoldsTheChecksVacuouslyTodayAndWheneverItCommitsWhatItPins() throws IOException {
        assertThat(PortaoDEvidenceChecks.fileProblems(PortaoDEvidenceChecks.policyOf(REPO), REPO))
                .isEmpty();
    }

    @Test
    void whatTheRealWritersEmitPassesIncludingASourceUrlWithALongDigitRun() throws IOException {
        Tree tree = tree();

        assertThat(Files.readString(tree.dossierJson())).contains("portaria-gm-ms-705373819");
        assertThat(problemsOf(tree)).isEmpty();
    }

    @Test
    void aManifestOrDossierCitedByNoDeclarationIsRefused() throws IOException {
        Tree tree = tree();
        Files.writeString(tree.manifest().resolveSibling("zz-9999990-2026q1-c4-team-r9.json"), "{}");
        Files.writeString(tree.dossierJson().resolveSibling("zz-9999990-2026q1-c4-team-r9-c4.json"), "{}");

        assertThat(problemsOf(tree))
                .anyMatch(problem -> problem.contains("references/zz-9999990-2026q1-c4-team-r9.json is cited by no"))
                .anyMatch(problem -> problem.contains("compatibilidade/zz-9999990-2026q1-c4-team-r9-c4.json is cited"));
    }

    @Test
    void aCitedFileThatIsMissingOrNotThePinnedOneIsRefused() throws IOException {
        Tree changed = tree();
        PortaoDEvidenceFixtures.replaceIn(changed.manifest(), "\"row_count\"", "\"row_count \"");
        assertThat(problemsOf(changed)).anyMatch(problem -> problem.contains("is not the file its declaration pins"));

        Path other = Files.createDirectories(workspace.resolve("other"));
        Tree missing = PortaoDEvidenceFixtures.decided(other, new C4Pack().descriptor(), Status.PASSED);
        Files.delete(missing.dossierJson());
        assertThat(problemsOf(missing)).anyMatch(problem -> problem.contains("is cited by a declaration and does not"));
    }

    @Test
    void aDossierWithoutAFieldOfTheEnvelopeOrOfAnotherSchemaVersionIsRefused() throws IOException {
        Tree lacking = tree();
        PortaoDEvidenceFixtures.replaceIn(lacking.dossierJson(), "\"coverage\"", "\"coverage_\"");
        assertThat(problemsOf(lacking)).anyMatch(problem -> problem.contains("lacks coverage"));

        Path other = Files.createDirectories(workspace.resolve("other"));
        Tree schema = PortaoDEvidenceFixtures.decided(other, new C4Pack().descriptor(), Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(
                schema.dossierJson(), "\"schema_version\": \"1\"", "\"schema_version\": \"2\"");
        assertThat(problemsOf(schema)).anyMatch(problem -> problem.contains("is not schema_version 1"));
    }

    @Test
    void aMarkdownThatIsNotTheRenderingOfItsJsonIsRefusedForDossiersAndForSetSummaries() throws IOException {
        Tree dossier = tree();
        PortaoDEvidenceFixtures.append(dossier.dossierMarkdown(), "a line nobody rendered\n");
        assertThat(problemsOf(dossier))
                .anyMatch(problem -> problem.endsWith(dossier.dossierMarkdown().getFileName()
                        + " is not the rendering of "
                        + dossier.dossierJson().getFileName()));

        Path other = Files.createDirectories(workspace.resolve("other"));
        Tree summary = PortaoDEvidenceFixtures.decided(other, new C4Pack().descriptor(), Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(summary.summaryMarkdown(), "**PASSED**", "**FAILED**");
        assertThat(problemsOf(summary)).anyMatch(problem -> problem.contains(" is not the rendering of "));
    }

    @Test
    void aMarkdownWithoutItsJsonOrAJsonWithoutItsMarkdownIsRefused() throws IOException {
        Tree orphan = tree();
        Files.writeString(orphan.dossierJson().resolveSibling("zz-extra.md"), "x\n");
        assertThat(problemsOf(orphan)).anyMatch("zz-extra.md has no .json beside it"::equals);

        Path other = Files.createDirectories(workspace.resolve("other"));
        Tree bare = PortaoDEvidenceFixtures.decided(other, new C4Pack().descriptor(), Status.PASSED);
        Files.delete(bare.summaryMarkdown());
        assertThat(problemsOf(bare)).anyMatch(problem -> problem.contains(" is missing: the rendering of "));
    }

    @Test
    void aSetSummaryOfAnotherSchemaVersionIsRefused() throws IOException {
        Tree tree = tree();
        PortaoDEvidenceFixtures.replaceIn(tree.summaryJson(), "\"schema_version\": \"1\"", "\"schema_version\": \"9\"");

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("is not a set summary of schema_version 1"));
    }
}
