package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ValidatedReference.UniverseConfidence;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The contract between the probes and what runs them (spec 2026-10-08 §9.4): {@link ProbeResult}
 * keeps the unobservable apart from zero, masks what it shows and keeps the local detail local, and
 * a {@link ProbeContext} that does not hold together is refused before a probe reads it. The probes
 * here are fakes: the real ones come with the production profiles.
 */
class MethodologyProbeContractTest {

    private static final String MUNICIPALITY = "3541307";
    private static final String FINGERPRINT = "sha256:" + "a".repeat(64);
    private static final Quadrimestre Q1_2026 = new Quadrimestre(2026, 1);
    private static final String SIAPS_Q1_2026 = "2026Q1";
    private static final String PROBE = "fx.cohort.anchor-date";
    private static final String INE = "0000012345";
    private static final String NONE_BECAUSE = "the canonical dataset has no PA field";
    private static final List<String> NO_DETAIL = List.of();

    /** The id of the first pack of the Portão D, whose rule the contexts below run. */
    private static final String C1 = new C1Pack().descriptor().id();

    /** A context that does not hold together, and the sentence that says why. */
    record BadContext(String name, Supplier<ProbeContext> build, String says) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** A probe whose answer the test fixes; it only looks at the shape of the context. */
    private record FakeProbe(String id, Set<String> packs, ProbeResult answer) implements MethodologyProbe {
        @Override
        public ProbeResult evaluate(ProbeContext context) {
            return switch (context) {
                case PackProbeContext pack -> answer;
                case NotaFinalProbeContext notaFinal -> ProbeResult.none(id, "the Nota Final is not probed here");
            };
        }
    }

    // ---- the contexts

    private static IndicatorRule c1() {
        return new C1Pack();
    }

    private static GatePack pack(IndicatorRule rule) {
        return GatePack.byPackId(rule.descriptor().id()).orElseThrow();
    }

    private static List<PackInput> inputs(IndicatorRule rule, String municipality) {
        return Q1_2026.months().stream()
                .map(month -> new PackInput(
                        rule, CanonicalDataset.builder().build(), EvaluationContext.endOfMonth(municipality, month)))
                .toList();
    }

    private static IndicatorResult result(PackDescriptor pack, YearMonth month) {
        return new IndicatorResult(
                IndicatorStatus.NO_DENOMINATOR,
                null,
                null,
                null,
                pack.denominatorKind(),
                null,
                month.toString(),
                pack.ruleVersion(),
                month.atEndOfMonth().toString(),
                MUNICIPALITY,
                List.of(),
                pack.calculationPolicyVersion());
    }

    private static List<RuleOutcome> baseline(IndicatorRule rule) {
        return Q1_2026.months().stream()
                .map(month -> new RuleOutcome(result(rule.descriptor(), month), List.of(), List.of()))
                .toList();
    }

    private static ValidatedReference reference(GatePack pack, String municipality, String quadrimestre) {
        return new ValidatedReference(
                pack,
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                municipality,
                quadrimestre,
                UniverseConfidence.OFFICIAL,
                List.of(),
                Map.of(SiapsParser.ESF, ClassCounts.EMPTY, SiapsParser.EAP, ClassCounts.EMPTY),
                List.of(),
                Map.of());
    }

    private static SiapsReferenceManifest manifest(
            GatePack pack, String municipality, String quadrimestre, SourceKind kind) {
        String id = SiapsReferenceManifest.referenceId("SP", municipality, quadrimestre, pack, kind, 1);
        return new SiapsReferenceManifest(
                id,
                kind,
                municipality,
                quadrimestre,
                OffsetDateTime.parse("2026-10-08T12:00:00-03:00"),
                LocalDateTime.parse("2026-10-08T11:00:00"),
                OfficialStatus.FINAL,
                "SIAPS fixture",
                SiapsReferenceManifest.sourceFilenameOf("a".repeat(64)),
                "a".repeat(64),
                "b".repeat(64),
                "siaps-team-export@1",
                3,
                List.of(pack.siapsCode()),
                List.of(SiapsParser.EAP, SiapsParser.ESF),
                false,
                List.of());
    }

    private static SiapsReferenceManifest manifest(GatePack pack) {
        return manifest(pack, MUNICIPALITY, SIAPS_Q1_2026, SourceKind.OFFICIAL_TEAM_EXPORT_CSV);
    }

    private static PackProbeContext packContext() {
        IndicatorRule rule = c1();
        return new PackProbeContext(
                Q1_2026,
                inputs(rule, MUNICIPALITY),
                baseline(rule),
                reference(pack(rule), MUNICIPALITY, SIAPS_Q1_2026),
                manifest(pack(rule)),
                FINGERPRINT);
    }

    private static PackProbeContext packContext(
            List<PackInput> inputs,
            List<RuleOutcome> baseline,
            ValidatedReference reference,
            SiapsReferenceManifest manifest,
            String fingerprint) {
        return new PackProbeContext(Q1_2026, inputs, baseline, reference, manifest, fingerprint);
    }

    /** Every pack of C1 to C7, every month, one team in the first: a complete quadrimestre. */
    private static Map<String, Map<YearMonth, List<TeamResult>>> monthlyResults() {
        Map<String, Map<YearMonth, List<TeamResult>>> byPack = new LinkedHashMap<>();
        for (GatePack pack : GatePack.all()) {
            Map<YearMonth, List<TeamResult>> months = new LinkedHashMap<>();
            for (YearMonth month : Q1_2026.months()) {
                months.put(month, List.of());
            }
            byPack.put(pack.packId(), months);
        }
        YearMonth january = Q1_2026.firstMonth();
        IndicatorResult first = result(c1().descriptor(), january);
        byPack.get(C1).put(january, List.of(new TeamResult(INE, null, first)));
        return byPack;
    }

    private static NotaFinalProbeContext notaFinalContext(
            Map<String, Map<YearMonth, List<TeamResult>>> monthlyResults, ValidatedReference reference) {
        return new NotaFinalProbeContext(
                Q1_2026, monthlyResults, reference, manifest(GatePack.NOTA_FINAL), FINGERPRINT);
    }

    private static NotaFinalProbeContext notaFinalContext() {
        return notaFinalContext(monthlyResults(), reference(GatePack.NOTA_FINAL, MUNICIPALITY, SIAPS_Q1_2026));
    }

    // ---- what a probe sees of the official side

    @Test
    void aProbeSeesTheTeamsOfTheRevisionButNotTheirOfficialClasses() {
        IndicatorRule rule = c1();
        ValidatedReference reference = new ValidatedReference(
                pack(rule),
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                MUNICIPALITY,
                SIAPS_Q1_2026,
                UniverseConfidence.OFFICIAL,
                List.of(),
                Map.of(
                        SiapsParser.ESF,
                        ClassCounts.EMPTY.plus(Classification.OTIMO),
                        SiapsParser.EAP,
                        ClassCounts.EMPTY),
                List.of(new SiapsSnapshot.Team(INE, SiapsParser.ESF)),
                Map.of(INE, Classification.OTIMO));

        PackProbeContext context =
                packContext(inputs(rule, MUNICIPALITY), baseline(rule), reference, manifest(pack(rule)), FINGERPRINT);

        assertThat(context.revisionTeams()).containsExactly(Map.entry(INE, SiapsParser.ESF));
        assertThat(packContext().revisionTeams()).isEmpty();
    }

    // ---- the result of a probe

    @Test
    void aFakeProbeReturningNoneIsPreservedAsUnobservableNotZero() {
        MethodologyProbe probe = new FakeProbe(PROBE, Set.of(C1), ProbeResult.none(PROBE, NONE_BECAUSE));

        ProbeResult result = probe.evaluate(packContext());

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.affected()).isEmpty();
        assertThat(result.divergent()).isEmpty();
        assertThat(result.maskedSummary()).isEqualTo("not observable");
        // masking turns a zero into "<10", so the absence is what proves it was not coerced
        assertThat(result.versionedForm())
                .doesNotContainKeys("affected", "divergent")
                .containsEntry("observability", "NONE")
                .containsEntry("reason", NONE_BECAUSE);
        ProbeResult found = ProbeResult.complete(PROBE, 0, 0, List.of());
        assertThat(found.affected()).hasValue(0);
        assertThat(found.divergent()).hasValue(0);
        assertThat(found.versionedForm()).containsKeys("affected", "divergent");
        assertThat(result).isNotEqualTo(found);
        assertThat(result.versionedForm()).isNotEqualTo(found.versionedForm());
    }

    @Test
    void anUnobservableResultCannotBeBuiltWithCountsAndAnObservedOneCannotBeBuiltWithout() {
        OptionalInt absent = OptionalInt.empty();
        OptionalInt zero = OptionalInt.of(0);
        OptionalInt one = OptionalInt.of(1);
        for (Observability counted : List.of(Observability.COMPLETE, Observability.PARTIAL)) {
            assertThatThrownBy(
                            () -> new ProbeResult(PROBE, counted, absent, absent, NONE_BECAUSE, List.of(), NO_DETAIL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("counts both affected and divergent");
            assertThatThrownBy(() -> new ProbeResult(PROBE, counted, one, absent, NONE_BECAUSE, List.of(), NO_DETAIL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("counts both affected and divergent");
        }
        assertThatThrownBy(() ->
                        new ProbeResult(PROBE, Observability.NONE, zero, zero, NONE_BECAUSE, List.of(), NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("an unobservable probe is not one that found zero");
        assertThatThrownBy(() ->
                        new ProbeResult(PROBE, Observability.NONE, zero, absent, NONE_BECAUSE, List.of(), NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialAndNoneRequireAReason() {
        for (String reason : new String[] {null, "", "  ", "\t\n"}) {
            assertThatThrownBy(() -> ProbeResult.partial(PROBE, 1, 0, reason, NO_DETAIL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PARTIAL and needs a reason");
            assertThatThrownBy(() -> ProbeResult.none(PROBE, reason))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("NONE and needs a reason");
        }

        ProbeResult partial = ProbeResult.partial(PROBE, 25, 12, "  the PA of 2 of 4 months is absent  ", List.of());
        ProbeResult complete = ProbeResult.complete(PROBE, 25, 12, List.of());

        assertThat(partial.reason()).isEqualTo("the PA of 2 of 4 months is absent");
        assertThat(partial.versionedForm()).containsEntry("reason", "the PA of 2 of 4 months is absent");
        assertThat(complete.reason()).isEmpty();
        assertThat(complete.versionedForm()).doesNotContainKey("reason");
    }

    @Test
    void maskedSummaryNeverShowsCountsBelowTen() {
        String standaloneSingleDigit = "(?<![0-9])[0-9](?![0-9])";
        for (int count = 0; count < 10; count++) {
            ProbeResult complete = ProbeResult.complete(PROBE, count, count, List.of());
            ProbeResult partial = ProbeResult.partial(PROBE, count, count, NONE_BECAUSE, List.of());

            assertThat(complete.maskedSummary()).isEqualTo("affected <10; divergent <10");
            assertThat(partial.maskedSummary()).isEqualTo("partial (lower bounds): affected <10; divergent <10");
            for (ProbeResult result : List.of(complete, partial)) {
                assertThat(result.maskedSummary()).doesNotContainPattern(standaloneSingleDigit);
                assertThat(result.versionedForm())
                        .containsEntry("affected", "<10")
                        .containsEntry("divergent", "<10");
                assertThat(result.toString()).doesNotContainPattern(standaloneSingleDigit);
            }
        }

        ProbeResult mixed = ProbeResult.complete(PROBE, 9, 10, List.of());
        ProbeResult large = ProbeResult.complete(PROBE, 123, 1000, List.of());
        ProbeResult partial = ProbeResult.partial(PROBE, 3, 12, NONE_BECAUSE, List.of());

        assertThat(mixed.maskedSummary()).isEqualTo("affected <10; divergent 10");
        assertThat(large.maskedSummary()).isEqualTo("affected 123; divergent 1000");
        assertThat(large.versionedForm()).containsEntry("affected", "123").containsEntry("divergent", "1000");
        assertThat(partial.maskedSummary()).isEqualTo("partial (lower bounds): affected <10; divergent 12");
        // the raw counts stay available to the evaluator, which needs them exact
        assertThat(mixed.affected()).hasValue(9);
        assertThat(mixed.divergent()).hasValue(10);
    }

    @Test
    void aChannelOfADeclaredLimitationLeavesTheProbeCompleteAndNamed() {
        List<String> limitations = List.of("oor.l6.bp-home-visit", "oor.l5.bp-collective-participant");

        ProbeResult result = ProbeResult.completeWithin(PROBE, 14, 0, limitations, NONE_BECAUSE, NO_DETAIL);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(14);
        assertThat(result.divergent()).hasValue(0);
        assertThat(result.limitations()).containsExactly("oor.l5.bp-collective-participant", "oor.l6.bp-home-visit");
        assertThat(result.maskedSummary())
                .isEqualTo("affected 14; divergent <10; within oor.l5.bp-collective-participant, oor.l6.bp-home-visit");
        assertThat(result.versionedForm().keySet())
                .containsExactly("probe_id", "observability", "affected", "divergent", "limitations", "reason");
        assertThat(result.versionedForm())
                .containsEntry("limitations", "oor.l5.bp-collective-participant, oor.l6.bp-home-visit");
        assertThat(ProbeResult.complete(PROBE, 14, 0, NO_DETAIL).versionedForm())
                .doesNotContainKey("limitations");
    }

    @Test
    void aDeclaredLimitationIsNamedByItsIdAndSaysWhatItLeavesOut() {
        List<String> limitation = List.of("oor.l6.bp-home-visit");

        assertThatThrownBy(() -> ProbeResult.completeWithin(PROBE, 1, 1, List.of(), NONE_BECAUSE, NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no declared limitation");
        assertThatThrownBy(() -> ProbeResult.completeWithin(PROBE, 1, 1, limitation, " ", NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("what they leave out");
        assertThatThrownBy(() ->
                        ProbeResult.completeWithin(PROBE, 1, 1, List.of("l6.bp-home-visit"), NONE_BECAUSE, NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oor.l6.bp-home-visit");
        assertThatThrownBy(
                        () -> ProbeResult.completeWithin(PROBE, 1, 1, List.of("oor.L6 visit"), NONE_BECAUSE, NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localDetailIsNeverPartOfTheVersionedForm() {
        List<String> detail = List.of(INE + " eSF: BOM -> REGULAR", "subject 9f2c41: excluded");
        ProbeResult complete = ProbeResult.complete(PROBE, 25, 12, detail);
        ProbeResult partial = ProbeResult.partial(PROBE, 25, 12, NONE_BECAUSE, detail);

        assertThat(complete.localDetail()).containsExactlyElementsOf(detail);
        assertThat(complete.versionedForm().keySet())
                .containsExactly("probe_id", "observability", "affected", "divergent");
        assertThat(partial.versionedForm().keySet())
                .containsExactly("probe_id", "observability", "affected", "divergent", "reason");
        for (ProbeResult result : List.of(complete, partial)) {
            assertThat(result.versionedForm().values())
                    .noneMatch(value -> value.contains(INE) || value.contains("9f2c41"));
            assertThat(result.maskedSummary()).doesNotContain(INE).doesNotContain("9f2c41");
            assertThat(result.toString()).doesNotContain(INE).doesNotContain("9f2c41");
        }
        // the detail is the only difference between these two, and it never reaches the versioned form
        ProbeResult bare = ProbeResult.complete(PROBE, 25, 12, List.of());
        assertThat(bare.versionedForm()).isEqualTo(complete.versionedForm());
        assertThat(bare).isNotEqualTo(complete);
    }

    @Test
    void theVersionedFormAndTheLocalDetailCannotBeChangedAfterwards() {
        List<String> detail = new ArrayList<>(List.of("one"));
        ProbeResult result = ProbeResult.complete(PROBE, 25, 12, detail);
        detail.add("two");
        Map<String, String> form = result.versionedForm();
        List<String> kept = result.localDetail();

        assertThat(kept).containsExactly("one");
        assertThatThrownBy(() -> form.put("ine", INE)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> kept.add("two")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aResultNeedsTheIdOfAStableProbeAndNoNegativeCount() {
        assertThatThrownBy(() -> ProbeResult.none("Anchor", NONE_BECAUSE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probe id must look like");
        assertThatThrownBy(() -> ProbeResult.none(null, NONE_BECAUSE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProbeResult.complete(PROBE, -1, 0, NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative count");
        assertThatThrownBy(() -> ProbeResult.complete(PROBE, 0, -1, NO_DETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative count");
    }

    // ---- the contexts

    @Test
    void aPackContextExposesTheFourMonthsInOrderAndWhatToCompareThemWith() {
        PackProbeContext context = packContext();

        assertThat(context.packId()).isEqualTo(C1);
        assertThat(context.ruleVersion()).isEqualTo(c1().descriptor().ruleVersion());
        assertThat(context.quadrimestre()).isEqualTo(Q1_2026);
        assertThat(context.inputs())
                .extracting(input -> input.context().competencia())
                .containsExactlyElementsOf(Q1_2026.months());
        assertThat(context.baseline()).hasSize(4);
        assertThat(context.reference().pack().packId()).isEqualTo(C1);
        assertThat(context.manifest().quadrimestre()).isEqualTo(SIAPS_Q1_2026);
        assertThat(context.localSourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void aContextKeepsItsOwnCopyOfWhatItWasBuiltFrom() {
        IndicatorRule rule = c1();
        List<PackInput> inputs = new ArrayList<>(inputs(rule, MUNICIPALITY));
        List<RuleOutcome> baseline = new ArrayList<>(baseline(rule));
        PackProbeContext pack = packContext(
                inputs,
                baseline,
                reference(pack(rule), MUNICIPALITY, SIAPS_Q1_2026),
                manifest(pack(rule)),
                FINGERPRINT);
        Map<String, Map<YearMonth, List<TeamResult>>> results = monthlyResults();
        NotaFinalProbeContext notaFinal =
                notaFinalContext(results, reference(GatePack.NOTA_FINAL, MUNICIPALITY, SIAPS_Q1_2026));

        inputs.removeFirst();
        baseline.clear();
        results.get(C1).put(YearMonth.of(2026, 5), List.of());
        results.remove(C1);

        assertThat(pack.inputs()).hasSize(4);
        assertThat(pack.baseline()).hasSize(4);
        assertThat(notaFinal.monthlyResults()).containsKey(C1);
        assertThat(notaFinal.monthlyResults().get(C1)).hasSize(4);
        List<PackInput> keptInputs = pack.inputs();
        Map<YearMonth, List<TeamResult>> months = notaFinal.monthlyResults().get(C1);
        List<TeamResult> january = months.get(Q1_2026.firstMonth());
        YearMonth may = YearMonth.of(2026, 5);
        List<TeamResult> noTeams = List.of();
        assertThatThrownBy(keptInputs::removeFirst).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> months.put(may, noTeams)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(january::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aNotaFinalContextHoldsTheSevenPacksWholeQuadrimestre() {
        NotaFinalProbeContext context = notaFinalContext();

        assertThat(context.packId()).isEqualTo(ComponentIII.ID);
        assertThat(context.ruleVersion()).isEqualTo(ComponentIII.RULE_VERSION);
        assertThat(context.monthlyResults().keySet())
                .containsExactlyElementsOf(
                        GatePack.all().stream().map(GatePack::packId).toList());
        assertThat(context.monthlyResults().get(C1).get(Q1_2026.firstMonth()))
                .extracting(TeamResult::ine)
                .containsExactly(INE);
        assertThat(context.reference().pack()).isEqualTo(GatePack.NOTA_FINAL);
        assertThat(context.manifest().indicatorCodes()).containsExactly(GatePack.NOTA_FINAL_CODE);
    }

    @Test
    void theContextIsSealedToThePackAndNotaFinalShapesAndAProbeSwitchesOverThemWithoutADefault() {
        MethodologyProbe probe =
                new FakeProbe(PROBE, Set.of(C1, ComponentIII.ID), ProbeResult.complete(PROBE, 1, 0, List.of()));

        assertThat(ProbeContext.class.isSealed()).isTrue();
        assertThat(ProbeContext.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(PackProbeContext.class, NotaFinalProbeContext.class);
        assertThat(probe.evaluate(packContext()).observability()).isEqualTo(Observability.COMPLETE);
        assertThat(probe.evaluate(notaFinalContext()).observability()).isEqualTo(Observability.NONE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentPackContexts")
    void aPackContextThatDoesNotHoldTogetherIsRefusedBeforeAProbeReadsIt(BadContext bad) {
        assertThatThrownBy(bad.build()::get)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(bad.says());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentNotaFinalContexts")
    void aNotaFinalContextThatDoesNotHoldTogetherIsRefusedBeforeAProbeReadsIt(BadContext bad) {
        assertThatThrownBy(bad.build()::get)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(bad.says());
    }

    // ---- the probes of a profile

    /**
     * How a runner uses the profile: every probe it requires is found by id, serves the pack, and
     * answers with its own id. A probe it cannot find is a gap, never an answer.
     */
    @Test
    void everyProbeAProfileRequiresIsFoundByIdServesThePackAndAnswersWithItsOwnId() throws IOException {
        MethodologyProfile profile = fixtureProfile();
        PackProbeContext context = packContext();
        Map<String, MethodologyProbe> catalog = new LinkedHashMap<>();
        for (String id : profile.requiredProbeIds()) {
            ProbeResult answer = id.equals(profile.requiredProbeIds().getFirst())
                    ? ProbeResult.complete(id, 0, 0, List.of())
                    : ProbeResult.none(id, NONE_BECAUSE);
            catalog.put(id, new FakeProbe(id, Set.of(context.packId()), answer));
        }

        List<ProbeResult> results = new ArrayList<>();
        for (String id : profile.requiredProbeIds()) {
            MethodologyProbe probe = catalog.get(id);
            assertThat(probe.packs()).contains(context.packId());
            results.add(probe.evaluate(context));
        }

        assertThat(results).extracting(ProbeResult::probeId).containsExactlyElementsOf(profile.requiredProbeIds());
        assertThat(results)
                .extracting(ProbeResult::observability)
                .containsExactly(Observability.COMPLETE, Observability.NONE, Observability.NONE);
        assertThat(catalog.keySet()).containsExactlyInAnyOrderElementsOf(profile.requiredProbeIds());
    }

    private static MethodologyProfile fixtureProfile() throws IOException {
        String resource = "/esusdata/indicator/reconciliation/methodology-profile-fixture.json";
        try (InputStream stream = MethodologyProbeContractTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("missing classpath resource %s", resource).isNotNull();
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return MethodologyProfileRegistry.fromJson(json).profile("fixture-pack", "fixture-pack@1.0.0");
        }
    }

    // ---- the contexts that do not hold together

    static Stream<BadContext> inconsistentPackContexts() {
        IndicatorRule c1 = c1();
        IndicatorRule c2 = new C2Pack();
        GatePack c1Pack = pack(c1);
        List<PackInput> inputs = inputs(c1, MUNICIPALITY);
        List<RuleOutcome> baseline = baseline(c1);
        ValidatedReference reference = reference(c1Pack, MUNICIPALITY, SIAPS_Q1_2026);
        SiapsReferenceManifest manifest = manifest(c1Pack);
        List<PackInput> swapped = new ArrayList<>(inputs);
        Collections.swap(swapped, 0, 1);
        List<PackInput> mixed = new ArrayList<>(inputs);
        mixed.set(3, inputs(c2, MUNICIPALITY).get(3));
        return Stream.of(
                new BadContext(
                        "three months only",
                        () -> packContext(
                                inputs.subList(0, 3), baseline.subList(0, 3), reference, manifest, FINGERPRINT),
                        "a quadrimestre has 4 months, not 3"),
                new BadContext(
                        "months out of order",
                        () -> packContext(swapped, baseline, reference, manifest, FINGERPRINT),
                        "input 1 is 2026-02, not 2026-01"),
                new BadContext(
                        "a month without its baseline outcome",
                        () -> packContext(inputs, baseline.subList(0, 3), reference, manifest, FINGERPRINT),
                        "each input needs its baseline outcome"),
                new BadContext(
                        "a month of another rule",
                        () -> packContext(mixed, baseline, reference, manifest, FINGERPRINT),
                        "the four months are not all of " + c1.descriptor().ruleVersion()),
                new BadContext(
                        "inputs of another municipality than the manifest",
                        () -> packContext(inputs(c1, "3550308"), baseline, reference, manifest, FINGERPRINT),
                        "an input is of another municipality than the manifest's 3541307"),
                new BadContext(
                        "a reference validated for another pack",
                        () -> packContext(
                                inputs,
                                baseline,
                                reference(pack(c2), MUNICIPALITY, SIAPS_Q1_2026),
                                manifest,
                                FINGERPRINT),
                        "the reference was validated for C2"),
                new BadContext(
                        "a reference of another quadrimestre",
                        () -> packContext(
                                inputs, baseline, reference(c1Pack, MUNICIPALITY, "2025Q3"), manifest, FINGERPRINT),
                        "the reference is of 2025Q3, not 2026Q1"),
                new BadContext(
                        "a manifest of another quadrimestre",
                        () -> packContext(
                                inputs,
                                baseline,
                                reference,
                                manifest(c1Pack, MUNICIPALITY, "2025Q3", SourceKind.OFFICIAL_TEAM_EXPORT_CSV),
                                FINGERPRINT),
                        "the manifest is of 2025Q3, not 2026Q1"),
                new BadContext(
                        "a manifest of another source than the reference",
                        () -> packContext(
                                inputs,
                                baseline,
                                reference,
                                manifest(c1Pack, MUNICIPALITY, SIAPS_Q1_2026, SourceKind.PUBLIC_AGGREGATE),
                                FINGERPRINT),
                        "the manifest is of another source than the reference"),
                new BadContext(
                        "a reference of another municipality than the manifest",
                        () -> packContext(
                                inputs, baseline, reference(c1Pack, "3550308", SIAPS_Q1_2026), manifest, FINGERPRINT),
                        "the manifest and the reference are of different municipalities"),
                new BadContext(
                        "a manifest of another pack",
                        () -> packContext(inputs, baseline, reference, manifest(pack(c2)), FINGERPRINT),
                        "the manifest is not that of C1"),
                new BadContext(
                        "a fingerprint that is not sha256:<64 hex>",
                        () -> packContext(inputs, baseline, reference, manifest, "sha256:abc"),
                        "a local source fingerprint is sha256:<64 hex>, not sha256:abc"),
                new BadContext(
                        "no fingerprint at all",
                        () -> packContext(inputs, baseline, reference, manifest, PackVerdict.NO_LOCAL_SOURCE),
                        "a local source fingerprint is sha256:<64 hex>, not "));
    }

    static Stream<BadContext> inconsistentNotaFinalContexts() {
        ValidatedReference reference = reference(GatePack.NOTA_FINAL, MUNICIPALITY, SIAPS_Q1_2026);
        String c3 = GatePack.all().get(2).packId();
        Map<String, Map<YearMonth, List<TeamResult>>> withoutC7 = monthlyResults();
        withoutC7.remove(GatePack.all().get(6).packId());
        Map<String, Map<YearMonth, List<TeamResult>>> withoutMarch = monthlyResults();
        withoutMarch.get(c3).remove(YearMonth.of(2026, 3));
        Map<String, Map<YearMonth, List<TeamResult>>> withMay = monthlyResults();
        withMay.get(C1).put(YearMonth.of(2026, 5), List.of());
        Map<String, Map<YearMonth, List<TeamResult>>> withForeignPack = monthlyResults();
        withForeignPack.put(ComponentIII.ID, withForeignPack.get(C1));
        return Stream.of(
                new BadContext(
                        "a reference validated for C1",
                        () -> notaFinalContext(monthlyResults(), reference(pack(c1()), MUNICIPALITY, SIAPS_Q1_2026)),
                        "the reference was validated for C1"),
                new BadContext(
                        "a pack without any result",
                        () -> notaFinalContext(withoutC7, reference),
                        "the Nota Final lacks the results of [C7 2026-01, C7 2026-02, C7 2026-03, C7 2026-04]"),
                new BadContext(
                        "a month without any result",
                        () -> notaFinalContext(withoutMarch, reference),
                        "the Nota Final lacks the results of [C3 2026-03]"),
                new BadContext(
                        "a month outside the quadrimestre",
                        () -> notaFinalContext(withMay, reference),
                        C1 + " holds results outside 2026-Q1"),
                new BadContext(
                        "the results of a pack that is not C1 to C7",
                        () -> notaFinalContext(withForeignPack, reference),
                        "results of a pack that is not C1 to C7"),
                new BadContext(
                        "a fingerprint that is not sha256:<64 hex>",
                        () -> new NotaFinalProbeContext(
                                Q1_2026, monthlyResults(), reference, manifest(GatePack.NOTA_FINAL), "sha256:ABC"),
                        "a local source fingerprint is sha256:<64 hex>, not sha256:ABC"));
    }
}
