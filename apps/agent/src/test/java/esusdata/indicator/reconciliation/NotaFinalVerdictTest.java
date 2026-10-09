package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import esusdata.indicator.reconciliation.SiapsSnapshot.Row;
import esusdata.indicator.reconciliation.SiapsSnapshot.Team;
import esusdata.indicator.reconciliation.SiapsTeamExportFixtures.Export;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** {@code siaps-nota-final-por-classe@1} end to end on invented data: parser, eligibility, verdict, record. */
class NotaFinalVerdictTest {

    private static final GatePack NOTA_FINAL = GatePack.NOTA_FINAL;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);
    private static final Path REPO = Path.of("..", "..");
    private static final String EVIDENCE = "docs/indicadores/portoes/portao-d-nota-final-siaps.md";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FINGERPRINT = "sha256:" + "ab".repeat(32);

    private static final String FILTRO = """
            {"classificacaoFinalComponente":[
              {"nuQuadrimestre":"2026Q2","coMunicipioIbge":"999999","sgEquipe":"eSF","tipoOrigem":"QUALIDADE",
               "qtdClassificacaoOtimo":3,"qtdClassificacaoBom":5,"qtdClassificacaoSuficiente":2,"qtdClassificacaoRegular":1,
               "totalEquipesValidasParaComponente":11},
              {"nuQuadrimestre":"2026Q2","coMunicipioIbge":"999999","sgEquipe":"eAP","tipoOrigem":"QUALIDADE",
               "qtdClassificacaoOtimo":0,"qtdClassificacaoBom":1,"qtdClassificacaoSuficiente":0,"qtdClassificacaoRegular":0,
               "totalEquipesValidasParaComponente":1},
              {"nuQuadrimestre":"2026Q2","coMunicipioIbge":"999999","sgEquipe":"eSF","tipoOrigem":"CVAT",
               "qtdClassificacaoOtimo":9,"qtdClassificacaoBom":9,"qtdClassificacaoSuficiente":9,"qtdClassificacaoRegular":9,
               "totalEquipesValidasParaComponente":36},
              {"nuQuadrimestre":"2026Q2","coMunicipioIbge":"999999","sgEquipe":"eSB","tipoOrigem":"QUALIDADE",
               "qtdClassificacaoOtimo":8,"qtdClassificacaoBom":8,"qtdClassificacaoSuficiente":8,"qtdClassificacaoRegular":8,
               "totalEquipesValidasParaComponente":32}],
             "conceitoPorIndicadorQualidade":[]}
            """;

    private static final Team EAP_TEAM = new Team("0000000013", "eAP");
    private static final List<Team> TEAMS =
            List.of(new Team("0000000011", "eSF"), new Team("0000000012", "eSF"), EAP_TEAM);

    /** What the public answer says of the three teams above: two eSF in the top classes, one eAP BOM. */
    private static final ClassCounts ESF_COUNTS = new ClassCounts(0, 0, 1, 1);

    private static final ClassCounts EAP_COUNTS = new ClassCounts(0, 0, 1, 0);

    /** The local classes that agree with that answer. */
    private static Map<String, Classification> agreeingLocal() {
        return Map.of(
                "0000000011", Classification.BOM, "0000000012", Classification.OTIMO, "0000000013", Classification.BOM);
    }

    /** A snapshot whose final rows are the given counts and whose seven team lists are {@code lists}. */
    private static SiapsSnapshot snapshot(ClassCounts esf, ClassCounts eap, Map<Integer, List<Team>> lists) {
        return new SiapsSnapshot(
                "999999",
                "2026Q2",
                List.of("2026Q2"),
                List.of(
                        new Row("999999", "2026Q2", GatePack.NOTA_FINAL_CODE, "eSF", esf),
                        new Row("999999", "2026Q2", GatePack.NOTA_FINAL_CODE, "eAP", eap)),
                lists);
    }

    private static Map<Integer, List<Team>> sameListForEvery(List<Team> teams) {
        Map<Integer, List<Team>> lists = new LinkedHashMap<>();
        GatePack.all().forEach(pack -> lists.put(pack.siapsCode(), teams));
        return lists;
    }

    /** The public answer is a diagnostic reference: the team lists that come with it are today's directory. */
    private static PackVerdict evaluate(SiapsSnapshot snapshot, Map<String, Classification> local) {
        return PackVerdict.evaluate(
                NOTA_FINAL,
                ComponentIII.RULE_VERSION,
                ReferencePurpose.DIAGNOSTIC,
                ValidatedReference.fromPublicAggregate(snapshot, NOTA_FINAL),
                new LocalClasses(local, local.keySet()),
                PackVerdict.NO_LOCAL_SOURCE);
    }

    /** The Nota Final reference of an official team export of the invented municipality. */
    private static ValidatedReference official(Export export) {
        return ValidatedReference.from(
                OfficialTeamExportCsvParser.parse(
                        export.bytes(),
                        new OfficialTeamExportCsvParser.ExpectedScope(
                                SiapsTeamExportFixtures.MUNICIPALITY_IBGE, SiapsTeamExportFixtures.QUADRIMESTRE)),
                NOTA_FINAL);
    }

    private static PackVerdict gate(ValidatedReference reference, Map<String, Classification> local) {
        return PackVerdict.evaluate(
                NOTA_FINAL,
                ComponentIII.RULE_VERSION,
                ReferencePurpose.GATE,
                reference,
                new LocalClasses(local, local.keySet()),
                FINGERPRINT);
    }

    @Test
    void readsOnlyTheQualidadeRowsOfEsfAndEapOfTheFinalClassification() {
        List<Row> rows = SiapsParser.finalRows(FILTRO);

        assertThat(rows)
                .containsExactly(
                        new Row("999999", "2026Q2", GatePack.NOTA_FINAL_CODE, "eSF", new ClassCounts(1, 2, 5, 3)),
                        new Row("999999", "2026Q2", GatePack.NOTA_FINAL_CODE, "eAP", new ClassCounts(0, 0, 1, 0)));
    }

    @Test
    void anAnswerWithoutTheListGivesNoRowsAndTwoRowsOfOneTypeAreRefused() {
        assertThat(SiapsParser.finalRows("{\"conceitoPorIndicadorQualidade\":[]}"))
                .isEmpty();
        String twice = FILTRO.replace("\"sgEquipe\":\"eAP\"", "\"sgEquipe\":\"eSF\"");
        assertThatThrownBy(() -> SiapsParser.finalRows(twice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two QUALIDADE rows");
        assertThatThrownBy(() -> SiapsParser.finalRows("{\"classificacaoFinalComponente\":{}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSnapshotFileCarriesTheFinalRowsBesideThePackRows() {
        String file = "{\"competencias\":[{\"nuCompetencia\":\"2026Q2\",\"quadrimestre\":true}],"
                + "\"filtro\":"
                + FILTRO.replace(
                        "\"conceitoPorIndicadorQualidade\":[]",
                        "\"conceitoPorIndicadorQualidade\":["
                                + "{\"nuQuadrimestre\":\"2026Q2\",\"coMunicipioIbge\":\"999999\",\"sgEquipe\":\"eSF\","
                                + "\"coTipoIndicador\":110,"
                                + "\"qtdClassificacaoOtimo\":1,\"qtdClassificacaoBom\":0,\"qtdClassificacaoSuficiente\":0,"
                                + "\"qtdClassificacaoRegular\":0}]")
                + ",\"equipes\":{}}";

        SiapsSnapshot snapshot = SiapsParser.snapshot(file);

        assertThat(snapshot.hasFinalRows()).isTrue();
        assertThat(snapshot.counts(GatePack.NOTA_FINAL_CODE, "eSF")).contains(new ClassCounts(1, 2, 5, 3));
        assertThat(snapshot.counts(110, "eSF")).contains(new ClassCounts(0, 0, 0, 1));
        assertThat(SiapsParser.snapshot(file.replace("classificacaoFinalComponente", "outra"))
                        .hasFinalRows())
                .isFalse();
    }

    @Test
    void theEligibilityIsThatOfTheLatestOfTheSevenFichasAndOnlyTheLatestPublishedIsTheReference() {
        assertThat(NOTA_FINAL.floor()).isEqualTo(LocalDate.of(2026, 6, 24));
        assertThat(GatePack.all())
                .allSatisfy(pack -> assertThat(NOTA_FINAL.floor()).isAfterOrEqualTo(pack.floor()));
        assertThat(Eligibility.firstEligible(NOTA_FINAL)).isEqualTo(new Quadrimestre(2026, 2));
        assertThat(GatePack.all()).doesNotContain(NOTA_FINAL);

        assertThat(Eligibility.reference(NOTA_FINAL, List.of("2026Q1"))).isEmpty();
        assertThat(Eligibility.waitingFor(NOTA_FINAL)).isEqualTo("aguardando 2026Q2 no SIAPS");
        List<String> published = List.of("2026Q1", "2026Q2", "2026Q3");
        assertThat(Eligibility.reference(NOTA_FINAL, published)).contains(new Quadrimestre(2026, 3));
        assertThat(Eligibility.isReference(NOTA_FINAL, new Quadrimestre(2026, 3), published))
                .isTrue();
        assertThat(Eligibility.isReference(NOTA_FINAL, new Quadrimestre(2026, 2), published))
                .as("eligible, but not the most recent published")
                .isFalse();
    }

    @Test
    void passesWhenTheDistributionsAgreeWithinTheThresholdPerTeamType() {
        SiapsSnapshot snapshot = snapshot(ESF_COUNTS, EAP_COUNTS, sameListForEvery(TEAMS));

        PackVerdict verdict = evaluate(snapshot, agreeingLocal());

        assertThat(verdict.status()).isEqualTo(Status.PASSED);
        assertThat(verdict.isGateEvidence())
                .as("a public answer is a diagnostic")
                .isFalse();
        assertThat(verdict.rows()).extracting(Comparison.RowResult::distance).containsExactly(0, 0);
        assertThat(verdict.rows().get(1).evaluated())
                .as("eAP: one team on each side")
                .isTrue();
    }

    @Test
    void failsWhenOneTypeIsOverTheThresholdAndAnUnclassifiedTeamIsReportedNotCounted() {
        SiapsSnapshot snapshot = snapshot(ESF_COUNTS, EAP_COUNTS, sameListForEvery(TEAMS));

        PackVerdict verdict = evaluate(snapshot, Map.of("0000000011", Classification.REGULAR));

        // cum: SIAPS 0,0,1,2 against local 1,1,1,1: D = 1 + 1 + 0 + 1 = 3 > T = 2
        assertThat(verdict.status()).isEqualTo(Status.FAILED);
        assertThat(verdict.rows().getFirst().semClasseLocal()).isEqualTo(1);
        assertThat(verdict.rows().getFirst().distance()).isEqualTo(3);
        assertThat(verdict.rows().getFirst().threshold()).isEqualTo(2);
    }

    @Test
    void theDiagnosticSplitKeyIsTheTeamsOfAllSevenCurrentLists() {
        Map<Integer, List<Team>> lists = sameListForEvery(TEAMS);
        lists.put(GatePack.all().get(3).siapsCode(), List.of(new Team("0000000011", "eSF")));

        SiapsSnapshot snapshot = snapshot(ESF_COUNTS, EAP_COUNTS, lists);

        assertThat(snapshot.notaFinalTeams()).contains(List.of(new Team("0000000011", "eSF")));
        PackVerdict verdict =
                evaluate(snapshot, Map.of("0000000011", Classification.BOM, "0000000012", Classification.OTIMO));
        assertThat(verdict.localNotInSiaps())
                .as("the team missing from one current list is outside")
                .isEqualTo(1);
        assertThat(verdict.rows().getFirst().localTeams()).isEqualTo(1);
    }

    @Test
    void aTeamListedWithAnotherTypeInOneListIsOutsideTheSet() {
        Map<Integer, List<Team>> lists = sameListForEvery(TEAMS);
        lists.put(GatePack.all().get(1).siapsCode(), List.of(new Team("0000000011", "eAP"), TEAMS.get(1)));

        assertThat(snapshot(ClassCounts.EMPTY, ClassCounts.EMPTY, lists).notaFinalTeams())
                .contains(List.of(TEAMS.get(1)));
    }

    @Test
    void isPendingWithoutTheFinalRowsOrWithoutOneOfTheSevenLists() {
        SiapsSnapshot noFinalRows =
                new SiapsSnapshot("999999", "2026Q2", List.of("2026Q2"), List.of(), sameListForEvery(TEAMS));
        Map<Integer, List<Team>> sixLists = sameListForEvery(TEAMS);
        sixLists.remove(GatePack.all().getLast().siapsCode());

        PackVerdict first = evaluate(noFinalRows, Map.of("0000000011", Classification.BOM));
        PackVerdict second =
                evaluate(snapshot(ESF_COUNTS, EAP_COUNTS, sixLists), Map.of("0000000011", Classification.BOM));

        assertThat(first.status()).isEqualTo(Status.PENDING);
        assertThat(first.reason()).contains("classificação final");
        assertThat(second.status()).isEqualTo(Status.PENDING);
        assertThat(second.reason()).contains("listas de equipes");
        assertThat(first.isGateEvidence()).isFalse();
    }

    @Test
    void aRowOfZerosOfTheFinalClassificationIsAGapNotAStatedZero() {
        // the public answer cannot prove that no eAP team was counted: only a complete team export can
        SiapsSnapshot snapshot = snapshot(ESF_COUNTS, ClassCounts.EMPTY, sameListForEvery(TEAMS));

        PackVerdict verdict = evaluate(snapshot, agreeingLocal());

        assertThat(verdict.status()).isEqualTo(Status.PENDING);
        assertThat(verdict.reason()).contains("referência incompleta", "linha de eAP de CIII", "só de zeros");
        assertThat(verdict.rows()).isEmpty();
    }

    @Test
    void notaFinalRequiresEveryOfficialTypeInItsUniverse() {
        Map<String, Classification> local = Map.of(
                SiapsTeamExportFixtures.ESF_1, Classification.BOM,
                SiapsTeamExportFixtures.ESF_2, Classification.OTIMO,
                SiapsTeamExportFixtures.EAP_1, Classification.BOM);
        // every eSF and eAP team of the export has its Total row: that is the universe, by type
        PackVerdict complete = gate(official(SiapsTeamExportFixtures.standard()), local);
        // the eAP team is in the export (its seven indicators are there) but has no Total row
        Export noTotal = SiapsTeamExportFixtures.standard()
                .dropRow(SiapsTeamExportFixtures.EAP_1, SiapsTeamExportFixtures.TOTAL_NAME);
        PackVerdict incomplete = gate(official(noTotal), local);

        assertThat(complete.status()).isEqualTo(Status.PASSED);
        assertThat(complete.isGateEvidence()).isTrue();
        assertThat(complete.rows()).extracting(Comparison.RowResult::siapsTeams).containsExactly(2, 1);
        assertThat(complete.localNotInSiaps()).isZero();
        // the eSF row alone would pass; the Nota Final waits for the eAP team's Total row
        assertThat(incomplete.status()).isEqualTo(Status.PENDING);
        assertThat(incomplete.reason()).contains("referência incompleta", "Total (nota final)");
        assertThat(incomplete.isGateEvidence()).isFalse();
    }

    @Test
    void anAggregateOnlyNotaFinalCannotBecomeGateEvidence() {
        SiapsSnapshot snapshot = snapshot(ESF_COUNTS, EAP_COUNTS, sameListForEvery(TEAMS));
        Map<String, Classification> local = agreeingLocal();

        PackVerdict verdict = PackVerdict.evaluate(
                NOTA_FINAL,
                ComponentIII.RULE_VERSION,
                ReferencePurpose.GATE,
                ValidatedReference.fromPublicAggregate(snapshot, NOTA_FINAL),
                new LocalClasses(local, local.keySet()),
                FINGERPRINT);

        // the intersection of the seven current lists is no historical universe, however well it agrees
        assertThat(verdict.status()).isEqualTo(Status.PENDING);
        assertThat(verdict.reason()).contains("universo histórico");
        assertThat(verdict.isGateEvidence()).isFalse();
        assertThat(evaluate(snapshot, local).status()).isEqualTo(Status.PASSED);
    }

    @Test
    void theSummaryNamesTheNotaFinalCheckAndRuleAndMasksTheCounts() {
        PackVerdict verdict = evaluate(snapshot(ESF_COUNTS, EAP_COUNTS, sameListForEvery(TEAMS)), agreeingLocal());

        String text = SummaryWriter.render(verdict, DAY);

        assertThat(text)
                .contains("`siaps-nota-final-por-classe@1`")
                .contains("`" + EVIDENCE + "`")
                .contains(ComponentIII.RULE_VERSION)
                .contains("| CIII | eSF | <10 | <10 |")
                .doesNotContain("siaps-distribuicao-por-classe@1");
        assertThat(SummaryWriter.fileName(verdict))
                .isEqualTo("diagnostico-portao-d-componente-iii-nota-final-2026Q2.md");
        PackVerdict gate = gate(
                official(SiapsTeamExportFixtures.standard()),
                Map.of(
                        SiapsTeamExportFixtures.ESF_1, Classification.BOM,
                        SiapsTeamExportFixtures.ESF_2, Classification.OTIMO,
                        SiapsTeamExportFixtures.EAP_1, Classification.BOM));
        assertThat(SummaryWriter.fileName(gate)).isEqualTo("portao-d-componente-iii-nota-final-2026Q1.md");
        assertThat(SummaryWriter.render(gate, DAY)).contains("- Fingerprint da fonte local: `" + FINGERPRINT + "`");
    }

    @Test
    void theRegistryRecordsTheNotaFinalCheckInItsOwnEntry(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("release-gates.json");
        Files.copy(REPO.resolve("contracts/indicators/release-gates.json"), file);
        String sha = SummaryWriter.sha256(REPO.resolve(EVIDENCE));
        PackVerdict passed = new PackVerdict(
                NOTA_FINAL,
                ComponentIII.RULE_VERSION,
                ReferencePurpose.GATE,
                Status.PASSED,
                "",
                "2026Q2",
                List.of(),
                0,
                new ArrayList<>(),
                FINGERPRINT);

        RegistryUpdater.record(file, REPO, passed, DAY, EVIDENCE, sha);

        GateCheck d = ReleaseGateRegistry.fromJson(Files.readString(file))
                .statusOf(ComponentIII.DESCRIPTOR)
                .check(GateId.D);
        assertThat(d.isPassed()).isTrue();
        assertThat(d.check()).isEqualTo("siaps-nota-final-por-classe@1");
        // every other entry is untouched: the seven packs keep their own D
        JsonNode before = MAPPER.readTree(Files.readString(REPO.resolve("contracts/indicators/release-gates.json")))
                .path("packs");
        JsonNode after = MAPPER.readTree(Files.readString(file)).path("packs");
        assertThat(after.size()).isEqualTo(before.size());
        for (int i = 0; i < before.size(); i++) {
            if (!ComponentIII.ID.equals(before.get(i).path("pack").asString())) {
                assertThat(after.get(i)).isEqualTo(before.get(i));
            }
        }
    }
}
