package esusdata.indicator.pack.c5;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.Assertions;

/**
 * Synthetic records, scenarios and evidence readers for the C5 tests. Records the shared {@link
 * CanonicalFixtures} do not cover (blood pressure outside an encounter, a visit with weight and
 * height, a self-reported or resolved condition, registration exits, teams, deaths) are built here.
 * Every record lives in {@link CanonicalFixtures#IBGE} unless a factory says otherwise; no record
 * carries a name, CPF or CNS.
 *
 * <p>The default eligible person ({@link Scenario#eligible(String)}) is linked to INE {@link #INE}
 * / CNES {@link #CNES} on {@link #LINK_DATE} and has {@code I10} evaluated by a professional in
 * 2019 and still active — the "base comum" of the ficha's derived cases.
 */
final class C5TestData {

    static final String IBGE = CanonicalFixtures.IBGE;
    static final String OTHER_IBGE = "3550308";

    /** Competência base dos casos derivados: 2026-03, corte 2026-03-31. */
    static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

    static final LocalDate CUTOFF = MARCH_2026.atEndOfMonth();
    static final String CUTOFF_TEXT = CUTOFF.toString();
    static final LocalDate LINK_DATE = LocalDate.of(2025, 1, 10);
    static final LocalDate HYPERTENSION_DATE = LocalDate.of(2019, 5, 10);

    static final String INE = "0000001111";
    static final String CNES = "1234567";
    static final String INE_2 = "0000002222";
    static final String CNES_2 = "7654321";

    static final String P1 = "pessoa-1";
    static final String P2 = "pessoa-2";
    static final String P3 = "pessoa-3";
    static final String P4 = "pessoa-4";
    static final String P5 = "pessoa-5";
    static final String P6 = "pessoa-6";
    static final String P7 = "pessoa-7";
    static final String P8 = "pessoa-8";

    static final String CID10 = "CID10";
    static final String CIAP2 = "CIAP2";
    static final String ACTIVE = "ATIVO";
    static final String RESOLVED = "RESOLVIDO";

    /** Médico de família e comunidade (grupo 2251). */
    static final String CBO_DOCTOR = "225142";
    /** Enfermeiro (grupo 2235). */
    static final String CBO_NURSE = "223505";
    /** Técnico de enfermagem (grupo 3222). */
    static final String CBO_NURSING_TECH = "322205";
    /** Técnico em agente comunitário de saúde (3222-55). */
    static final String CBO_TACS = "322255";
    /** Agente comunitário de saúde (5151-05). */
    static final String CBO_ACS = "515105";
    /** Técnico em saúde bucal (grupo 3224). */
    static final String CBO_ORAL_HEALTH_TECH = "322415";
    /** Cirurgião-dentista clínico geral (grupo 2232, Quadro 03). */
    static final String CBO_DENTIST = "223208";
    /** Psicólogo clínico: in none of the C5 groups. */
    static final String CBO_PSYCHOLOGIST = "251510";

    static final String SIGTAP_BLOOD_PRESSURE = "0301100039";
    static final String SIGTAP_ANTHROPOMETRY = "0101040024";
    static final String SIGTAP_WEIGHT = "0101040083";
    static final String SIGTAP_HEIGHT = "0101040075";
    /** Consulta de profissional de nível superior na atenção primária (MIP; AMB-C5-05). */
    static final String SIGTAP_CONSULTATION = "0301010030";

    static final String ORIGIN_MIP = "MIP";
    static final String ORIGIN_MIAC = "MIAC";
    /** Dental encounter: not a model of Quadros 03/04. */
    static final String ORIGIN_MIAO = "MIAO";

    static final List<String> PRACTICES = List.of("A", "B", "C", "D");
    static final BigInteger PRACTICE_POINTS = BigInteger.valueOf(25);

    private static final String WEIGHT = "72.5";
    private static final String HEIGHT = "168";
    private static final String SYSTOLIC = "130";
    private static final String DIASTOLIC = "85";
    private static final String PROFESSIONAL = "PROFESSIONAL";
    private static final String REGISTRATION_TABLE = "tb_fat_cad_individual";
    private static final String CONDITION_TABLE = "tb_fat_atd_ind_problemas";
    private static final String ENCOUNTER_TABLE = "tb_fat_atendimento_individual";
    private static final String VISIT_TABLE = "tb_fat_visita_domiciliar";
    private static final String INDIVIDUAL = "INDIVIDUAL";
    private static final String VISIT_DONE = "1";
    private static final List<String> VISIT_REASONS = List.of("ACOMP_PESSOA_HIPERTENSAO");

    private C5TestData() {}

    // ---- contexts ----

    static EvaluationContext march() {
        return EvaluationContext.endOfMonth(IBGE, MARCH_2026);
    }

    static EvaluationContext endOf(YearMonth competencia) {
        return EvaluationContext.endOfMonth(IBGE, competencia);
    }

    // ---- conditions ----

    static CanonicalCondition condition(String key, String system, String code, LocalDate recorded, String status) {
        return CanonicalFixtures.conditionEvaluatedBy(key, system, code, recorded, status, CBO_DOCTOR);
    }

    /** {@code I10} evaluated by a doctor in 2019 and still active (item 5: médica(o) e/ou enfermeira(o)). */
    static CanonicalCondition hypertension(String key) {
        return condition(key, CID10, "I10", HYPERTENSION_DATE, ACTIVE);
    }

    /** A professional condition with {@code status} and the date it was resolved ({@code null} allowed). */
    static CanonicalCondition resolvedCondition(
            String key, String system, String code, String status, LocalDate resolvedOn) {
        return new CanonicalCondition(
                CanonicalFixtures.ref(CONDITION_TABLE),
                IBGE,
                key,
                system,
                code,
                HYPERTENSION_DATE.toString(),
                status,
                resolvedOn == null ? null : resolvedOn.toString(),
                PROFESSIONAL,
                CBO_DOCTOR);
    }

    /** A professional condition evaluated by {@code cbo} ({@code null} when the source does not say). */
    static CanonicalCondition conditionBy(String key, String system, String code, LocalDate recorded, String cbo) {
        return CanonicalFixtures.conditionEvaluatedBy(key, system, code, recorded, ACTIVE, cbo);
    }

    /** An active condition, evaluated by a doctor, whose source does not say whether it was evaluated or reported. */
    static CanonicalCondition conditionWithoutBasis(String key, String system, String code, LocalDate recorded) {
        return new CanonicalCondition(
                CanonicalFixtures.ref(CONDITION_TABLE),
                IBGE,
                key,
                system,
                code,
                recorded.toString(),
                ACTIVE,
                null,
                null,
                CBO_DOCTOR);
    }

    /** A condition the person reported (basis {@code SELF_REPORTED}), active. */
    static CanonicalCondition selfReportedCondition(String key, String system, String code) {
        return new CanonicalCondition(
                CanonicalFixtures.ref(CONDITION_TABLE),
                IBGE,
                key,
                system,
                code,
                HYPERTENSION_DATE.toString(),
                ACTIVE,
                null,
                "SELF_REPORTED");
    }

    /** Active {@code I10} recorded in another municipality's extract. */
    static CanonicalCondition hypertensionIn(String municipalityIbge, String key) {
        return new CanonicalCondition(
                CanonicalFixtures.ref(CONDITION_TABLE),
                municipalityIbge,
                key,
                CID10,
                "I10",
                HYPERTENSION_DATE.toString(),
                ACTIVE,
                null,
                PROFESSIONAL,
                CBO_DOCTOR);
    }

    // ---- registrations, people and teams ----

    static CanonicalRegistration link(String key, LocalDate date, String cnes, String ine) {
        return CanonicalFixtures.registration(key, date, cnes, ine);
    }

    /** A complete registration version on the default team with "saída do cidadão" {@code exitReason}. */
    static CanonicalRegistration exitRegistration(String key, LocalDate date, String exitReason) {
        return registration(key, date, false, false, false, exitReason, null);
    }

    /** The PEC's simplified citizen record: not a complete individual registration. */
    static CanonicalRegistration simplifiedRegistration(String key, LocalDate date) {
        return registration(key, date, true, false, false, null, null);
    }

    static CanonicalRegistration inactiveRegistration(String key, LocalDate date) {
        return registration(key, date, false, true, false, null, null);
    }

    static CanonicalRegistration refusedRegistration(String key, LocalDate date) {
        return registration(key, date, false, false, true, null, null);
    }

    /** A complete registration on the default team where the person reported hypertension. */
    static CanonicalRegistration selfReportedRegistration(String key, LocalDate date) {
        return registration(key, date, false, false, false, null, Boolean.TRUE);
    }

    private static CanonicalRegistration registration(
            String key,
            LocalDate date,
            boolean simplified,
            boolean inactive,
            boolean refused,
            String exitReason,
            Boolean selfReportedHypertension) {
        return new CanonicalRegistration(
                CanonicalFixtures.ref(REGISTRATION_TABLE),
                IBGE,
                key,
                date.toString(),
                CNES,
                INE,
                simplified,
                inactive,
                refused,
                exitReason,
                selfReportedHypertension,
                null,
                null);
    }

    static CanonicalPerson deceased(String key, LocalDate deathDate) {
        return new CanonicalPerson(
                CanonicalFixtures.ref(REGISTRATION_TABLE),
                IBGE,
                key,
                "1950-06-15",
                "FEMININO",
                null,
                deathDate.toString());
    }

    static CanonicalTeam team(String ine, String cnes, String teamTypeCode) {
        return new CanonicalTeam(CanonicalFixtures.ref("tb_dim_equipe"), IBGE, ine, cnes, teamTypeCode, CUTOFF_TEXT);
    }

    // ---- care events (MIAI) ----

    /** An individual consultation that evaluated {@code K86} (any evaluated problem satisfies MIAI). */
    static CanonicalCareEvent consultation(String key, LocalDate date, String cbo) {
        return CanonicalFixtures.encounterWithProblems(key, date, cbo, List.of("K86"), List.of());
    }

    /** An individual encounter with no evaluated problem. */
    static CanonicalCareEvent consultationWithoutProblem(String key, LocalDate date, String cbo) {
        return CanonicalFixtures.encounter(key, date, cbo, false);
    }

    /** A remote individual consultation (teleconsulta) that evaluated {@code I10}. */
    static CanonicalCareEvent remoteConsultation(String key, LocalDate date, String cbo) {
        return careEvent(key, date, cbo, true, List.of("I10"), List.of());
    }

    /** An individual encounter whose only content is SIGTAP {@code codes} performed. */
    static CanonicalCareEvent encounterWithProcedures(String key, LocalDate date, String cbo, String... codes) {
        return careEvent(key, date, cbo, false, List.of(), List.of(codes));
    }

    /** An individual encounter with blood pressure written in the PEC's own field. */
    static CanonicalCareEvent bloodPressureEncounter(String key, LocalDate date, String cbo) {
        return CanonicalFixtures.encounterWithMeasures(key, date, cbo, null, null, SYSTOLIC, DIASTOLIC);
    }

    /** An individual encounter with weight and/or height ({@code null} = not written). */
    static CanonicalCareEvent anthropometryEncounter(
            String key, LocalDate date, String cbo, String weightKg, String heightCm) {
        return CanonicalFixtures.encounterWithMeasures(key, date, cbo, weightKg, heightCm, null, null);
    }

    private static CanonicalCareEvent careEvent(
            String key, LocalDate date, String cbo, boolean remote, List<String> cid, List<String> performed) {
        return new CanonicalCareEvent(
                CanonicalFixtures.ref(ENCOUNTER_TABLE),
                IBGE,
                key,
                date.toString(),
                INDIVIDUAL,
                cbo,
                null,
                null,
                null,
                null,
                remote,
                List.of(),
                cid,
                List.of(),
                List.of(),
                performed,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    // ---- procedures and measurements (MIP / MIAC) ----

    /** A performed SIGTAP procedure (MIP). */
    static CanonicalProcedureEvent procedure(String key, LocalDate date, String code, String cbo) {
        return CanonicalFixtures.procedure(key, date, code, "PERFORMED", cbo);
    }

    static CanonicalProcedureEvent procedure(String key, LocalDate date, String code, String stage, String cbo) {
        return CanonicalFixtures.procedure(key, date, code, stage, cbo);
    }

    /** A SIGTAP procedure of {@code stage} from {@code origin} ({@code null} allowed for both). */
    static CanonicalProcedureEvent procedureEvent(
            String key, LocalDate date, String code, String stage, String cbo, String origin) {
        return new CanonicalProcedureEvent(
                CanonicalFixtures.ref("tb_fat_proced_atend_proced"),
                IBGE,
                key,
                date.toString(),
                code,
                stage,
                cbo,
                null,
                null,
                origin);
    }

    /** A performed SIGTAP procedure from the information model {@code origin}. */
    static CanonicalProcedureEvent procedureFrom(String key, LocalDate date, String code, String cbo, String origin) {
        return new CanonicalProcedureEvent(
                CanonicalFixtures.ref("tb_fat_proced_atend_proced"),
                IBGE,
                key,
                date.toString(),
                code,
                "PERFORMED",
                cbo,
                null,
                null,
                origin);
    }

    /** Blood pressure measured outside an encounter ({@code origin} MIP or MIAC) by {@code cbo}. */
    static CanonicalMeasurement bloodPressureMeasurement(String key, LocalDate date, String cbo, String origin) {
        return new CanonicalMeasurement(
                CanonicalFixtures.ref("tb_fat_proced_atend"),
                IBGE,
                key,
                date.toString(),
                null,
                null,
                SYSTOLIC,
                DIASTOLIC,
                cbo,
                origin);
    }

    /** Weight and height measured outside an encounter ({@code origin} MIP or MIAC) by {@code cbo}. */
    static CanonicalMeasurement anthropometryMeasurement(String key, LocalDate date, String cbo, String origin) {
        return new CanonicalMeasurement(
                CanonicalFixtures.ref("tb_fat_atividade_coletiva_part"),
                IBGE,
                key,
                date.toString(),
                WEIGHT,
                HEIGHT,
                null,
                null,
                cbo,
                origin);
    }

    // ---- home visits (MIVDT) ----

    /** A done visit by {@code cbo} with its reason filled in (item 24 e). */
    static CanonicalHomeVisit visit(String key, LocalDate date, String cbo) {
        return visit(key, date, cbo, VISIT_DONE);
    }

    static CanonicalHomeVisit visit(String key, LocalDate date, String cbo, String outcome) {
        return homeVisit(key, date, cbo, outcome, VISIT_REASONS, null, null);
    }

    /** A visit without «motivo da visita»: item 24 e does not count it. */
    static CanonicalHomeVisit visitWithoutReason(String key, LocalDate date, String cbo) {
        return homeVisit(key, date, cbo, VISIT_DONE, List.of(), null, null);
    }

    private static CanonicalHomeVisit homeVisit(
            String key,
            LocalDate date,
            String cbo,
            String outcome,
            List<String> reasons,
            String weightKg,
            String heightCm) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref(VISIT_TABLE),
                IBGE,
                key,
                date.toString(),
                cbo,
                null,
                null,
                outcome,
                reasons,
                weightKg,
                heightCm);
    }

    /** A visit that wrote weight and/or height in the visit form ({@code null} = not written). */
    static CanonicalHomeVisit visitWithAnthropometry(
            String key, LocalDate date, String cbo, String weightKg, String heightCm) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref(VISIT_TABLE),
                IBGE,
                key,
                date.toString(),
                cbo,
                null,
                null,
                VISIT_DONE,
                VISIT_REASONS,
                weightKg,
                heightCm);
    }

    /** A visit recorded in another municipality's extract. */
    static CanonicalHomeVisit visitIn(String municipalityIbge, String key, LocalDate date) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref(VISIT_TABLE),
                municipalityIbge,
                key,
                date.toString(),
                CBO_ACS,
                null,
                null,
                VISIT_DONE,
                VISIT_REASONS,
                null,
                null);
    }

    // ---- scenario ----

    static Scenario scenario() {
        return new Scenario();
    }

    /** A dataset under construction for competência 2026-03 (or any context given). */
    static final class Scenario {
        private final CanonicalDataset.Builder builder = CanonicalDataset.builder();

        /** Declares that the run read {@code capability} for {@code window} (§1.6). */
        Scenario window(String capability, DateWindow window) {
            builder.window(capability, window);
            return this;
        }

        /** Declares every capability read exactly as {@code requirements(2026-03)} asks. */
        Scenario readAsRequired() {
            for (PartRequirement part : new C5Pack().requirements(MARCH_2026).parts()) {
                window(part.capability(), new DateWindow(part.periodStart(), part.periodEndExclusive()));
            }
            return this;
        }

        Scenario add(Record... records) {
            for (Record canonicalRecord : records) {
                builder.add(canonicalRecord);
            }
            return this;
        }

        /** Registration on the default team on {@link C5TestData#LINK_DATE}, without any condition. */
        Scenario linked(String key) {
            return add(link(key, LINK_DATE, CNES, INE));
        }

        /** The default eligible person: linked to {@link C5TestData#INE} and with active {@code I10} since 2019. */
        Scenario eligible(String key) {
            return eligible(key, CNES, INE);
        }

        Scenario eligible(String key, String cnes, String ine) {
            return add(link(key, LINK_DATE, cnes, ine), hypertension(key));
        }

        /** Eligible, linked on {@code linkDate} (for competências before {@link C5TestData#LINK_DATE}). */
        Scenario eligibleSince(String key, LocalDate linkDate) {
            return add(link(key, linkDate, CNES, INE), hypertension(key));
        }

        /** Practice A: a doctor's consultation with an evaluated problem. */
        Scenario withConsultation(String key, LocalDate date) {
            return add(consultation(key, date, CBO_DOCTOR));
        }

        /** Practice B: blood pressure in an encounter by a nursing technician. */
        Scenario withBloodPressure(String key, LocalDate date) {
            return add(bloodPressureEncounter(key, date, CBO_NURSING_TECH));
        }

        /** Practice C: weight and height in the same encounter by a nurse. */
        Scenario withAnthropometry(String key, LocalDate date) {
            return add(anthropometryEncounter(key, date, CBO_NURSE, WEIGHT, HEIGHT));
        }

        /** Home visits by an ACS on each date. */
        Scenario withVisits(String key, LocalDate... dates) {
            for (LocalDate date : dates) {
                add(visit(key, date, CBO_ACS));
            }
            return this;
        }

        /** A, B and C inside their windows for competência 2026-03; no visit. */
        Scenario withPracticesAbc(String key) {
            return withConsultation(key, LocalDate.of(2026, 2, 10))
                    .withBloodPressure(key, LocalDate.of(2026, 2, 11))
                    .withAnthropometry(key, LocalDate.of(2025, 12, 1));
        }

        /** A, B, C and D (visits 75 days apart) inside their windows for competência 2026-03. */
        Scenario withAllPractices(String key) {
            return withPracticesAbc(key).withVisits(key, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 1, 15));
        }

        CanonicalDataset dataset() {
            return builder.build();
        }

        /** The gated result ({@code evaluate}) for competência 2026-03. */
        RuleOutcome evaluate() {
            C5Pack pack = new C5Pack();
            return GateFixtures.published(pack.descriptor(), pack.evaluate(dataset(), march()));
        }

        /** The result before the release gates, for competência 2026-03. */
        RuleOutcome ungated() {
            return ungated(march());
        }

        RuleOutcome ungated(EvaluationContext context) {
            return new C5Pack().evaluateUngated(dataset(), context);
        }
    }

    // ---- evidence and result readers ----

    static List<EvidenceItem> rowsOf(RuleOutcome outcome, String key) {
        return outcome.evidence().stream()
                .filter(row -> key.equals(row.subjectKey()))
                .toList();
    }

    /** True for a per-practice decision row (not a supporting event, not a person decision). */
    static boolean isPracticeDecision(EvidenceItem row) {
        return row.component() != null && row.decision() != EvidenceDecision.SUPPORTING_EVENT;
    }

    /** The single person decision row (component {@code null}) of {@code key}. */
    static EvidenceItem decisionOf(RuleOutcome outcome, String key) {
        List<EvidenceItem> rows = rowsOf(outcome, key).stream()
                .filter(row -> row.component() == null)
                .toList();
        Assertions.assertThat(rows).as("person decision rows of %s", key).hasSize(1);
        return rows.get(0);
    }

    /** The single decision row of practice {@code component} of {@code key}. */
    static EvidenceItem practiceOf(RuleOutcome outcome, String key, String component) {
        List<EvidenceItem> rows = rowsOf(outcome, key).stream()
                .filter(C5TestData::isPracticeDecision)
                .filter(row -> component.equals(row.component()))
                .toList();
        Assertions.assertThat(rows)
                .as("decision rows of practice %s of %s", component, key)
                .hasSize(1);
        return rows.get(0);
    }

    static List<EvidenceItem> supportingOf(RuleOutcome outcome, String key, String component) {
        return rowsOf(outcome, key).stream()
                .filter(row -> row.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .filter(row -> component.equals(row.component()))
                .toList();
    }

    /** The practices {@code key} met, sorted, one entry per {@code PRACTICE_MET} row. */
    static List<String> metPractices(RuleOutcome outcome, String key) {
        return rowsOf(outcome, key).stream()
                .filter(row -> row.decision() == EvidenceDecision.PRACTICE_MET)
                .map(EvidenceItem::component)
                .sorted()
                .toList();
    }

    static TeamResult teamOf(RuleOutcome outcome, String ine) {
        List<TeamResult> teams = outcome.teams().stream()
                .filter(team -> Objects.equals(team.ine(), ine))
                .toList();
        Assertions.assertThat(teams).as("team results of INE %s", ine).hasSize(1);
        return teams.get(0);
    }

    /** {@code key} is eligible, met exactly {@code met} and earned 25 points for each. */
    static void assertPractices(RuleOutcome outcome, String key, String... met) {
        EvidenceItem decision = decisionOf(outcome, key);
        Assertions.assertThat(decision.decision()).as("decision of %s", key).isEqualTo(EvidenceDecision.ELIGIBLE);
        Assertions.assertThat(metPractices(outcome, key))
                .as("practices met by %s", key)
                .containsExactly(met);
        Assertions.assertThat(decision.points())
                .as("points of %s", key)
                .isEqualTo(PRACTICE_POINTS.multiply(BigInteger.valueOf(met.length)));
    }

    /** {@code key} was considered and left out with {@code reasonCode}, without practice rows. */
    static void assertExcluded(RuleOutcome outcome, String key, String reasonCode) {
        EvidenceItem decision = decisionOf(outcome, key);
        Assertions.assertThat(decision.decision()).as("decision of %s", key).isEqualTo(EvidenceDecision.EXCLUDED);
        Assertions.assertThat(decision.reasonCode()).as("reason of %s", key).isEqualTo(reasonCode);
        Assertions.assertThat(decision.points())
                .as("points of excluded %s", key)
                .isNull();
        Assertions.assertThat(rowsOf(outcome, key))
                .as("practice rows of excluded %s", key)
                .noneMatch(row -> row.component() != null);
    }

    /** Components A–D, 25 points each, with {@code met[i]} of {@code denominator} for each practice. */
    static void assertComponents(IndicatorResult result, long denominator, long... met) {
        List<ResultComponent> components = result.components();
        Assertions.assertThat(components).extracting(ResultComponent::code).containsExactlyElementsOf(PRACTICES);
        for (int i = 0; i < PRACTICES.size(); i++) {
            ResultComponent component = components.get(i);
            Assertions.assertThat(component.weight())
                    .as("weight of %s", component.code())
                    .isEqualTo(PRACTICE_POINTS);
            Assertions.assertThat(component.numerator())
                    .as("numerator of %s", component.code())
                    .isEqualTo(BigInteger.valueOf(met[i]));
            Assertions.assertThat(component.denominator())
                    .as("denominator of %s", component.code())
                    .isEqualTo(BigInteger.valueOf(denominator));
        }
    }

    /** {@code actual} equals {@code numerator/denominator} as a fraction (any representation). */
    static void assertExactValue(ExactRatio actual, long numerator, long denominator) {
        Assertions.assertThat(actual).as("exact value").isNotNull();
        Assertions.assertThat(actual.compareTo(ExactRatio.of(numerator, denominator)))
                .as("%s compared to %s/%s", actual, numerator, denominator)
                .isZero();
    }

    /** No source record supports the same practice of the same person twice (MET-32). */
    static void assertNoRepeatedSupport(RuleOutcome outcome) {
        List<String> keys = outcome.evidence().stream()
                .filter(row -> row.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .map(row -> row.subjectKey() + "|" + row.component() + "|" + row.sourceRef())
                .toList();
        Assertions.assertThat(keys).as("supporting rows").doesNotHaveDuplicates();
    }
}
