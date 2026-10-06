package esusdata.indicator.pack.c3;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Synthetic canonical records for the C3 tests, written from the ficha transcription
 * ({@code docs/metodologia/c3-gestacao-puerperio.md}) and the rule contract only. The default
 * scenario follows the transcription's notation: DUM 2025-01-01, so DUM+294 = 2025-10-22 (the
 * substitute end) and a recorded outcome D = DUM+270 = 2025-09-28 (D+41 = 2025-11-08, D+42 =
 * 2025-11-09, D+43 = 2025-11-10); competência 2025-11. Every record gets a fresh {@code SourceRef}
 * and the municipality {@link CanonicalFixtures#IBGE}; fields a test does not care about stay
 * {@code null}.
 */
final class C3Fixtures {

    static final String IBGE = CanonicalFixtures.IBGE;
    static final String OTHER_IBGE = "3550308";
    static final YearMonth NOVEMBER = YearMonth.of(2025, 11);

    /** The transcription's example DUM. */
    static final LocalDate DUM = LocalDate.of(2025, 1, 1);

    /** DUM + 294: the substitute end of the pregnancy without a recorded outcome. */
    static final LocalDate SUBSTITUTE_END = LocalDate.of(2025, 10, 22);

    /** D = DUM + 270, a recorded outcome (CT-C3-62). */
    static final LocalDate OUTCOME = LocalDate.of(2025, 9, 28);

    static final LocalDate LINKED_ON = LocalDate.of(2024, 6, 1);
    static final String CNES = "1234567";
    static final String INE = "0000000001";
    static final String OTHER_INE = "0000000002";

    static final String DOCTOR = "225142";
    static final String NURSE = "223505";
    static final String ACS = "515105";
    static final String DENTIST = "223208";
    static final String PHARMACIST = "223405";
    static final String NURSING_TECHNICIAN = "322205";
    static final String ORAL_HEALTH_TECHNICIAN = "322405";

    static final String PREGNANCY_CIAP = "W78";
    static final String PUERPERIUM_CID = "Z39";

    static final String SYPHILIS = "0214010074";
    static final String HIV = "0214010040";
    static final String HEPATITIS_B = "0214010104";
    static final String HEPATITIS_C = "0214010090";
    static final String DTPA = "57";

    static final String PERFORMED = "PERFORMED";
    static final String REQUESTED = "REQUESTED";
    static final String EVALUATED = "EVALUATED";
    static final String MIP = "MIP";

    /** The eleven practices of the Quadro 01, in order. */
    static final String ALL_PRACTICES = "ABCDEFGHIJK";

    /** Six prenatal consultation days besides the anchor (all before DUM+294). */
    static final List<Integer> MORE_PRENATAL_DAYS = List.of(126, 154, 182, 210, 238, 266);

    private static final Set<EvidenceDecision> PRACTICE_DECISIONS =
            Set.of(EvidenceDecision.PRACTICE_MET, EvidenceDecision.PRACTICE_NOT_MET, EvidenceDecision.PRACTICE_EXEMPT);

    private C3Fixtures() {}

    // ---- packs, contexts and datasets ----

    static EvaluationContext context(YearMonth competencia) {
        return EvaluationContext.endOfMonth(IBGE, competencia);
    }

    /** compute(...) of a pack for competência 2025-11. */
    static RuleOutcome computeNovember(C3Pack pack, Collection<? extends Record> records) {
        return pack.compute(dataset(NOVEMBER, records), context(NOVEMBER));
    }

    /** The records, with the window of every capability C3 reads for {@code competencia}. */
    static CanonicalDataset dataset(YearMonth competencia, Collection<? extends Record> records) {
        DateWindow care = DateWindow.lastCivilMonths(competencia, 13);
        CanonicalDataset.Builder builder = CanonicalDataset.builder()
                .window(Capabilities.CITIZEN, care)
                .window(Capabilities.INDIVIDUAL_REGISTRATION, DateWindow.lastCivilMonths(competencia, 24))
                .window(Capabilities.CONDITION_LIST, care)
                .window(Capabilities.CARE_ENCOUNTER, care)
                .window(Capabilities.DENTAL_ENCOUNTER, care)
                .window(Capabilities.PROCEDURE_PERFORMED, care)
                .window(Capabilities.EXAM_REQUEST_EVALUATION, care)
                .window(Capabilities.HOME_VISIT, care)
                .window(Capabilities.MEASUREMENT_RECORD, care)
                .window(Capabilities.IMMUNIZATION_HISTORY, care);
        for (Record r : records) {
            builder.add(r);
        }
        return builder.build();
    }

    static LocalDate dum(int days) {
        return DUM.plusDays(days);
    }

    static String episodeKey(String personKey, LocalDate dum) {
        return personKey + "#" + dum;
    }

    // ---- records ----

    /** An individual (MIAI) encounter by a nurse on {@code date}; chain the fields a test needs. */
    static Encounter care(String personKey, LocalDate date) {
        return new Encounter(personKey, date);
    }

    /** The prenatal consultation that anchors the episode: nurse, W78 and the DUM. */
    static CanonicalCareEvent anchor(String personKey, LocalDate date, LocalDate lmp) {
        return care(personKey, date).ciap(PREGNANCY_CIAP).lmp(lmp).build();
    }

    /** A prenatal consultation (nurse, W78) without DUM. */
    static CanonicalCareEvent prenatal(String personKey, LocalDate date) {
        return care(personKey, date).ciap(PREGNANCY_CIAP).build();
    }

    /** A puerperal consultation (nurse, CID Z39). */
    static CanonicalCareEvent puerperal(String personKey, LocalDate date) {
        return care(personKey, date).cid(PUERPERIUM_CID).build();
    }

    /** A dental encounter (MIAOI) by a cirurgião-dentista. */
    static CanonicalCareEvent dental(String personKey, LocalDate date) {
        return care(personKey, date).form("DENTAL").cbo(DENTIST).build();
    }

    static CanonicalHomeVisit visit(String personKey, LocalDate date, String cbo) {
        return visit(personKey, date, cbo, "1", null, null);
    }

    static CanonicalHomeVisit visit(
            String personKey, LocalDate date, String cbo, String outcomeCode, String weightKg, String heightCm) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                personKey,
                date.toString(),
                cbo,
                CNES,
                INE,
                outcomeCode,
                List.of("ACOMP_GESTANTE"),
                weightKg,
                heightCm);
    }

    /** A dose of {@code code}; {@code date} may be {@code null} (a transcription without date). */
    static CanonicalImmunization dose(
            String personKey, LocalDate date, String code, String cbo, boolean transcription) {
        return dose(personKey, date, code, cbo, transcription, date);
    }

    /** A dose applied on {@code date} (may be {@code null}) and registered on {@code registered}. */
    static CanonicalImmunization dose(
            String personKey, LocalDate date, String code, String cbo, boolean transcription, LocalDate registered) {
        return new CanonicalImmunization(
                CanonicalFixtures.ref(transcription ? "rnds_ria" : "tb_fat_vacinacao_vacina"),
                IBGE,
                personKey,
                date == null ? null : date.toString(),
                code,
                "1",
                null,
                transcription,
                cbo,
                CNES,
                INE,
                registered == null ? null : registered.toString());
    }

    /** A dTpa (57) applied by a nurse. */
    static CanonicalImmunization dtpa(String personKey, LocalDate date) {
        return dose(personKey, date, DTPA, NURSE, false);
    }

    static CanonicalProcedureEvent procedure(
            String personKey, LocalDate date, String sigtap, String stage, String origin, String cbo) {
        return new CanonicalProcedureEvent(
                CanonicalFixtures.ref("tb_fat_proced_atend_proced"),
                IBGE,
                personKey,
                date.toString(),
                sigtap,
                stage,
                cbo,
                CNES,
                INE,
                origin);
    }

    /** A rapid test performed by a nurse in the MIP. */
    static CanonicalProcedureEvent test(String personKey, LocalDate date, String sigtap) {
        return procedure(personKey, date, sigtap, PERFORMED, MIP, NURSE);
    }

    static CanonicalMeasurement measurement(
            String personKey,
            LocalDate date,
            String weightKg,
            String heightCm,
            String systolic,
            String diastolic,
            String cbo,
            String origin) {
        return new CanonicalMeasurement(
                CanonicalFixtures.ref("tb_fat_proced_atend"),
                IBGE,
                personKey,
                date.toString(),
                weightKg,
                heightCm,
                systolic,
                diastolic,
                cbo,
                origin);
    }

    /** Blood pressure written in the MIP by a nurse. */
    static CanonicalMeasurement bloodPressure(String personKey, LocalDate date, String cbo) {
        return measurement(personKey, date, null, null, "120", "80", cbo, MIP);
    }

    /** A participant's weight and height in a collective activity (MIAC). */
    static CanonicalMeasurement collectiveActivity(
            String personKey, LocalDate date, String cbo, String activityType, String... practices) {
        return new CanonicalMeasurement(
                CanonicalFixtures.ref("tb_fat_atvdd_coletiva_part"),
                IBGE,
                personKey,
                date.toString(),
                "62.5",
                "160",
                null,
                null,
                cbo,
                "MIAC",
                activityType,
                List.of(practices));
    }

    /**
     * A problem/condition (LPC) of {@code system} ({@code CIAP2}/{@code CID10}) recorded on {@code
     * recorded} with {@code status} ("0" active, "1" latent, "2" resolved) and {@code resolved}.
     */
    static CanonicalCondition condition(
            String personKey, String system, String code, LocalDate recorded, String status, LocalDate resolved) {
        return new CanonicalCondition(
                CanonicalFixtures.ref("tb_fat_atd_ind_problemas"),
                IBGE,
                personKey,
                system,
                code,
                recorded.toString(),
                status,
                resolved == null ? null : resolved.toString(),
                "PROFESSIONAL",
                NURSE);
    }

    /** Weight and height written together in the MIP by a nurse. */
    static CanonicalMeasurement anthropometry(String personKey, LocalDate date) {
        return measurement(personKey, date, "62.5", "160", null, null, NURSE, MIP);
    }

    static CanonicalRegistration registration(String personKey, LocalDate date, String ine, String exitReason) {
        return new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                personKey,
                date.toString(),
                ine == null ? null : CNES,
                ine,
                false,
                false,
                false,
                exitReason,
                null,
                null,
                true);
    }

    static CanonicalPerson person(String personKey, LocalDate deathDate) {
        return new CanonicalPerson(
                CanonicalFixtures.ref("tb_fat_cidadao_pec"),
                IBGE,
                personKey,
                "1995-05-10",
                "FEMININO",
                null,
                deathDate == null ? null : deathDate.toString());
    }

    static CanonicalTeam team(String ine, String teamTypeCode) {
        return team(ine, teamTypeCode, "2025-01-01");
    }

    /** A team observation on {@code observedAt} (may be {@code null}). */
    static CanonicalTeam team(String ine, String teamTypeCode, String observedAt) {
        return new CanonicalTeam(CanonicalFixtures.ref("tb_dim_equipe"), IBGE, ine, CNES, teamTypeCode, observedAt);
    }

    /** A registration version with its own source reference and flags. */
    static CanonicalRegistration registrationVersion(
            String personKey,
            LocalDate date,
            String ine,
            SourceRef ref,
            boolean simplified,
            boolean inactive,
            boolean refused) {
        return new CanonicalRegistration(
                ref,
                IBGE,
                personKey,
                date.toString(),
                CNES,
                ine,
                simplified,
                inactive,
                refused,
                null,
                null,
                null,
                true);
    }

    /** Blood pressure of a collective activity (MIAC) participant, by a nurse. */
    static CanonicalMeasurement collectivePressure(
            String personKey, LocalDate date, String activityType, String... practices) {
        return new CanonicalMeasurement(
                CanonicalFixtures.ref("tb_fat_atvdd_coletiva_part"),
                IBGE,
                personKey,
                date.toString(),
                null,
                null,
                "120",
                "80",
                NURSE,
                "MIAC",
                activityType,
                List.of(practices));
    }

    static CanonicalPregnancyOutcome outcome(String personKey, LocalDate date, String... codes) {
        return new CanonicalPregnancyOutcome(
                CanonicalFixtures.ref("tb_fat_desfecho_gestacao"),
                IBGE,
                personKey,
                date.toString(),
                null,
                List.of(codes));
    }

    /** The person and a registration linking them to {@code ine} on {@link #LINKED_ON}. */
    static List<Record> linked(String personKey, String ine) {
        return linked(personKey, ine, LINKED_ON);
    }

    static List<Record> linked(String personKey, String ine, LocalDate on) {
        return List.of(person(personKey, null), registration(personKey, on, ine, null));
    }

    /** {@link #episode} with all eleven practices met, linked to {@link #INE}. */
    static List<Record> fullEpisode(String personKey) {
        return episode(personKey, INE, ALL_PRACTICES);
    }

    /**
     * A linked pregnancy with DUM {@link #DUM}, no recorded outcome (D = DUM+294 = 2025-10-22), in
     * which exactly the practices named in {@code practices} (letters A..K) are met (production
     * trimester convention: 1º trimestre up to DUM+97, 3º from DUM+196); every other practice is clearly not met. The anchor consultation is on
     * DUM+56 when A is wanted and on DUM+100 otherwise.
     */
    static List<Record> episode(String personKey, String ine, String practices) {
        List<Record> records = new ArrayList<>(linked(personKey, ine));
        records.add(anchor(personKey, dum(practices.indexOf('A') >= 0 ? 56 : 100), DUM));
        for (int i = 0; i < practices.length(); i++) {
            records.addAll(practiceRecords(personKey, practices.charAt(i)));
        }
        return records;
    }

    /** The records that meet one practice of {@link #episode} (A is met by the anchor itself). */
    private static List<Record> practiceRecords(String personKey, char practice) {
        List<Record> records = new ArrayList<>();
        switch (practice) {
            case 'B' -> MORE_PRENATAL_DAYS.forEach(day -> records.add(prenatal(personKey, dum(day))));
            case 'C' -> {
                for (int i = 0; i < 7; i++) {
                    records.add(bloodPressure(personKey, dum(101 + i), NURSE));
                }
            }
            case 'D' -> {
                for (int i = 0; i < 7; i++) {
                    records.add(anthropometry(personKey, dum(111 + i)));
                }
            }
            case 'E' -> List.of(150, 160, 170).forEach(day -> records.add(visit(personKey, dum(day), ACS)));
            case 'F' -> records.add(dtpa(personKey, dum(196)));
            case 'G' -> records.addAll(tests(personKey, dum(60), SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C));
            case 'H' -> records.addAll(tests(personKey, dum(240), SYPHILIS, HIV));
            case 'I' -> records.add(puerperal(personKey, SUBSTITUTE_END.plusDays(10)));
            case 'J' -> records.add(visit(personKey, SUBSTITUTE_END.plusDays(10), ACS));
            case 'K' -> records.add(dental(personKey, dum(140)));
            default -> {
                // A: the anchor on DUM+56
            }
        }
        return records;
    }

    static List<Record> tests(String personKey, LocalDate date, String... sigtap) {
        List<Record> records = new ArrayList<>();
        for (String code : sigtap) {
            records.add(test(personKey, date, code));
        }
        return records;
    }

    // ---- evidence lookups and assertions ----

    /** The EPISODE row (ELIGIBLE or EXCLUDED) of one subject. */
    static EvidenceItem episodeRow(RuleOutcome outcome, String subjectKey) {
        List<EvidenceItem> rows = outcome.evidence().stream()
                .filter(e -> e.subjectKind() == EvidenceSubjectKind.EPISODE)
                .filter(e -> subjectKey.equals(e.subjectKey()))
                .filter(e -> e.component() == null)
                .filter(e -> e.decision() == EvidenceDecision.ELIGIBLE || e.decision() == EvidenceDecision.EXCLUDED)
                .toList();
        assertThat(rows).as("EPISODE row of %s", subjectKey).hasSize(1);
        return rows.get(0);
    }

    /** The one decision row of practice {@code code} for an episode. */
    static EvidenceItem practice(RuleOutcome outcome, String subjectKey, String code) {
        List<EvidenceItem> rows = outcome.evidence().stream()
                .filter(e -> subjectKey.equals(e.subjectKey()))
                .filter(e -> code.equals(e.component()))
                .filter(e -> PRACTICE_DECISIONS.contains(e.decision()))
                .toList();
        assertThat(rows).as("practice %s of %s", code, subjectKey).hasSize(1);
        return rows.get(0);
    }

    /** The practice row of the default episode (person {@code gestante-1}, DUM {@link #DUM}). */
    static EvidenceItem practice(RuleOutcome outcome, String code) {
        return practice(outcome, episodeKey("gestante-1", DUM), code);
    }

    static List<EvidenceItem> supporting(RuleOutcome outcome, String subjectKey, String code) {
        return outcome.evidence().stream()
                .filter(e -> subjectKey.equals(e.subjectKey()))
                .filter(e -> code.equals(e.component()))
                .filter(e -> e.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .toList();
    }

    static void assertMet(EvidenceItem row, int points) {
        assertThat(row.decision()).as("practice %s", row.component()).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertThat(row.reasonCode()).isEqualTo("CUMPRIDA");
        assertThat(row.points()).isEqualTo(BigInteger.valueOf(points));
    }

    static void assertNotMet(EvidenceItem row) {
        assertThat(row.decision()).as("practice %s", row.component()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(row.reasonCode()).isEqualTo("NAO_CUMPRIDA");
        assertThat(row.points()).isEqualTo(BigInteger.ZERO);
    }

    /** An MIAI (or MIAOI) encounter under construction; every unset field stays {@code null}. */
    static final class Encounter {
        private final String personKey;
        private final LocalDate date;
        private String municipality = IBGE;
        private String form = "INDIVIDUAL";
        private String cbo = NURSE;
        private Boolean remote = false;
        private final List<String> ciap = new ArrayList<>();
        private final List<String> cid = new ArrayList<>();
        private final List<String> evaluated = new ArrayList<>();
        private final List<String> performed = new ArrayList<>();
        private final List<String> requested = new ArrayList<>();
        private String weightKg;
        private String heightCm;
        private String systolic;
        private String diastolic;
        private LocalDate lmp;
        private Integer gestationalWeeks;

        private Encounter(String personKey, LocalDate date) {
            this.personKey = personKey;
            this.date = date;
        }

        Encounter municipality(String ibge) {
            this.municipality = ibge;
            return this;
        }

        Encounter form(String value) {
            this.form = value;
            return this;
        }

        Encounter cbo(String value) {
            this.cbo = value;
            return this;
        }

        Encounter remote() {
            this.remote = true;
            return this;
        }

        Encounter ciap(String... codes) {
            ciap.addAll(List.of(codes));
            return this;
        }

        Encounter cid(String... codes) {
            cid.addAll(List.of(codes));
            return this;
        }

        Encounter evaluated(String... sigtap) {
            evaluated.addAll(List.of(sigtap));
            return this;
        }

        Encounter performed(String... sigtap) {
            performed.addAll(List.of(sigtap));
            return this;
        }

        Encounter requested(String... sigtap) {
            requested.addAll(List.of(sigtap));
            return this;
        }

        Encounter weight(String kg) {
            this.weightKg = kg;
            return this;
        }

        Encounter height(String cm) {
            this.heightCm = cm;
            return this;
        }

        Encounter pressure() {
            this.systolic = "118";
            this.diastolic = "76";
            return this;
        }

        Encounter lmp(LocalDate value) {
            this.lmp = value;
            return this;
        }

        Encounter gestationalWeeks(int weeks) {
            this.gestationalWeeks = weeks;
            return this;
        }

        CanonicalCareEvent build() {
            return new CanonicalCareEvent(
                    CanonicalFixtures.ref(
                            "DENTAL".equals(form) ? "tb_fat_atendimento_odonto" : "tb_fat_atendimento_individual"),
                    municipality,
                    personKey,
                    date.toString(),
                    form,
                    cbo,
                    CNES,
                    INE,
                    null,
                    null,
                    remote,
                    ciap,
                    cid,
                    requested,
                    evaluated,
                    performed,
                    weightKg,
                    heightCm,
                    systolic,
                    diastolic,
                    lmp == null ? null : lmp.toString(),
                    gestationalWeeks == null ? null : gestationalWeeks.toString(),
                    true,
                    null);
        }
    }
}
