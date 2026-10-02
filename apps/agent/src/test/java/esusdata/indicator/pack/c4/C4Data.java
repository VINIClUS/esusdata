package esusdata.indicator.pack.c4;

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
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Synthetic C4 data, built only from the ficha transcription (docs/metodologia/c4-cuidado-diabetes.md)
 * and the pack's public contract. Base person ("Casos de teste derivados"): linked to an eSF 70 team,
 * E11 evaluated by a professional in 2020 and active; competência 2026-03, cutoff 2026-03-31.
 */
final class C4Data {

    static final String IBGE = CanonicalFixtures.IBGE;
    static final String OTHER_IBGE = "3550308";
    static final String CNES = "2000001";
    static final String CNES_2 = "2000002";
    static final String INE_ESF = "0000100001";
    static final String INE_ESF_2 = "0000100002";
    static final String INE_EAP = "0000200001";
    /** A team with no CanonicalTeam record: type unknown. */
    static final String INE_UNKNOWN = "0000300001";

    static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);
    static final EvaluationContext CONTEXT = EvaluationContext.endOfMonth(IBGE, MARCH_2026);

    static final LocalDate LINKED_ON = LocalDate.of(2025, 1, 10);
    static final LocalDate DIAGNOSED_ON = LocalDate.of(2020, 5, 10);

    // CBO occupations (6 digits) used across the tests.
    static final String MEDICO = "225125"; // 2251 médico clínico
    static final String MEDICO_2231 = "223115"; // 2231
    static final String ENFERMEIRO = "223505"; // 2235
    static final String TEC_ENFERMAGEM = "322205"; // 3222 (not 3222-55)
    static final String TACS = "322255"; // 3222-55
    static final String ACS = "515105"; // 5151-05
    static final String DENTISTA = "223208"; // 2232
    static final String FARMACEUTICO = "223405"; // 2234
    static final String FISIOTERAPEUTA = "223605"; // 2236
    static final String NUTRICIONISTA = "223710"; // 2237
    static final String TSB = "322405"; // 3224

    static final String ACTIVE = "0";
    static final String LATENT = "1";
    static final String RESOLVED = "2";

    private C4Data() {}

    // ---------------------------------------------------------------- dataset builder

    static DatasetBuilder data() {
        return new DatasetBuilder();
    }

    /** A dataset builder that already knows the eSF 70 and eAP 76 teams (not INE_UNKNOWN). */
    static final class DatasetBuilder {
        private final CanonicalDataset.Builder builder = CanonicalDataset.builder();

        private DatasetBuilder() {
            builder.add(team(INE_ESF, CNES, "70"));
            builder.add(team(INE_ESF_2, CNES_2, "70"));
            builder.add(team(INE_EAP, CNES, "76"));
        }

        DatasetBuilder add(Record... records) {
            for (Record r : records) {
                builder.add(r);
            }
            return this;
        }

        DatasetBuilder addAll(List<? extends Record> records) {
            for (Record r : records) {
                builder.add(r);
            }
            return this;
        }

        /** The base person: linked to eSF {@link #INE_ESF}, E11 evaluated in 2020 and active. */
        DatasetBuilder diabetic(String key) {
            return diabetic(key, INE_ESF);
        }

        DatasetBuilder diabetic(String key, String ine) {
            return add(registration(key, LINKED_ON, ine), activeCondition(key, "CID10", "E11", DIAGNOSED_ON));
        }

        CanonicalDataset build() {
            return builder.build();
        }
    }

    // ---------------------------------------------------------------- records

    static CanonicalTeam team(String ine, String cnes, String type) {
        return new CanonicalTeam(CanonicalFixtures.ref("tb_dim_equipe"), IBGE, ine, cnes, type, "2024-01-01");
    }

    static CanonicalPerson person(String key, LocalDate deathDate) {
        return new CanonicalPerson(
                CanonicalFixtures.ref("tb_fat_cidadao"),
                IBGE,
                key,
                "1960-01-01",
                "FEMININO",
                null,
                deathDate == null ? null : deathDate.toString());
    }

    static String cnesOf(String ine) {
        return INE_ESF_2.equals(ine) ? CNES_2 : CNES;
    }

    /** A complete, active registration version linking {@code key} to {@code ine}. */
    static CanonicalRegistration registration(String key, LocalDate date, String ine) {
        return registration(key, date, ine, false, false, false, null, null);
    }

    static CanonicalRegistration registration(
            String key,
            LocalDate date,
            String ine,
            Boolean simplified,
            Boolean inactive,
            Boolean refused,
            String exitReason,
            Boolean selfReportedDiabetes) {
        return new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                key,
                date.toString(),
                ine == null ? CNES : cnesOf(ine),
                ine,
                simplified,
                inactive,
                refused,
                exitReason,
                null,
                selfReportedDiabetes,
                null);
    }

    static CanonicalRegistration exitRegistration(String key, LocalDate date, String ine, String exitReason) {
        return registration(key, date, ine, false, false, false, exitReason, null);
    }

    static CanonicalCondition activeCondition(String key, String system, String code, LocalDate recorded) {
        return condition(key, system, code, recorded, ACTIVE, null, "PROFESSIONAL");
    }

    static CanonicalCondition resolvedCondition(
            String key, String system, String code, LocalDate recorded, LocalDate resolved) {
        return condition(key, system, code, recorded, RESOLVED, resolved, "PROFESSIONAL");
    }

    static CanonicalCondition condition(
            String key,
            String system,
            String code,
            LocalDate recorded,
            String status,
            LocalDate resolved,
            String basis) {
        return new CanonicalCondition(
                CanonicalFixtures.ref("tb_fat_atd_ind_problemas"),
                IBGE,
                key,
                system,
                code,
                recorded.toString(),
                status,
                resolved == null ? null : resolved.toString(),
                basis);
    }

    /** An individual consultation (MIAI) with one non-diabetes problem evaluated (AMB-C4-05). */
    static CanonicalCareEvent consult(String key, LocalDate date, String cbo) {
        return care(key, date, cbo).ciap("K86").build();
    }

    static CareBuilder care(String key, LocalDate date, String cbo) {
        return new CareBuilder(key, date, cbo);
    }

    /** Fluent builder for a care event (MIAI by default, presential, no codes). */
    static final class CareBuilder {
        private final String key;
        private final LocalDate date;
        private final String cbo;
        private String municipality = IBGE;
        private String form = "INDIVIDUAL";
        private Boolean remote = false;
        private final List<String> ciap = new ArrayList<>();
        private final List<String> cid = new ArrayList<>();
        private final List<String> requested = new ArrayList<>();
        private final List<String> evaluated = new ArrayList<>();
        private final List<String> performed = new ArrayList<>();
        private String weight;
        private String height;
        private String systolic;
        private String diastolic;

        private CareBuilder(String key, LocalDate date, String cbo) {
            this.key = key;
            this.date = date;
            this.cbo = cbo;
        }

        CareBuilder municipality(String ibge) {
            this.municipality = ibge;
            return this;
        }

        CareBuilder form(String value) {
            this.form = value;
            return this;
        }

        CareBuilder remote() {
            this.remote = true;
            return this;
        }

        CareBuilder ciap(String... codes) {
            ciap.addAll(Arrays.asList(codes));
            return this;
        }

        CareBuilder cid(String... codes) {
            cid.addAll(Arrays.asList(codes));
            return this;
        }

        CareBuilder requested(String... codes) {
            requested.addAll(Arrays.asList(codes));
            return this;
        }

        CareBuilder evaluated(String... codes) {
            evaluated.addAll(Arrays.asList(codes));
            return this;
        }

        CareBuilder performed(String... codes) {
            performed.addAll(Arrays.asList(codes));
            return this;
        }

        CareBuilder weight(String kg) {
            this.weight = kg;
            return this;
        }

        CareBuilder height(String cm) {
            this.height = cm;
            return this;
        }

        CareBuilder weightAndHeight() {
            return weight("82.5").height("168");
        }

        CareBuilder bloodPressure(String sys, String dia) {
            this.systolic = sys;
            this.diastolic = dia;
            return this;
        }

        CareBuilder bloodPressure() {
            return bloodPressure("130", "80");
        }

        CanonicalCareEvent build() {
            return new CanonicalCareEvent(
                    CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                    municipality,
                    key,
                    date.toString(),
                    form,
                    cbo,
                    null,
                    null,
                    null,
                    null,
                    remote,
                    ciap,
                    cid,
                    requested,
                    evaluated,
                    performed,
                    weight,
                    height,
                    systolic,
                    diastolic,
                    null,
                    null,
                    null,
                    null);
        }
    }

    /** A home visit (MIVDT) with a visit reason filled in and outcome "1". */
    static CanonicalHomeVisit visit(String key, LocalDate date, String cbo) {
        return visit(key, date, cbo, "1", List.of("1"), null, null);
    }

    static CanonicalHomeVisit visit(
            String key,
            LocalDate date,
            String cbo,
            String outcome,
            List<String> reasons,
            String weight,
            String height) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                cbo,
                null,
                null,
                outcome,
                reasons,
                weight,
                height);
    }

    static CanonicalProcedureEvent procedure(String key, LocalDate date, String code, String stage, String cbo) {
        return CanonicalFixtures.procedure(key, date, code, stage, cbo);
    }

    static CanonicalMeasurement measurement(
            String key, LocalDate date, String cbo, String weight, String height, String sys, String dia) {
        return new CanonicalMeasurement(
                CanonicalFixtures.ref("tb_fat_atividade_coletiva_part"),
                IBGE,
                key,
                date.toString(),
                weight,
                height,
                sys,
                dia,
                cbo,
                "MIAC");
    }

    static CanonicalMeasurement bloodPressureMeasurement(String key, LocalDate date, String cbo) {
        return measurement(key, date, cbo, null, null, "128", "82");
    }

    /** Records that meet A, B, C, E and F (one MIAI by a physician on 2026-02-10). */
    static CanonicalCareEvent allButVisits(String key) {
        return care(key, LocalDate.of(2026, 2, 10), MEDICO)
                .ciap("T90")
                .bloodPressure()
                .weightAndHeight()
                .requested(C4Codes.HBA1C)
                .performed(C4Codes.DIABETIC_FOOT)
                .build();
    }

    /** Two ACS visits 46 days apart: D met. */
    static List<Record> twoVisits(String key) {
        return List.of(visit(key, LocalDate.of(2026, 1, 5), ACS), visit(key, LocalDate.of(2026, 2, 20), ACS));
    }

    /** Every practice A–F met: 100 points. */
    static List<Record> fullCare(String key) {
        List<Record> list = new ArrayList<>(twoVisits(key));
        list.add(allButVisits(key));
        return list;
    }

    // ---------------------------------------------------------------- evaluation and lookups

    static RuleOutcome ungated(CanonicalDataset data) {
        return C4Pack.evaluateUngated(data, CONTEXT);
    }

    static RuleOutcome ungated(CanonicalDataset data, EvaluationContext context) {
        return C4Pack.evaluateUngated(data, context);
    }

    static RuleOutcome gated(CanonicalDataset data) {
        return new C4Pack().evaluate(data, CONTEXT);
    }

    static List<EvidenceItem> rowsOf(RuleOutcome outcome, String key) {
        return outcome.evidence().stream()
                .filter(e -> key.equals(e.subjectKey()))
                .toList();
    }

    /** The single person-level row (ELIGIBLE or EXCLUDED) of {@code key}. */
    static EvidenceItem personRow(RuleOutcome outcome, String key) {
        List<EvidenceItem> rows = rowsOf(outcome, key).stream()
                .filter(e -> e.component() == null)
                .filter(e -> e.decision() == EvidenceDecision.ELIGIBLE || e.decision() == EvidenceDecision.EXCLUDED)
                .toList();
        if (rows.size() != 1) {
            throw new AssertionError("expected exactly one person row for " + key + ", got " + rows);
        }
        return rows.get(0);
    }

    static boolean isEligible(RuleOutcome outcome, String key) {
        return personRow(outcome, key).decision() == EvidenceDecision.ELIGIBLE;
    }

    /** The single practice decision row of {@code key} for practice {@code code}. */
    static EvidenceItem practiceRow(RuleOutcome outcome, String key, String code) {
        List<EvidenceItem> rows = rowsOf(outcome, key).stream()
                .filter(e -> code.equals(e.component()))
                .filter(e -> e.decision() == EvidenceDecision.PRACTICE_MET
                        || e.decision() == EvidenceDecision.PRACTICE_NOT_MET)
                .toList();
        if (rows.size() != 1) {
            throw new AssertionError("expected exactly one practice " + code + " row for " + key + ", got " + rows);
        }
        return rows.get(0);
    }

    static boolean met(RuleOutcome outcome, String key, String code) {
        return practiceRow(outcome, key, code).decision() == EvidenceDecision.PRACTICE_MET;
    }

    static List<EvidenceItem> supporting(RuleOutcome outcome, String key, String code) {
        return rowsOf(outcome, key).stream()
                .filter(e -> code.equals(e.component()))
                .filter(e -> e.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .toList();
    }

    static BigInteger points(RuleOutcome outcome, String key) {
        return personRow(outcome, key).points();
    }

    static ResultComponent component(IndicatorResult result, String code) {
        return result.components().stream()
                .filter(c -> code.equals(c.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component " + code + " in " + result.components()));
    }

    static TeamResult teamOf(RuleOutcome outcome, String ine) {
        return outcome.teams().stream()
                .filter(t -> ine.equals(t.ine()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no team " + ine + " in " + outcome.teams()));
    }

    static BigInteger big(long value) {
        return BigInteger.valueOf(value);
    }

    static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
