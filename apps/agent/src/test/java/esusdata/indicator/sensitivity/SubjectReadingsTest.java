package esusdata.indicator.sensitivity;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.sensitivity.SubjectReadings.Tally;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Aggregation, frequencies, cohort readings and bounds over synthetic subjects. */
class SubjectReadingsTest {

    private static final String INE_A = "0000000001";
    private static final String INE_B = "0000000002";
    private static final String COHORT = "AMB-C2-03";
    private static final String PRACTICE = "AMB-C2-09";
    private static final String OTHER = "AMB-C2-10";
    private static final String PACK = "pack";

    private int next;

    private SubjectScore subject(
            String ine, long points, SortedSet<String> cohort, SortedMap<String, SubjectScore.OpenPractice> open) {
        return new SubjectScore("k" + next++, ine, true, BigInteger.valueOf(points), cohort, open);
    }

    private SubjectScore plain(String ine, long points) {
        return subject(ine, points, new TreeSet<>(), new TreeMap<>());
    }

    private SubjectScore completingTwo(String ine, long points) {
        return subject(ine, points, new TreeSet<>(List.of(COHORT)), new TreeMap<>());
    }

    private SubjectScore open(String ine, long points, long weight, String... codes) {
        SortedMap<String, SubjectScore.OpenPractice> open = new TreeMap<>();
        open.put("E", new SubjectScore.OpenPractice(BigInteger.valueOf(weight), new TreeSet<>(List.of(codes))));
        return subject(ine, points, new TreeSet<>(), open);
    }

    private static ReadingRow find(List<ReadingRow> rows, String code, String readingStart, String unit) {
        return rows.stream()
                .filter(r -> r.code().equals(code)
                        && r.reading().startsWith(readingStart)
                        && r.unit().equals(unit))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void unitsAreTheMunicipalityThenEachIneAndSubjectsWithoutTeamOnlyCountInTheFirst() {
        List<SubjectScore> subjects = new ArrayList<>(List.of(plain(INE_B, 20), plain(INE_A, 40), plain(INE_A, 0)));
        subjects.add(plain(null, 100));

        Map<String, List<SubjectScore>> units = SubjectReadings.byUnit(subjects);

        assertThat(units.keySet()).containsExactly(ReadingRow.MUNICIPALITY, INE_A, INE_B);
        assertThat(Tally.of(units.get(ReadingRow.MUNICIPALITY))).isEqualTo(new Tally(4, 0, BigInteger.valueOf(160)));
        Tally teamA = Tally.of(units.get(INE_A));
        assertThat(teamA.denominator()).isEqualTo(2);
        assertThat(teamA.mean()).isEqualTo("20.0000");
    }

    @Test
    void anAmbiguousSubjectIsCountedInTheDenominatorAndKeepsItsCertainPoints() {
        Tally tally = Tally.of(List.of(plain(INE_A, 40), open(INE_A, 20, 20, PRACTICE)));

        assertThat(tally.denominator()).isEqualTo(2);
        assertThat(tally.ambiguous()).isEqualTo(1);
        assertThat(tally.points()).isEqualTo(BigInteger.valueOf(60));
        assertThat(Tally.of(List.of()).mean()).isNull();
    }

    @Test
    void frequenciesCountTheSubjectsThatDependOnEachCodePerUnit() {
        List<SubjectScore> subjects = List.of(
                completingTwo(INE_A, 0), completingTwo(INE_B, 0), open(INE_A, 0, 20, PRACTICE, OTHER), plain(INE_A, 0));

        List<ReadingRow> rows = SubjectReadings.frequencies(PACK, subjects);

        assertThat(find(rows, COHORT, ReadingRow.FREQUENCY, ReadingRow.MUNICIPALITY)
                        .affected())
                .isEqualTo(2);
        assertThat(find(rows, COHORT, ReadingRow.FREQUENCY, INE_A).affected()).isEqualTo(1);
        assertThat(find(rows, PRACTICE, ReadingRow.FREQUENCY, ReadingRow.MUNICIPALITY)
                        .affected())
                .isEqualTo(1);
        assertThat(find(rows, OTHER, ReadingRow.FREQUENCY, ReadingRow.MUNICIPALITY)
                        .affected())
                .isEqualTo(1);
    }

    @Test
    void eachCohortReadingChangesTheDenominatorOrTheAmbiguityAsExpected() {
        List<SubjectScore> subjects =
                List.of(plain(INE_A, 20), plain(INE_A, 20), completingTwo(INE_A, 100), completingTwo(INE_A, 100));

        List<ReadingRow> rows = SubjectReadings.cohortReadings(PACK, subjects);

        ReadingRow production = find(rows, COHORT, "incluir e marcar", ReadingRow.MUNICIPALITY);
        assertThat(production.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(production.numerator()).isEqualTo(BigInteger.valueOf(240));
        assertThat(production.remaining()).isEqualTo(2);
        assertThat(production.affected()).isEqualTo(2);

        ReadingRow unmarked = find(rows, COHORT, "incluir sem marcar", ReadingRow.MUNICIPALITY);
        assertThat(unmarked.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(unmarked.remaining()).isZero();
        assertThat(unmarked.value()).isEqualTo("60.0000");

        ReadingRow excluded = find(rows, COHORT, "excluir", ReadingRow.MUNICIPALITY);
        assertThat(excluded.denominator()).isEqualTo(BigInteger.valueOf(2));
        assertThat(excluded.numerator()).isEqualTo(BigInteger.valueOf(40));
        assertThat(excluded.value()).isEqualTo("20.0000");
        assertThat(excluded.remaining()).isZero();
    }

    @Test
    void practiceBoundsDecideThePracticesOfTheCodeAndLeaveTheOtherAmbiguitiesOpen() {
        List<SubjectScore> subjects =
                List.of(plain(INE_A, 0), open(INE_A, 0, 20, PRACTICE), open(INE_A, 0, 20, PRACTICE, OTHER));

        List<ReadingRow> rows = SubjectReadings.practiceBounds(PACK, subjects);

        ReadingRow lower = find(rows, PRACTICE, "limite inferior", ReadingRow.MUNICIPALITY);
        ReadingRow upper = find(rows, PRACTICE, "limite superior", ReadingRow.MUNICIPALITY);
        assertThat(lower.numerator()).isZero();
        assertThat(upper.numerator()).isEqualTo(BigInteger.valueOf(40));
        assertThat(lower.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(upper.value()).isEqualTo("13.3333");
        assertThat(lower.affected()).isEqualTo(2);
        // the {PRACTICE, OTHER} practice is the same practice: it is decided with the code, so none remains
        assertThat(lower.remaining()).isZero();
        assertThat(find(rows, OTHER, "limite superior", ReadingRow.MUNICIPALITY).numerator())
                .isEqualTo(BigInteger.valueOf(20));
    }

    @Test
    void evidenceIsReadBackIntoSubjectsWithTheirCodesAndPoints() {
        List<EvidenceItem> evidence = List.of(
                row("c1", null, EvidenceDecision.ELIGIBLE, "COORTE_COMPLETA_2_ANOS_NA_COMPETENCIA", null, INE_A),
                row("c1", "A", EvidenceDecision.PRACTICE_MET, "CUMPRIDA", BigInteger.valueOf(20), INE_A),
                row("c1", "B", EvidenceDecision.PRACTICE_NOT_MET, "NAO_CUMPRIDA", BigInteger.ZERO, INE_A),
                row("c1", "E", EvidenceDecision.PRACTICE_AMBIGUOUS, "AMBIGUIDADE:AMB-C2-09,AMB-C2-10", null, INE_A),
                row("c1", "E", EvidenceDecision.SUPPORTING_EVENT, null, null, INE_A),
                row("c2", null, EvidenceDecision.EXCLUDED, "EXCLUIDO_SEM_VINCULO", null, null),
                row("e3", null, EvidenceDecision.EXCLUDED, "AMBIGUIDADE_AMB_C3_03", null, INE_B));

        List<SubjectScore> subjects =
                EvidenceSubjects.of(evidence, Map.of("A", BigInteger.valueOf(20), "E", BigInteger.valueOf(20)));

        assertThat(subjects).hasSize(2);
        SubjectScore child = subjects.getFirst();
        assertThat(child.ine()).isEqualTo(INE_A);
        assertThat(child.eligible()).isTrue();
        assertThat(child.certainPoints()).isEqualTo(BigInteger.valueOf(20));
        assertThat(child.openPractices().get("E").codes()).containsExactly("AMB-C2-09", "AMB-C2-10");
        assertThat(child.openPractices().get("E").weight()).isEqualTo(BigInteger.valueOf(20));
        SubjectScore episode = subjects.get(1);
        assertThat(episode.eligible()).isFalse();
        assertThat(episode.cohortCodes()).containsExactly("AMB-C3-03");
        assertThat(episode.ambiguous()).isTrue();
    }

    @Test
    void codesAreNormalizedFromEveryReasonShape() {
        assertThat(EvidenceSubjects.codesIn("AMBIGUIDADE_AMB_C3_02")).containsExactly("AMB-C3-02");
        assertThat(EvidenceSubjects.codesIn("AMB-C4-01_PRATICA_D_EAP76")).containsExactly("AMB-C4-01");
        assertThat(EvidenceSubjects.codesIn("DADO_INDISPONIVEL:LACUNA-L3")).containsExactly("LACUNA-L3");
        assertThat(EvidenceSubjects.codesIn("C_AMBIGUA_EAP76_AMB_C6_01")).containsExactly("AMB-C6-01");
        assertThat(EvidenceSubjects.codesIn("CUMPRIDA")).isEmpty();
        assertThat(EvidenceSubjects.codesIn(null)).isEmpty();
    }

    @Test
    void aSubjectNeverPrintsItsKey() {
        assertThat(plain(INE_A, 0).toString()).doesNotContain("k0").contains(INE_A);
    }

    private static EvidenceItem row(
            String key, String component, EvidenceDecision decision, String reason, BigInteger points, String ine) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                key,
                null,
                "2026-03-31",
                component,
                decision,
                reason,
                points,
                null,
                ine,
                null,
                null);
    }
}
