package esusdata.indicator.pack.c4;

import static esusdata.indicator.pack.c4.C4Data.ACS;
import static esusdata.indicator.pack.c4.C4Data.CNES;
import static esusdata.indicator.pack.c4.C4Data.CONTEXT;
import static esusdata.indicator.pack.c4.C4Data.DENTISTA;
import static esusdata.indicator.pack.c4.C4Data.DIAGNOSED_ON;
import static esusdata.indicator.pack.c4.C4Data.ENFERMEIRO;
import static esusdata.indicator.pack.c4.C4Data.INE_ESF;
import static esusdata.indicator.pack.c4.C4Data.LINKED_ON;
import static esusdata.indicator.pack.c4.C4Data.MEDICO;
import static esusdata.indicator.pack.c4.C4Data.TEC_ENFERMAGEM;
import static esusdata.indicator.pack.c4.C4Data.activeCondition;
import static esusdata.indicator.pack.c4.C4Data.care;
import static esusdata.indicator.pack.c4.C4Data.condition;
import static esusdata.indicator.pack.c4.C4Data.d;
import static esusdata.indicator.pack.c4.C4Data.data;
import static esusdata.indicator.pack.c4.C4Data.isEligible;
import static esusdata.indicator.pack.c4.C4Data.met;
import static esusdata.indicator.pack.c4.C4Data.personRow;
import static esusdata.indicator.pack.c4.C4Data.registration;
import static esusdata.indicator.pack.c4.C4Data.supporting;
import static esusdata.indicator.pack.c4.C4Data.ungated;
import static esusdata.indicator.pack.c4.C4Data.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.pack.PackSupport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Findings of the integration review of C4 (one test per finding, each failing before its fix). */
class C4IntegrationReviewTest {

    private static final String OTHER_IBGE = "3550308";

    private static CanonicalProcedureEvent proc(
            String key, LocalDate date, String code, String stage, String cbo, String origin) {
        return new CanonicalProcedureEvent(
                CanonicalFixtures.ref("tb_fat_proced_atend_proced"),
                CanonicalFixtures.IBGE,
                key,
                date.toString(),
                code,
                stage,
                cbo,
                null,
                null,
                origin);
    }

    private static CanonicalMeasurement mip(String key, LocalDate date, String cbo, String weight, String height) {
        return new CanonicalMeasurement(
                CanonicalFixtures.ref("tb_fat_proced_atend"),
                CanonicalFixtures.IBGE,
                key,
                date.toString(),
                weight,
                height,
                null,
                null,
                cbo,
                "MIP",
                null,
                List.of());
    }

    // ---- 1: only MIAI and MIP procedures, in the stage the ficha asks for ------------------------

    @Test
    void finding1_miaoProceduresNeverMeetBOrE() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .add(proc("p1", d(2026, 2, 10), C4Codes.BLOOD_PRESSURE, "PERFORMED", DENTISTA, "MIAO"))
                .add(proc("p1", d(2026, 2, 10), C4Codes.HBA1C, "REQUESTED", DENTISTA, "MIAO"))
                .build());

        assertThat(met(o, "p1", "B")).isFalse();
        assertThat(met(o, "p1", "E")).isFalse();
    }

    @Test
    void finding1_mipProceduresMeetBEAndFOnlyInTheirStage() {
        RuleOutcome performed = ungated(data().diabetic("p1")
                .add(proc("p1", d(2026, 2, 10), C4Codes.BLOOD_PRESSURE, "PERFORMED", DENTISTA, "MIP"))
                .add(proc("p1", d(2026, 2, 10), C4Codes.HBA1C, "PERFORMED", DENTISTA, "MIP"))
                .add(proc("p1", d(2026, 2, 10), C4Codes.DIABETIC_FOOT, "PERFORMED", ENFERMEIRO, "MIP"))
                .build());
        RuleOutcome requested = ungated(data().diabetic("p1")
                .add(proc("p1", d(2026, 2, 10), C4Codes.BLOOD_PRESSURE, "REQUESTED", MEDICO, "MIAI"))
                .add(proc("p1", d(2026, 2, 10), C4Codes.DIABETIC_FOOT, "REQUESTED", MEDICO, "MIAI"))
                .build());

        assertThat(met(performed, "p1", "B")).isTrue();
        assertThat(met(performed, "p1", "E")).isTrue();
        assertThat(met(performed, "p1", "F")).isTrue();
        assertThat(met(requested, "p1", "B")).isFalse();
        assertThat(met(requested, "p1", "F")).isFalse();
    }

    // ---- 2: MIVDT only by ACS/TACS with a visit reason -----------------------------------------

    @Test
    void finding2_homeVisitMeasuresCountForCOnlyByAcsOrTacsWithAReason() {
        RuleOutcome o = ungated(data().diabetic("nurse")
                .add(visit("nurse", d(2025, 12, 1), ENFERMEIRO, "1", List.of("ACOMP_CONDICAO"), "80", "170"))
                .diabetic("tec")
                .add(visit("tec", d(2025, 12, 1), TEC_ENFERMAGEM, "1", List.of("ACOMP_CONDICAO"), "80", "170"))
                .diabetic("noReason")
                .add(visit("noReason", d(2025, 12, 1), ACS, "1", List.of(), "80", "170"))
                .diabetic("acs")
                .add(visit("acs", d(2025, 12, 1), ACS, "1", List.of("ACOMP_CONDICAO"), "80", "170"))
                .build());

        assertThat(met(o, "nurse", "C")).isFalse();
        assertThat(met(o, "tec", "C")).isFalse();
        assertThat(met(o, "noReason", "C")).isFalse();
        assertThat(met(o, "acs", "C")).isTrue();
    }

    // ---- 3: one fact, one supporting event (MET-32) --------------------------------------------

    @Test
    void finding3_encounterCodesAreReadOnceFromTheProcedureEvents() {
        CanonicalCareEvent encounter = care("p1", d(2026, 2, 10), MEDICO)
                .requested(C4Codes.HBA1C)
                .performed(C4Codes.DIABETIC_FOOT)
                .build();
        RuleOutcome o = ungated(data().diabetic("p1").add(encounter).build());

        assertThat(supporting(o, "p1", "E"))
                .singleElement()
                .satisfies(e -> assertThat(e.modality()).isEqualTo("MIAI"));
        assertThat(supporting(o, "p1", "F")).hasSize(1);
    }

    @Test
    void finding3_encounterArraysAloneDoNotProveAPractice() {
        CanonicalCareEvent encounter =
                care("p1", d(2026, 2, 10), MEDICO).requested(C4Codes.HBA1C).build();
        CanonicalDataset onlyEncounter = CanonicalDataset.builder()
                .add(C4Data.team(INE_ESF, CNES, "70"))
                .add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .add(encounter)
                .build();

        assertThat(met(ungated(onlyEncounter), "p1", "E")).isFalse();
    }

    // ---- 4: team rows and link evidence only for usable links ----------------------------------

    @Test
    void finding4_noTeamRowForAnOutOfScopeTypeOrAnInactiveRegistration() {
        String ine71 = "0000400001";
        String inactiveIne = "0000500001";
        RuleOutcome o = ungated(data().add(registration("t71", LINKED_ON, ine71))
                .add(new CanonicalTeam(
                        CanonicalFixtures.ref("tb_dim_equipe"),
                        CanonicalFixtures.IBGE,
                        ine71,
                        CNES,
                        "71",
                        "2024-01-01"))
                .add(activeCondition("t71", "CID10", "E11", DIAGNOSED_ON))
                .add(registration("inactive", LINKED_ON, inactiveIne, false, true, false, null, null))
                .add(activeCondition("inactive", "CID10", "E11", DIAGNOSED_ON))
                .build());

        assertThat(o.teams()).isEmpty();
        EvidenceItem noLink = personRow(o, "inactive");
        assertThat(noLink.reasonCode()).isEqualTo(C4Reasons.NO_LINK);
        assertThat(noLink.ine()).isNull();
        assertThat(noLink.cnes()).isNull();
    }

    // ---- 5: requirements, mid-month cutoff, MIP measurements, entry by encounter ---------------

    @Test
    void finding5_requirementsBindTheFichaCodesAndWindows() {
        DataRequirements r = new C4Pack().requirements(YearMonth.of(2026, 3));
        Map<String, PartRequirement> parts =
                r.parts().stream().collect(Collectors.toMap(PartRequirement::capability, Function.identity()));

        assertThat(r.canonicalSchemaVersion()).isEqualTo(DataRequirements.V2);
        assertThat(parts.get(Capabilities.PROCEDURE_PERFORMED).arrayParams().get(Capabilities.PROCEDURE_CODES))
                .containsExactly(
                        "0301100039", "0101040024", "0101040083", "0101040075", "0202010503", "ABEX008", "0301040095");
        assertThat(parts.get(Capabilities.EXAM_REQUEST_EVALUATION).arrayParams().get(Capabilities.PROCEDURE_CODES))
                .containsExactly("0202010503", "ABEX008");
        assertThat(parts.get(Capabilities.CONDITION_LIST).arrayParams())
                .containsEntry(Capabilities.CIAP_CODES, List.of("T89", "T90"))
                .containsEntry(Capabilities.CID_CODES, List.of("E10", "E11", "E14"));
        assertThat(parts.get(Capabilities.CONDITION_LIST).periodStart()).isEqualTo(LocalDate.of(2013, 1, 1));
        assertThat(parts.get(Capabilities.INDIVIDUAL_REGISTRATION).periodStart())
                .isEqualTo(LocalDate.of(2024, 4, 1));
        for (String twelve : List.of(
                Capabilities.CARE_ENCOUNTER,
                Capabilities.PROCEDURE_PERFORMED,
                Capabilities.EXAM_REQUEST_EVALUATION,
                Capabilities.HOME_VISIT,
                Capabilities.MEASUREMENT_RECORD)) {
            assertThat(parts.get(twelve).periodStart()).isEqualTo(LocalDate.of(2025, 4, 1));
        }
        assertThat(parts.get(Capabilities.TEAM).dateParams()).isEmpty();
        assertThat(parts.get(Capabilities.TEAM).arrayParams()).isEmpty();
        for (PartRequirement part : r.parts()) {
            assertThat(part.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
            if (Capabilities.TEAM.equals(part.capability())) {
                continue; // no scope date and no birth range: the team types are read whole
            }
            assertThat(part.dateParams())
                    .containsEntry(PartRequirement.BIRTH_DATE_FROM, LocalDate.of(1896, 3, 1))
                    .containsEntry(PartRequirement.BIRTH_DATE_TO, LocalDate.of(2026, 3, 31));
        }
    }

    @Test
    void finding5_eventsAfterAMidMonthCutoffDoNotCount() {
        EvaluationContext midMonth =
                new EvaluationContext(CanonicalFixtures.IBGE, YearMonth.of(2026, 3), LocalDate.of(2026, 3, 15));
        RuleOutcome onCutoff = ungated(
                data().diabetic("p1")
                        .add(C4Data.consult("p1", d(2026, 3, 15), MEDICO))
                        .build(),
                midMonth);
        RuleOutcome afterCutoff = ungated(
                data().diabetic("p1")
                        .add(C4Data.consult("p1", d(2026, 3, 16), MEDICO))
                        .build(),
                midMonth);

        assertThat(met(onCutoff, "p1", "A")).isTrue();
        assertThat(met(afterCutoff, "p1", "A")).isFalse();
    }

    @Test
    void finding5_mipWeightAndHeightOnTheSameDayMeetC() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .add(mip("p1", d(2025, 11, 3), TEC_ENFERMAGEM, "81.2", null))
                .add(mip("p1", d(2025, 11, 3), TEC_ENFERMAGEM, null, "165"))
                .build());

        assertThat(met(o, "p1", "C")).isTrue();
        assertThat(supporting(o, "p1", "C")).extracting(EvidenceItem::modality).containsOnly("MIP");
    }

    @Test
    void finding5_entryByAnEncounterInsideTheReadWindow() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(care("p1", d(2025, 11, 20), MEDICO).cid("E11.9").build())
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
    }

    static List<Record> foreignRecords() {
        return List.of(
                new CanonicalPerson(CanonicalFixtures.ref("p"), OTHER_IBGE, "p1", "1960-01-01", null, null, null),
                new CanonicalRegistration(
                        CanonicalFixtures.ref("r"),
                        OTHER_IBGE,
                        "p1",
                        "2025-01-10",
                        CNES,
                        INE_ESF,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null),
                new CanonicalTeam(CanonicalFixtures.ref("t"), OTHER_IBGE, INE_ESF, CNES, "70", "2024-01-01"),
                care("p1", d(2026, 1, 1), MEDICO).municipality(OTHER_IBGE).build(),
                new CanonicalProcedureEvent(
                        CanonicalFixtures.ref("pe"),
                        OTHER_IBGE,
                        "p1",
                        "2026-01-01",
                        C4Codes.HBA1C,
                        "REQUESTED",
                        MEDICO,
                        null,
                        null,
                        "MIAI"),
                new CanonicalHomeVisit(
                        CanonicalFixtures.ref("v"),
                        OTHER_IBGE,
                        "p1",
                        "2026-01-01",
                        ACS,
                        null,
                        null,
                        "1",
                        List.of("ACOMP_CONDICAO"),
                        null,
                        null),
                new CanonicalCondition(
                        CanonicalFixtures.ref("c"),
                        OTHER_IBGE,
                        "p1",
                        "CID10",
                        "E11",
                        "2020-01-01",
                        "0",
                        null,
                        "PROFESSIONAL",
                        MEDICO),
                new CanonicalMeasurement(
                        CanonicalFixtures.ref("m"),
                        OTHER_IBGE,
                        "p1",
                        "2026-01-01",
                        "80",
                        "170",
                        null,
                        null,
                        MEDICO,
                        "MIP",
                        null,
                        List.of()));
    }

    @ParameterizedTest
    @MethodSource("foreignRecords")
    void finding5_eng38_aRecordOfAnyKindFromAnotherMunicipalityIsRefused(Record foreign) {
        CanonicalDataset dataset = CanonicalDataset.builder().add(foreign).build();

        assertThatThrownBy(() -> new C4Pack().evaluate(dataset, CONTEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OTHER_IBGE);
    }

    // ---- 7: blank, zero and garbage are absent values -------------------------------------------

    @Test
    void finding7_zeroOrBlankMeasuresAndBlankReasonsDoNotCount() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .add(care("p1", d(2026, 2, 10), MEDICO)
                        .weight("0")
                        .height("170")
                        .bloodPressure("", "80")
                        .build())
                .add(visit("p1", d(2025, 10, 1), ACS, "1", List.of(" "), null, null))
                .add(visit("p1", d(2026, 1, 1), ACS, "1", List.of(" "), null, null))
                .build());

        assertThat(met(o, "p1", "B")).isFalse();
        assertThat(met(o, "p1", "C")).isFalse();
        assertThat(met(o, "p1", "D")).isFalse();
        assertThat(PackSupport.positive("abc")).isFalse();
        assertThat(PackSupport.positive("72.5")).isTrue();
    }

    // ---- 9: a condition status outside 0/1/2 is diagnosed ---------------------------------------

    @Test
    void finding9_unknownConditionStatusIsCountedInTheLimitations() {
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, INE_ESF))
                .add(condition("p1", "CID10", "E11", DIAGNOSED_ON, null, null, "PROFESSIONAL"))
                .build());

        assertThat(isEligible(o, "p1")).isTrue();
        assertThat(o.result().limitations()).contains(C4Scoring.UNKNOWN_STATUS.formatted(1));
    }

    // ---- 10: an undated team type is never applied -------------------------------------------

    @Test
    void finding10_teamTypeWithoutObservationDateIsIgnored() {
        String ine = "0000600001";
        RuleOutcome o = ungated(data().add(registration("p1", LINKED_ON, ine))
                .add(new CanonicalTeam(
                        CanonicalFixtures.ref("tb_dim_equipe"), CanonicalFixtures.IBGE, ine, CNES, "76", null))
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .build());

        assertThat(o.result().status()).isNotEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    // ---- 11: supporting events name their information model ------------------------------------

    @Test
    void finding11_supportingEventsCarryTheInformationModel() {
        RuleOutcome o = ungated(data().diabetic("p1")
                .add(C4Data.consult("p1", d(2026, 2, 1), MEDICO))
                .add(visit("p1", d(2025, 10, 1), ACS))
                .add(visit("p1", d(2026, 1, 1), ACS))
                .build());

        assertThat(supporting(o, "p1", "A")).extracting(EvidenceItem::modality).containsOnly("MIAI");
        assertThat(supporting(o, "p1", "D")).extracting(EvidenceItem::modality).containsOnly("MIVDT");
    }

    // ---- 6: a part not read (or read over a shorter window) is UNSUPPORTED_SOURCE ---------------

    @Test
    void finding6_aRequiredPartNotReadOrReadShorterIsUnsupportedSource() {
        DataRequirements r = new C4Pack().requirements(YearMonth.of(2026, 3));
        CanonicalDataset.Builder complete = CanonicalDataset.builder();
        CanonicalDataset.Builder shorter = CanonicalDataset.builder();
        for (PartRequirement part : r.parts()) {
            DateWindow window = new DateWindow(part.periodStart(), part.periodEndExclusive());
            complete.window(part.capability(), window);
            boolean isConditions = Capabilities.CONDITION_LIST.equals(part.capability());
            shorter.window(
                    part.capability(), isConditions ? DateWindow.lastCivilMonths(YearMonth.of(2026, 3), 120) : window);
        }
        CanonicalDataset missing = CanonicalDataset.builder()
                .window(Capabilities.CARE_ENCOUNTER, DateWindow.lastCivilMonths(YearMonth.of(2026, 3), 12))
                .add(registration("p1", LINKED_ON, INE_ESF))
                .add(activeCondition("p1", "CID10", "E11", DIAGNOSED_ON))
                .build();

        for (CanonicalDataset dataset : List.of(shorter.build(), missing)) {
            RuleOutcome o = new C4Pack().evaluate(dataset, CONTEXT);
            assertThat(o.result().status()).isEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);
            assertThat(o.result().valueText()).isNull();
            assertThat(o.result().numerator()).isNull();
            assertThat(o.result().denominator()).isNull();
            assertThat(o.result().components()).isEmpty();
            assertThat(o.evidence()).isEmpty();
        }
        assertThat(new C4Pack().evaluate(complete.build(), CONTEXT).result().status())
                .isEqualTo(IndicatorStatus.NO_DENOMINATOR);
    }
}
