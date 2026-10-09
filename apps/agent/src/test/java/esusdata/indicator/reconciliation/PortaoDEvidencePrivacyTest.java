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
 * Nothing committed under {@code docs/indicadores/portoes} may identify a team, a person or a
 * machine. The guard is the dossier writer's own (a dossier is judged as the writer judges it
 * before writing, so what it allows, a long digit run in a source URL, is allowed here); manifests
 * and set summaries, which carry hashes and the municipality code of the reference, are scanned as
 * text once those are set aside; every file is also refused an IPv4 address or an e-mail, and a
 * JSON one a key named for a password, a token or a {@code senha}. The messages name the file, never
 * the value.
 */
class PortaoDEvidencePrivacyTest {

    private static final Path REPO = Path.of("..", "..");
    private static final String INE = "0000000011";

    @TempDir
    Path workspace;

    private Tree tree() throws IOException {
        return PortaoDEvidenceFixtures.decided(workspace, new C4Pack().descriptor(), Status.PASSED);
    }

    private static List<String> problemsOf(Tree tree) throws IOException {
        return PortaoDEvidenceChecks.privacyProblems(tree.root());
    }

    @Test
    void theRealRepositoryIsCleanAndSoIsWhatTheRealWritersEmit() throws IOException {
        assertThat(PortaoDEvidenceChecks.privacyProblems(REPO)).isEmpty();
        // hashes, the municipality code of the manifest and a DOU number in a URL are not identifiers
        assertThat(problemsOf(tree())).isEmpty();
    }

    @Test
    void anIneInADossierIsRefusedWithoutEchoingIt() throws IOException {
        Tree tree = tree();
        PortaoDEvidenceFixtures.replaceIn(tree.dossierJson(), "\"reason\": \"", "\"reason\": \"team " + INE + " ");

        assertThat(problemsOf(tree))
                .anyMatch(problem -> problem.contains(tree.dossierJson().getFileName() + " exposes an identifier at"))
                .noneMatch(problem -> problem.contains(INE));
    }

    @Test
    void aPersonKeyAUuidAndASecretKeyInADossierAreRefused() throws IOException {
        Tree person = tree();
        PortaoDEvidenceFixtures.replaceIn(person.dossierJson(), "\"coverage\"", "\"cpf\": \"x\", \"coverage\"");
        assertThat(problemsOf(person)).anyMatch(problem -> problem.contains("exposes an identifier at"));

        Path other = Files.createDirectories(workspace.resolve("other"));
        Tree secret = PortaoDEvidenceFixtures.decided(other, new C4Pack().descriptor(), Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(secret.dossierJson(), "\"coverage\"", "\"senha\": \"x\", \"coverage\"");
        assertThat(problemsOf(secret)).anyMatch(problem -> problem.contains("a key named for a password or a token"));
    }

    @Test
    void anIneACnesOrAUuidInAManifestOrASetSummaryIsRefused() throws IOException {
        Tree manifest = tree();
        PortaoDEvidenceFixtures.replaceIn(
                manifest.manifest(), "\"row_count\"", "\"nota\": \"" + INE + "\", \"row_count\"");
        assertThat(problemsOf(manifest))
                .anyMatch(problem -> problem.contains(manifest.manifest().getFileName() + " has an INE"));

        Path second = Files.createDirectories(workspace.resolve("second"));
        Tree uuid = PortaoDEvidenceFixtures.decided(second, new C4Pack().descriptor(), Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(
                uuid.manifest(), "\"row_count\"", "\"nota\": \"123e4567-e89b-12d3-a456-426614174000\", \"row_count\"");
        assertThat(problemsOf(uuid)).anyMatch(problem -> problem.contains("has an INE, a CNES, a UUID or a digest"));

        Path third = Files.createDirectories(workspace.resolve("third"));
        Tree summary = PortaoDEvidenceFixtures.decided(third, new C4Pack().descriptor(), Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(summary.summaryJson(), "\"reason\": \"\"", "\"reason\": \"" + INE + "\"");
        PortaoDEvidenceFixtures.append(summary.summaryMarkdown(), "equipe " + INE + "\n");
        assertThat(problemsOf(summary))
                .anyMatch(problem -> problem.contains(summary.summaryJson().getFileName() + " has an INE"))
                .anyMatch(problem -> problem.contains(summary.summaryMarkdown().getFileName() + " has an INE"));
    }

    @Test
    void anAddressAnEmailAndASecretKeyInAnyOfTheFilesAreRefused() throws IOException {
        Tree address = tree();
        PortaoDEvidenceFixtures.append(address.dossierMarkdown(), "servidor 192.0.2.1\n");
        assertThat(problemsOf(address)).anyMatch(problem -> problem.contains("has an IP address"));

        Path second = Files.createDirectories(workspace.resolve("second"));
        Tree email = PortaoDEvidenceFixtures.decided(second, new C4Pack().descriptor(), Status.PASSED);
        PortaoDEvidenceFixtures.append(email.summaryMarkdown(), "contato: pessoa@example.org.\n");
        assertThat(problemsOf(email)).anyMatch(problem -> problem.contains("has an e-mail address"));

        Path third = Files.createDirectories(workspace.resolve("third"));
        Tree token = PortaoDEvidenceFixtures.decided(third, new C4Pack().descriptor(), Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(token.manifest(), "\"row_count\"", "\"api_token\": \"x\", \"row_count\"");
        assertThat(problemsOf(token)).anyMatch(problem -> problem.contains("a key named for a password or a token"));
    }

    @Test
    void aRuleVersionWithAnAtSignIsNotAnEmailNorAVersionAnAddress() throws IOException {
        Tree tree = tree();

        assertThat(Files.readString(tree.summaryMarkdown()))
                .contains(tree.verdict().ruleVersion());
        assertThat(tree.verdict().ruleVersion()).contains("@");
        assertThat(problemsOf(tree)).isEmpty();
    }
}
