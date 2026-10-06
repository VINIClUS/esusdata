package esusdata.indicator.pack.c6;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Synthetic C6 scenarios (docs/metodologia/c6-cuidado-pessoa-idosa.md, "Casos de teste derivados"):
 * base competência 2026-03, a 70-year-old person linked to a team, and one builder per practice
 * source, plus lookups over the observable contract (result, teams and evidence).
 */
final class C6Scenario {

    static final String IBGE = CanonicalFixtures.IBGE;
    static final String OTHER_IBGE = "3550308";
    static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    static final String CNES = "2750325";
    static final String INE_A = "0000000001";
    static final String INE_B = "0000000002";
    static final LocalDate BORN_70 = LocalDate.of(1956, 3, 10);
    static final LocalDate LINKED_ON = LocalDate.of(2020, 1, 10);

    static final String CBO_MEDICO = "225142";
    static final String CBO_ENFERMEIRO = "223505";
    static final String CBO_NUTRICIONISTA = "223710";
    static final String CBO_ACS = "515105";
    static final String CBO_TACS = "322255";
    static final String VISIT_REASON = "PESSOA_IDOSA";
    static final String FLU_TRIVALENTE = "33";
    static final String FLU_TETRAVALENTE = "77";

    static final String EXIT_MUDANCA_TERRITORIO = "136";
    static final String EXIT_OBITO = "135";

    private final CanonicalDataset.Builder builder = CanonicalDataset.builder();
    private final java.util.Set<String> linkedIne = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> teamsGiven = new java.util.HashSet<>();

    private C6Scenario() {}

    static C6Scenario scenario() {
        return new C6Scenario();
    }

    static EvaluationContext context(YearMonth competencia) {
        return EvaluationContext.endOfMonth(IBGE, competencia);
    }

    // ---- records -------------------------------------------------------------------------------

    C6Scenario add(Record canonicalRecord) {
        builder.add(canonicalRecord);
        if (canonicalRecord instanceof CanonicalRegistration r && r.ine() != null) {
            linkedIne.add(r.ine());
        } else if (canonicalRecord instanceof CanonicalTeam t) {
            teamsGiven.add(t.ine());
        }
        return this;
    }

    /** A person born {@link #BORN_70}, linked to {@link #INE_A} since {@link #LINKED_ON}. */
    C6Scenario elder(String key) {
        return elder(key, INE_A);
    }

    C6Scenario elder(String key, String ine) {
        return person(key, BORN_70).linked(key, ine);
    }

    C6Scenario person(String key, LocalDate birth) {
        return add(CanonicalFixtures.person(key, birth, "FEMININO"));
    }

    C6Scenario linked(String key, String ine) {
        return add(registration(key, LINKED_ON, ine));
    }

    C6Scenario team(String ine, String teamTypeCode) {
        return add(team(ine, teamTypeCode, "2020-01-01"));
    }

    /** Practice A: a presential individual encounter by a physician. */
    C6Scenario consult(String key, LocalDate date) {
        return add(CanonicalFixtures.encounter(key, date, CBO_MEDICO, false));
    }

    /** Practice B only: weight and height the same day, by a nutritionist (not an A professional). */
    C6Scenario anthropometry(String key, LocalDate date) {
        return add(CanonicalFixtures.encounterWithMeasures(key, date, CBO_NUTRICIONISTA, "70.5", "165.0", null, null));
    }

    /** Practice C: a home visit by an ACS with the visit reason filled. */
    C6Scenario visit(String key, LocalDate date) {
        return add(homeVisit(key, date, CBO_ACS, List.of(VISIT_REASON), null, null));
    }

    /** Practice D: an influenza dose (code 33) applied on {@code date}. */
    C6Scenario fluDose(String key, LocalDate date) {
        return add(immunization(key, date, FLU_TRIVALENTE, false, null));
    }

    C6Scenario practiceA(String key) {
        return consult(key, LocalDate.of(2026, 1, 15));
    }

    C6Scenario practiceB(String key) {
        return anthropometry(key, LocalDate.of(2025, 11, 20));
    }

    C6Scenario practiceC(String key) {
        return visit(key, LocalDate.of(2025, 10, 1)).visit(key, LocalDate.of(2026, 1, 15));
    }

    C6Scenario practiceD(String key) {
        return fluDose(key, LocalDate.of(2025, 5, 10));
    }

    C6Scenario allPractices(String key) {
        return practiceA(key).practiceB(key).practiceC(key).practiceD(key);
    }

    /**
     * Every INE a registration names and no test gave a team for is an eSF 70 team, as the source
     * would have it: a team without a type is left out of the cohort (C6-D2).
     */
    CanonicalDataset build() {
        linkedIne.forEach(ine -> {
            if (teamsGiven.add(ine)) {
                builder.add(team(ine, "70", null));
            }
        });
        return builder.build();
    }

    /** The ungated result ({@code C6Pack.compute}) for the base competência. */
    RuleOutcome compute() {
        return compute(COMPETENCIA);
    }

    RuleOutcome compute(YearMonth competencia) {
        return C6Pack.compute(build(), context(competencia));
    }

    /** The gated result ({@code new C6Pack().evaluate}) for the base competência. */
    RuleOutcome evaluate() {
        C6Pack pack = new C6Pack();
        return GateFixtures.published(pack.descriptor(), pack.evaluate(build(), context(COMPETENCIA)));
    }

    static CanonicalRegistration registration(String key, LocalDate date, String ine) {
        return registration(key, date, ine, false, false, false, null);
    }

    static CanonicalRegistration registration(
            String key,
            LocalDate date,
            String ine,
            boolean simplified,
            boolean inactive,
            boolean refused,
            String exitReason) {
        return new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                key,
                date.toString(),
                CNES,
                ine,
                simplified,
                inactive,
                refused,
                exitReason,
                null,
                null,
                null);
    }

    static CanonicalPerson personWithDeath(String key, LocalDate birth, LocalDate death) {
        return new CanonicalPerson(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                key,
                birth == null ? null : birth.toString(),
                "MASCULINO",
                null,
                death == null ? null : death.toString());
    }

    static CanonicalHomeVisit homeVisit(
            String key, LocalDate date, String cbo, List<String> reasons, String weightKg, String heightCm) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                cbo,
                CNES,
                INE_A,
                "1",
                reasons,
                weightKg,
                heightCm);
    }

    static CanonicalImmunization immunization(
            String key, LocalDate date, String code, boolean transcription, String cbo) {
        return new CanonicalImmunization(
                CanonicalFixtures.ref("tb_fat_vacinacao_vacina"),
                IBGE,
                key,
                date.toString(),
                code,
                "1",
                null,
                transcription,
                cbo,
                CNES,
                INE_A);
    }

    /** One state of a team's type, valid from {@code from} (inclusive) until {@code to} (exclusive, null = open). */
    static CanonicalTeam teamState(String ine, String teamTypeCode, String from, String to) {
        return CanonicalFixtures.teamState(ine, CNES, teamTypeCode, from, to);
    }

    static CanonicalTeam team(String ine, String teamTypeCode, String observedAt) {
        return new CanonicalTeam(CanonicalFixtures.ref("tb_dim_equipe"), IBGE, ine, CNES, teamTypeCode, observedAt);
    }

    // ---- lookups over the contract -------------------------------------------------------------

    /** The single ELIGIBLE or EXCLUDED row of {@code key}. */
    static EvidenceItem subjectRow(RuleOutcome outcome, String key) {
        List<EvidenceItem> rows = outcome.evidence().stream()
                .filter(e -> key.equals(e.subjectKey()))
                .filter(e -> e.decision() == EvidenceDecision.ELIGIBLE || e.decision() == EvidenceDecision.EXCLUDED)
                .toList();
        assertThat(rows).as("subject row of %s", key).hasSize(1);
        return rows.get(0);
    }

    /** The single PRACTICE_MET or PRACTICE_NOT_MET row of {@code key} for practice {@code code}. */
    static EvidenceItem practiceRow(RuleOutcome outcome, String key, String code) {
        List<EvidenceItem> rows = outcome.evidence().stream()
                .filter(e -> key.equals(e.subjectKey()))
                .filter(e -> code.equals(e.component()))
                .filter(e -> e.decision() == EvidenceDecision.PRACTICE_MET
                        || e.decision() == EvidenceDecision.PRACTICE_NOT_MET
                        || e.decision() == EvidenceDecision.PRACTICE_AMBIGUOUS)
                .toList();
        assertThat(rows).as("practice %s row of %s", code, key).hasSize(1);
        return rows.get(0);
    }

    static boolean met(RuleOutcome outcome, String key, String code) {
        return practiceRow(outcome, key, code).decision() == EvidenceDecision.PRACTICE_MET;
    }

    static List<EvidenceItem> supportingRows(RuleOutcome outcome, String key) {
        return outcome.evidence().stream()
                .filter(e -> key.equals(e.subjectKey()))
                .filter(e -> e.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .toList();
    }

    static BigInteger points(RuleOutcome outcome, String key) {
        EvidenceItem row = subjectRow(outcome, key);
        assertThat(row.decision()).as("%s is eligible", key).isEqualTo(EvidenceDecision.ELIGIBLE);
        return row.points();
    }

    static String exclusionReason(RuleOutcome outcome, String key) {
        EvidenceItem row = subjectRow(outcome, key);
        assertThat(row.decision()).as("%s is excluded", key).isEqualTo(EvidenceDecision.EXCLUDED);
        return row.reasonCode();
    }

    static ResultComponent component(IndicatorResult result, String code) {
        return result.components().stream()
                .filter(c -> c.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component " + code));
    }

    static TeamResult teamOf(RuleOutcome outcome, String ine) {
        return outcome.teams().stream()
                .filter(t -> Objects.equals(t.ine(), ine))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no team " + ine));
    }

    /** COMPUTED with Σpoints/n exactly, the 4-decimal text and the band. */
    static void assertComputed(
            IndicatorResult result, long numerator, long denominator, String valueText, Classification classification) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(numerator));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(denominator));
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(numerator, denominator));
        assertThat(result.valueText()).isEqualTo(valueText);
        assertThat(result.classification()).isEqualTo(classification);
    }

    static void assertComponent(IndicatorResult result, String code, long numerator, long denominator) {
        ResultComponent component = component(result, code);
        assertThat(component.numerator()).as("component %s numerator", code).isEqualTo(BigInteger.valueOf(numerator));
        assertThat(component.denominator())
                .as("component %s denominator", code)
                .isEqualTo(BigInteger.valueOf(denominator));
    }

    static BigInteger pts(long value) {
        return BigInteger.valueOf(value);
    }
}
