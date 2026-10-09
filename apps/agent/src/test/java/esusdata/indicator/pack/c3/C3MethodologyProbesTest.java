package esusdata.indicator.pack.c3;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.Observability;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.indicator.reconciliation.SyntheticProbeContexts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The end-of-pregnancy probe of C3 on invented pregnancies of the first quadrimestre of 2026. The
 * scenario ends the pregnancy of the baseline on 2026-01-20 (a W78 resolved in the LPC), so the
 * puerperium reaches 2026-03-03 and the puerperal consultation of 2026-01-30 is an I practice; the
 * candidate readings end it on 2026-07-22 (DUM + 294), which turns that consultation into one more
 * pregnancy consultation and the episode into one that is still active in April.
 */
class C3MethodologyProbesTest {

    private static final Quadrimestre Q1 = new Quadrimestre(2026, 1);
    private static final C3Pack PACK = new C3Pack();
    private static final MethodologyProbe PROBE = C3MethodologyProbes.all().getFirst();

    private static final String REVISED = C3Fixtures.INE;
    private static final String OTHER = C3Fixtures.OTHER_INE;
    private static final String ESF = "eSF";
    private static final String CIAP2 = "CIAP2";
    private static final String ACTIVE = "0";
    private static final String RESOLVED_STATUS = "2";

    /** DUM + 294 is 2026-07-22, after every competência of the quadrimestre. */
    private static final LocalDate DUM = LocalDate.of(2025, 10, 1);

    /** DUM + 111: the baseline ends the pregnancy here, and its puerperium on 2026-03-03. */
    private static final LocalDate RESOLVED = LocalDate.of(2026, 1, 20);

    /** A pregnancy of the revision team whose W78 is resolved on {@link #RESOLVED}. */
    private static final String RESOLVED_PERSON = "gestante-resolvida";

    private static PackProbeContext context(List<Record> records, Map<String, String> revisionTeams) {
        List<PackInput> inputs = Q1.months().stream()
                .map(month -> new PackInput(PACK, C3Fixtures.dataset(month, records), C3Fixtures.context(month)))
                .toList();
        return SyntheticProbeContexts.pack(inputs, revisionTeams);
    }

    private static ProbeResult probe(List<Record> records, Map<String, String> revisionTeams) {
        return PROBE.evaluate(context(records, revisionTeams));
    }

    /**
     * A linked pregnancy with DUM {@link #DUM}: a nurse's W78 consultation with the DUM, the W78
     * problem of the LPC with the given status and resolution, and a puerperal consultation ten
     * days after {@link #RESOLVED}.
     */
    private static List<Record> pregnancy(String person, String ine, String status, LocalDate resolved) {
        List<Record> records = new ArrayList<>(C3Fixtures.linked(person, ine));
        records.add(C3Fixtures.anchor(person, DUM.plusDays(30), DUM));
        records.add(C3Fixtures.condition(
                person, CIAP2, C3Codes.PREGNANCY_CONDITION_CIAP, DUM.plusDays(30), status, resolved));
        records.add(C3Fixtures.puerperal(person, RESOLVED.plusDays(10)));
        return records;
    }

    /** One record of every kind the rule reads, with a W78 that nothing ends and an outcome it ignores. */
    private static List<Record> everyKind(String person, String ine) {
        List<Record> records = new ArrayList<>(pregnancy(person, ine, ACTIVE, null));
        for (int day : List.of(60, 70, 80)) {
            records.add(C3Fixtures.visit(person, DUM.plusDays(day), C3Fixtures.ACS));
        }
        records.add(C3Fixtures.dtpa(person, DUM.plusDays(150)));
        for (int day = 100; day < 107; day++) {
            records.add(C3Fixtures.bloodPressure(person, DUM.plusDays(day), C3Fixtures.NURSE));
            records.add(C3Fixtures.anthropometry(person, DUM.plusDays(day)));
        }
        records.addAll(C3Fixtures.tests(
                person,
                DUM.plusDays(60),
                C3Fixtures.SYPHILIS,
                C3Fixtures.HIV,
                C3Fixtures.HEPATITIS_B,
                C3Fixtures.HEPATITIS_C));
        records.add(C3Fixtures.dental(person, DUM.plusDays(70)));
        records.add(C3Fixtures.outcome(person, DUM.plusDays(300)));
        return records;
    }

    @Test
    void theProbeIsTheEndDateOfC3AndTheOnlyOneOfThePack() {
        assertThat(C3MethodologyProbes.all()).hasSize(1);
        assertThat(PROBE.id()).isEqualTo("c3.episode.end-date");
        assertThat(PROBE.packs()).containsExactly(C3Pack.ID);
    }

    @Test
    void aW78ResolvedBeforeTheMaximumIsCountedOnItsEpisodeAndItsTeam() {
        List<Record> records = pregnancy(RESOLVED_PERSON, REVISED, RESOLVED_STATUS, RESOLVED);

        ProbeResult result = probe(records, Map.of(REVISED, ESF));

        assertThat(result.probeId()).isEqualTo("c3.episode.end-date");
        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.divergent()).hasValue(1);
        assertThat(result.reason()).contains("registered outcome date").contains("exact over it");
        assertThat(result.limitations()).containsExactly("oor.c3.outcome-date-field");
        assertThat(result.localDetail()).anyMatch(line -> line.contains(REVISED));
    }

    @Test
    void aW78ThatEndsNoPregnancyLeavesTheCountsAtZeroButNeverComplete() {
        List<Record> records = new ArrayList<>(everyKind("gestante-calma", REVISED));
        records.addAll(pregnancy("gestante-tardia", REVISED, RESOLVED_STATUS, DUM.plusDays(300)));
        records.add(C3Fixtures.person("gestante-falecida", RESOLVED.minusDays(5)));
        records.add(C3Fixtures.registration("gestante-falecida", C3Fixtures.LINKED_ON, REVISED, null));
        records.add(C3Fixtures.anchor("gestante-falecida", DUM.plusDays(30), DUM));

        ProbeResult result = probe(records, Map.of(REVISED, ESF));

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aResolutionOnATeamOutsideTheRevisionIsNotCounted() {
        List<Record> records = pregnancy(RESOLVED_PERSON, OTHER, RESOLVED_STATUS, RESOLVED);

        ProbeResult outside = probe(records, Map.of(REVISED, ESF));
        ProbeResult inside = probe(records, Map.of(OTHER, ESF));

        assertThat(outside.affected()).hasValue(0);
        assertThat(outside.divergent()).hasValue(0);
        assertThat(outside.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(inside.affected()).hasValue(1);
        assertThat(inside.divergent()).hasValue(1);
    }

    @Test
    void aRegisteredOutcomeIsMeasuredThroughTheE25ReadingAndItsTeamJoinsTheUnion() {
        List<Record> records = new ArrayList<>(pregnancy(RESOLVED_PERSON, OTHER, RESOLVED_STATUS, RESOLVED));
        records.addAll(pregnancy("gestante-desfecho", REVISED, ACTIVE, null));
        records.add(C3Fixtures.outcome("gestante-desfecho", RESOLVED));

        ProbeResult both = probe(records, Map.of(REVISED, ESF, OTHER, ESF));
        ProbeResult outcomeOnly = probe(records, Map.of(REVISED, ESF));
        ProbeResult resolutionOnly = probe(records, Map.of(OTHER, ESF));

        assertThat(both.affected()).hasValue(2);
        assertThat(both.divergent()).hasValue(2);
        assertThat(outcomeOnly.affected()).hasValue(1);
        assertThat(outcomeOnly.divergent()).hasValue(1);
        assertThat(resolutionOnly.affected()).hasValue(1);
        assertThat(resolutionOnly.divergent()).hasValue(1);
    }

    @Test
    void aMonthThatTheExtractDoesNotCoverMakesTheProbeUnobservableNotZero() {
        List<PackInput> inputs = Q1.months().stream()
                .map(month -> new PackInput(PACK, CanonicalDataset.builder().build(), C3Fixtures.context(month)))
                .toList();

        ProbeResult result = PROBE.evaluate(SyntheticProbeContexts.pack(inputs, Map.of(REVISED, ESF)));

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.divergent()).isEmpty();
        assertThat(result.reason()).contains("does not cover what C3 reads");
    }

    @Test
    void theVersionedFormMasksSmallCountsAndNeverCarriesAnIne() {
        ProbeResult result =
                probe(pregnancy(RESOLVED_PERSON, REVISED, RESOLVED_STATUS, RESOLVED), Map.of(REVISED, ESF));

        assertThat(result.versionedForm())
                .containsEntry("probe_id", "c3.episode.end-date")
                .containsEntry("observability", "COMPLETE")
                .containsEntry("affected", "<10")
                .containsEntry("divergent", "<10")
                .containsKey("reason");
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(REVISED));
        assertThat(result.toString()).doesNotContain(REVISED);
        assertThat(result.maskedSummary()).doesNotContain(REVISED);
    }

    @Test
    void theE26ReadingKeepsTheOutcomesAndTheE25ReadingDropsThem() {
        List<Record> records = new ArrayList<>(pregnancy(RESOLVED_PERSON, REVISED, RESOLVED_STATUS, RESOLVED));
        records.add(C3Fixtures.outcome(RESOLVED_PERSON, RESOLVED));
        CanonicalDataset data = C3Fixtures.dataset(YearMonth.of(2026, 1), records);

        assertThat(C3MethodologyProbes.endingAtOutcomeOr294(data).pregnancyOutcomes())
                .isEqualTo(data.pregnancyOutcomes())
                .hasSize(1);
        assertThat(C3MethodologyProbes.endingAt294(data).pregnancyOutcomes()).isEmpty();
    }

    @Test
    void bothReadingsOnlyTakeTheResolutionDateOffTheW78AndCopyEverythingElse() {
        List<Record> records = new ArrayList<>(everyKind("gestante-calma", REVISED));
        records.addAll(pregnancy(RESOLVED_PERSON, REVISED, RESOLVED_STATUS, RESOLVED));
        records.add(C3Fixtures.condition(
                RESOLVED_PERSON, CIAP2, "W79", DUM.plusDays(30), RESOLVED_STATUS, RESOLVED.plusDays(1)));
        CanonicalDataset data = C3Fixtures.dataset(YearMonth.of(2026, 1), records);

        for (CanonicalDataset reading :
                List.of(C3MethodologyProbes.endingAt294(data), C3MethodologyProbes.endingAtOutcomeOr294(data))) {
            assertThat(reading.windows()).isEqualTo(data.windows());
            assertThat(reading.persons()).isEqualTo(data.persons());
            assertThat(reading.registrations()).isEqualTo(data.registrations());
            assertThat(reading.teams()).isEqualTo(data.teams());
            assertThat(reading.careEvents()).isEqualTo(data.careEvents());
            assertThat(reading.procedureEvents()).isEqualTo(data.procedureEvents());
            assertThat(reading.homeVisits()).isEqualTo(data.homeVisits());
            assertThat(reading.immunizations()).isEqualTo(data.immunizations());
            assertThat(reading.measurements()).isEqualTo(data.measurements());
            assertThat(reading.conditions()).hasSameSizeAs(data.conditions());
            assertThat(reading.conditions())
                    .filteredOn(condition -> C3Codes.PREGNANCY_CONDITION_CIAP.equals(condition.code()))
                    .allSatisfy(
                            condition -> assertThat(condition.resolvedDate()).isNull());
            assertThat(reading.conditions())
                    .filteredOn(condition -> "W79".equals(condition.code()))
                    .extracting(CanonicalCondition::resolvedDate)
                    .containsExactly(RESOLVED.plusDays(1).toString());
        }
    }
}
