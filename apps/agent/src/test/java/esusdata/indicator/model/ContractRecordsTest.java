package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The invariants of the ADR 0030 contract records, so a pack cannot build an impossible one. */
class ContractRecordsTest {

    private static final ComponentSpec PRACTICE = ComponentSpec.practice("A", "Consulta", 25, "12 meses");

    @Test
    void releaseGatesListEveryMissingGate() {
        assertThat(ReleaseGates.allComplete().isComplete()).isTrue();
        assertThat(ReleaseGates.allComplete().incompleteReasons()).isEmpty();
        assertThat(ReleaseGates.adapterOnly().incompleteReasons()).hasSize(4).noneMatch(r -> r.startsWith("Portão C"));
        assertThat(ReleaseGates.noneComplete().incompleteReasons()).hasSize(5);
    }

    @Test
    void amendedRecordsKeepTheirOriginalConstructors() {
        SourceRef ref = new SourceRef("src", "tb", "1");
        CanonicalCondition condition =
                new CanonicalCondition(ref, "3541307", "p", "CID10", "E11", "2026-01-01", "0", null, "PROFESSIONAL");
        assertThat(condition.cbo()).isNull();

        CanonicalImmunization dose =
                new CanonicalImmunization(ref, "3541307", "p", "2026-01-01", "42", "1", null, false, null, null, null);
        assertThat(dose.registrationDate()).isNull();

        CanonicalMeasurement measurement =
                new CanonicalMeasurement(ref, "3541307", "p", "2026-01-01", "70", "160", null, null, null, "MIP");
        assertThat(measurement.activityTypeCode()).isNull();
        assertThat(measurement.healthPracticeCodes()).isEmpty();

        List<String> practices = new java.util.ArrayList<>(List.of("2"));
        CanonicalMeasurement copied = new CanonicalMeasurement(
                ref, "3541307", "p", "2026-01-01", "70", "160", null, null, null, "MIAC", "05", practices);
        practices.add("30");
        assertThat(copied.healthPracticeCodes()).containsExactly("2");
        assertThat(EvidenceDecision.values()).contains(EvidenceDecision.PRACTICE_AMBIGUOUS);
    }

    @Test
    void aComponentWithoutDenominatorIsUndefinedNotZero() {
        ResultComponent empty = ResultComponent.of(PRACTICE, BigInteger.ZERO, BigInteger.ZERO);
        assertThat(empty.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(empty.value()).isNull();
        ResultComponent half = ResultComponent.of(PRACTICE, BigInteger.ONE, BigInteger.TWO);
        assertThat(half.value()).isEqualTo(ExactRatio.of(1, 2));
        assertThatThrownBy(() -> ResultComponent.of(PRACTICE, BigInteger.TWO, BigInteger.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComponentSpec.practice("A", "A", -1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evidenceNeedsItsSourceRecordOrItsSubjectKey() {
        SourceRef ref = new SourceRef("pec-1", "tb_fat_visita_domiciliar", "9");
        assertThatThrownBy(() -> new EvidenceItem(
                        EvidenceSubjectKind.EVENT,
                        null,
                        null,
                        null,
                        null,
                        EvidenceDecision.SUPPORTING_EVENT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceItem(
                        EvidenceSubjectKind.PERSON,
                        " ",
                        ref,
                        null,
                        "A",
                        EvidenceDecision.PRACTICE_MET,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        EvidenceItem person = new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                "p-1",
                null,
                "2026-03-31",
                "A",
                EvidenceDecision.PRACTICE_MET,
                null,
                BigInteger.valueOf(25),
                "1234567",
                "0000000001",
                null,
                null);
        assertThat(person.points()).isEqualTo(BigInteger.valueOf(25));
    }

    @Test
    void requirementsRejectEmptyWindowsDuplicatesAndMultiPartV1() {
        PartRequirement part = PartRequirement.of("citizen", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));
        assertThatThrownBy(() -> PartRequirement.of("citizen", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataRequirements(DataRequirements.V2, List.of(part, part)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataRequirements(
                        DataRequirements.V1,
                        List.of(
                                part,
                                PartRequirement.of("home_visit", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataRequirements(3, List.of(part))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataRequirements(DataRequirements.V2, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void personScopedPartsBindAnInclusiveBirthRange() {
        SortedMap<String, List<String>> codes = new TreeMap<>();
        codes.put("immunobiological_codes", List.of("33"));
        PartRequirement part = PartRequirement.personScoped(
                "immunization_history",
                DateWindow.lastCivilMonths(YearMonth.of(2026, 3), 12),
                new DateWindow(LocalDate.of(1900, 1, 1), LocalDate.of(1966, 4, 1)),
                codes);
        assertThat(part.dateParams())
                .containsEntry(PartRequirement.BIRTH_DATE_FROM, LocalDate.of(1900, 1, 1))
                .containsEntry(PartRequirement.BIRTH_DATE_TO, LocalDate.of(1966, 3, 31));
        assertThat(part.arrayParams()).containsEntry("immunobiological_codes", List.of("33"));
        assertThat(part.periodStart()).isEqualTo(LocalDate.of(2025, 4, 1));
    }

    @Test
    void contextsAndBudgetsRefuseNonsense() {
        assertThatThrownBy(() -> EvaluationContext.endOfMonth("123", YearMonth.of(2026, 3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(EvaluationContext.endOfMonth("3541307", YearMonth.of(2026, 2))
                        .dataCutoff())
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThatThrownBy(() -> new BudgetHint(0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(BudgetHint.engineeringDefault().maxRows()).isEqualTo(200_000);
    }

    @Test
    void cboGroupsMatchFamiliesAndOccupations() {
        CboGroups groups = CboGroups.of("2235", "2251", "5151-05");
        assertThat(groups.matches("223505")).isTrue();
        assertThat(groups.matches("225142")).isTrue();
        assertThat(groups.matches("515105")).isTrue();
        assertThat(groups.matches("515110")).isFalse();
        assertThat(groups.matches("322205")).isFalse();
        assertThat(groups.matches(null)).isFalse();
        assertThat(groups.matches("2235C3")).isTrue();
        assertThat(CboGroups.of("2231F9").matches("2231F9")).isTrue();
        assertThat(CboGroups.of("2231F9").matches("223109")).isFalse();
        assertThatThrownBy(() -> CboGroups.of("22")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordKindsRoundTripTheirWireNames() {
        for (RecordKind kind : RecordKind.values()) {
            assertThat(RecordKind.fromWireName(kind.wireName())).isEqualTo(kind);
        }
        assertThat(RecordKind.CARE_EVENT.wireName()).isEqualTo("care_event");
        assertThatThrownBy(() -> RecordKind.fromWireName("encounter")).isInstanceOf(IllegalArgumentException.class);
    }
}
