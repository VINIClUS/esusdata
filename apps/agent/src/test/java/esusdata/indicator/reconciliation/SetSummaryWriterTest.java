package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import esusdata.indicator.reconciliation.PackVerdict.TeamLine;
import esusdata.indicator.reconciliation.PortaoDEvidenceFixtures.Tree;
import esusdata.indicator.reconciliation.SummaryWriter.WrittenSetSummary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The summary of a reference set: JSON is the evidence, Markdown is rendered from it. */
class SetSummaryWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PackDescriptor DESCRIPTOR = new C4Pack().descriptor();
    private static final String INE = "0000000011";

    @TempDir
    Path workspace;

    private static JsonNode json(Tree tree) throws Exception {
        return MAPPER.readTree(Files.readString(tree.summaryJson(), StandardCharsets.UTF_8));
    }

    @Test
    void theJsonCarriesTheSetTheEvidenceOfEachReferenceAndTheDossiersFieldComparisonAsIs() throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, DESCRIPTOR, Status.PASSED);

        JsonNode summary = json(tree);
        JsonNode reference = summary.path("references").get(0);
        JsonNode dossier = MAPPER.readTree(Files.readString(tree.dossierJson(), StandardCharsets.UTF_8));

        assertThat(summary.path("schema_version").asString()).isEqualTo("1");
        assertThat(summary.path("pack").asString()).isEqualTo(DESCRIPTOR.id());
        assertThat(summary.path("rule_version").asString()).isEqualTo(DESCRIPTOR.ruleVersion());
        assertThat(summary.path("check").asString()).isEqualTo("siaps-distribuicao-por-classe@2");
        assertThat(summary.path("gate_set_sha256").asString())
                .isEqualTo(tree.verdict().gateSetSha256());
        assertThat(summary.path("status").asString()).isEqualTo("PASSED");
        assertThat(summary.path("checked_at").asString()).isEqualTo("2026-10-09");
        assertThat(reference.path("reference_id").asString()).isEqualTo(tree.referenceId());
        assertThat(reference.path("reference_manifest_ref").asString())
                .isEqualTo(ReferencePolicy.manifestPath(tree.referenceId()));
        assertThat(reference.path("reference_manifest_sha256").asString())
                .isEqualTo(SummaryWriter.sha256(tree.manifest()));
        assertThat(reference.path("compatibility_evidence_ref").asString())
                .isEqualTo(ReferencePolicy.dossierPath(tree.referenceId(), DESCRIPTOR.id()));
        assertThat(reference.path("compatibility_evidence_sha256").asString())
                .isEqualTo(SummaryWriter.sha256(tree.dossierJson()));
        assertThat(reference.path("compatibility").asString()).isEqualTo("EXACT");
        assertThat(reference.path("local_source_fingerprint").asString())
                .isEqualTo(PortaoDEvidenceFixtures.FINGERPRINT);
        assertThat(reference.path("official_field_comparison")).isEqualTo(dossier.path("official_field_comparison"));
        assertThat(reference.path("official_field_comparison").has("max_abs_score_difference"))
                .isTrue();
        JsonNode esf = reference.path("rows").get(0);
        assertThat(esf.path("team_type").asString()).isEqualTo(SiapsParser.ESF);
        // the two eSF teams of the export, below the mask
        assertThat(esf.path("n_s").asString()).isEqualTo("<10");
        assertThat(esf.path("d").asString()).isEqualTo("0");
        assertThat(esf.path("t").asString()).isEqualTo("2");
        assertThat(esf.path("sem_classe_local").asString()).isEqualTo("<10");
    }

    @Test
    void theMarkdownIsTheRenderingOfTheJsonBytesAndTheBytesAreDeterministic() throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, DESCRIPTOR, Status.FAILED);
        byte[] bytes = Files.readAllBytes(tree.summaryJson());

        assertThat(Files.readString(tree.summaryMarkdown(), StandardCharsets.UTF_8))
                .isEqualTo(SummaryWriter.renderSetMarkdown(bytes))
                .contains("gate_set_sha256", "**FAILED**", tree.referenceId(), "| Tipo | N_S |");
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .doesNotContain("\r")
                .endsWith("}\n");

        WrittenSetSummary again =
                SummaryWriter.writeSet(workspace.resolve("again"), tree.verdict(), PortaoDEvidenceFixtures.DAY);
        assertThat(Files.readAllBytes(again.json())).isEqualTo(bytes);
        assertThat(again.jsonSha256()).isEqualTo(SummaryWriter.sha256(bytes));
        assertThat(Files.readAllBytes(again.markdown())).isEqualTo(Files.readAllBytes(tree.summaryMarkdown()));
    }

    @Test
    void thePerTeamLinesOfTheLocalVerdictNeverReachTheSummary() throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, DESCRIPTOR, Status.PASSED);
        ReferenceSet set = tree.loadedPolicy().referenceSet(DESCRIPTOR.id(), DESCRIPTOR.ruleVersion());
        DossierEvidence dossier = DossierEvidence.read(
                        set.references().getFirst().compatibilityEvidenceRef(), Files.readAllBytes(tree.dossierJson()))
                .orElseThrow();
        ClassCounts counts = new ClassCounts(10, 0, 0, 0);
        PackVerdict withTeams = new PackVerdict(
                GatePack.byPackId(DESCRIPTOR.id()).orElseThrow(),
                DESCRIPTOR.ruleVersion(),
                ReferencePurpose.GATE,
                Status.PASSED,
                "",
                PortaoDEvidenceFixtures.QUADRIMESTRE,
                List.of(Comparison.row(SiapsParser.ESF, counts, counts, 0)),
                0,
                List.of(new TeamLine(INE, SiapsParser.ESF, Classification.OTIMO, "uma equipe")),
                PortaoDEvidenceFixtures.FINGERPRINT);
        ReferenceSetVerdict verdict = ReferenceSetVerdict.aggregate(
                set, Map.of(tree.referenceId(), withTeams), Map.of(tree.referenceId(), dossier));

        byte[] bytes = SummaryWriter.summaryJson(verdict, PortaoDEvidenceFixtures.DAY);

        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain(INE, "OTIMO", "uma equipe");
        assertThat(SummaryWriter.renderSetMarkdown(bytes)).doesNotContain(INE, "OTIMO", "uma equipe");
    }

    @Test
    void aPendingReferenceShowsItsReasonAndNoFigures() throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, DESCRIPTOR, Status.PASSED);
        ReferenceSet set = tree.loadedPolicy().referenceSet(DESCRIPTOR.id(), DESCRIPTOR.ruleVersion());
        ReferenceSetVerdict pending = ReferenceSetVerdict.aggregate(set, Map.of(), Map.of());

        byte[] bytes = SummaryWriter.summaryJson(pending, PortaoDEvidenceFixtures.DAY);
        JsonNode reference = MAPPER.readTree(bytes).path("references").get(0);

        assertThat(reference.path("status").asString()).isEqualTo("PENDING");
        assertThat(reference.path("reason").asString()).contains("no local verdict", "no dossier");
        assertThat(reference.path("rows")).isEmpty();
        assertThat(reference.has("official_field_comparison")).isFalse();
        assertThat(SummaryWriter.renderSetMarkdown(bytes)).contains("Sem linhas avaliadas.");
    }
}
