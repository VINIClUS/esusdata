package esusdata.indicator.reconciliation;

import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.CompatibilityDossier.ConventionEntry;
import esusdata.indicator.reconciliation.CompatibilityDossier.NormativeDelta;
import esusdata.indicator.reconciliation.CompatibilityDossier.ProbeEntry;
import esusdata.indicator.reconciliation.CompatibilityDossier.Role;
import esusdata.indicator.reconciliation.MethodologyProfile.Dimension;
import esusdata.indicator.reconciliation.MethodologyProfile.OfficialEdition;
import esusdata.indicator.reconciliation.MethodologyProfile.Reading;
import esusdata.indicator.reconciliation.MethodologyProfile.Source;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Decides the methodological compatibility of one reference with one rule (ADR 0034 section 5, spec
 * section 9.5). The decision table, first match wins:
 *
 * <ol>
 *   <li value="0">a probe result citing a limitation the profile does not declare, or whose probe
 *       is neither a probe of a dimension nor of a convention of the profile, is a code bug:
 *       {@link IllegalStateException}, not a verdict;
 *   <li>no official edition for the quadrimestre, a scope or hash mismatch, or no official
 *       historical universe: {@code INCONCLUSIVE};
 *   <li>a complete probe with divergence on a dimension the edition reads {@code DIFFERENT}:
 *       {@code INCOMPATIBLE}, even if another probe is partial;
 *   <li>{@code INCONCLUSIVE} when a dimension that is not {@code SAME} here has no result or a
 *       partial or unobservable one; when an {@code UNKNOWN} dimension diverges; when a revision
 *       team-month stands on a current type other than the revision's, or on none; or, for the Nota
 *       Final, when a sibling pack is not {@code EXACT} or {@code EQUIVALENT_FOR_REFERENCE};
 *   <li>every dimension {@code SAME} here and no declared convention: {@code EXACT};
 *   <li>otherwise {@code EQUIVALENT_FOR_REFERENCE}.
 * </ol>
 *
 * <p>The results of convention probes and the official field comparison are recorded in the
 * dossier and never read by the table. Declared limitations are listed in it.
 */
public final class MethodologyCompatibilityEvaluator {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FINGERPRINT = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final String STEP_1 = "Step 1: ";
    private static final String STEP_3 = "Step 3: ";

    private MethodologyCompatibilityEvaluator() {}

    /**
     * Everything the table reads.
     *
     * @param profile the methodology profile of the compiled rule
     * @param quadrimestre the SIAPS quadrimestre of the reference, {@code 2026Q1}
     * @param referenceId the reference revision
     * @param manifestSha256 the SHA-256 of the manifest of the revision
     * @param localSourceFingerprint {@code sha256:<hex>} of the local source the probes read
     * @param scopeMismatch why the manifest, the pack, the rule version or the quadrimestre do not
     *     agree with each other, when they do not; empty when the scope holds
     * @param historicalUniversePresent whether the official historical universe of teams is present
     * @param dimensionProbes the results of the probes of the dimensions, by probe id
     * @param conventionProbes the results of the probes of the conventions, by probe id; recorded only
     * @param coverage how the type of the revision's teams was read
     * @param officialFields local against official figures; recorded only
     * @param siblingVerdicts for the Nota Final, the verdict of each of the seven packs of the
     *     quadrimestre by pack id; empty for a pack
     */
    public record Evidence(
            MethodologyProfile profile,
            String quadrimestre,
            String referenceId,
            String manifestSha256,
            String localSourceFingerprint,
            Optional<String> scopeMismatch,
            boolean historicalUniversePresent,
            Map<String, ProbeResult> dimensionProbes,
            Map<String, ProbeResult> conventionProbes,
            TeamTypeCoverage coverage,
            OfficialFieldComparison officialFields,
            Map<String, ReferenceCompatibility> siblingVerdicts) {

        public Evidence {
            dimensionProbes = Map.copyOf(dimensionProbes);
            conventionProbes = Map.copyOf(conventionProbes);
            siblingVerdicts = Map.copyOf(siblingVerdicts);
        }
    }

    /** The verdict and the sentence that says why. */
    private record Decision(ReferenceCompatibility verdict, String reason) {}

    /**
     * Decides and builds the dossier.
     *
     * @throws IllegalStateException when a probe result cites a limitation the profile does not
     *     declare, or belongs to a probe the profile does not have
     */
    public static CompatibilityDossier evaluate(Evidence evidence) {
        MethodologyProfile profile = evidence.profile();
        refuseWhatTheProfileDoesNotKnow(evidence);
        Optional<OfficialEdition> edition = profile.officialEdition(evidence.quadrimestre());
        Decision decision = decide(evidence, edition);
        return new CompatibilityDossier(
                evidence.referenceId(),
                profile.pack(),
                profile.ruleVersion(),
                evidence.quadrimestre(),
                evidence.manifestSha256(),
                evidence.localSourceFingerprint(),
                sourcesOf(profile, edition),
                deltas(profile, edition),
                probeEntries(evidence),
                evidence.officialFields(),
                evidence.coverage(),
                decision.verdict(),
                decision.reason(),
                profile.declaredLimitations(),
                conventions(evidence),
                evidence.siblingVerdicts());
    }

    // Step 0

    private static void refuseWhatTheProfileDoesNotKnow(Evidence evidence) {
        MethodologyProfile profile = evidence.profile();
        requireKnown(evidence.dimensionProbes(), profile.requiredProbeIds(), "a probe of a dimension");
        requireKnown(evidence.conventionProbes(), profile.conventionProbeIds(), "a probe of a convention");
        Stream.concat(evidence.dimensionProbes().values().stream(), evidence.conventionProbes().values().stream())
                .forEach(result -> result.limitations().stream()
                        .filter(limitation -> !profile.declaresLimitation(limitation))
                        .findFirst()
                        .ifPresent(limitation -> {
                            throw new IllegalStateException(result.probeId() + " cites the limitation " + limitation
                                    + ", which " + profile.ruleVersion() + " does not declare");
                        }));
        if (!evidence.siblingVerdicts().isEmpty() && !isNotaFinal(profile)) {
            throw new IllegalStateException("only the Nota Final has sibling verdicts, not " + profile.pack());
        }
    }

    private static void requireKnown(Map<String, ProbeResult> results, List<String> known, String what) {
        results.forEach((id, result) -> {
            if (!known.contains(id) || !id.equals(result.probeId())) {
                throw new IllegalStateException(
                        id + " is not " + what + " of the profile (known: " + known + "), or is keyed by another id");
            }
        });
    }

    private static boolean isNotaFinal(MethodologyProfile profile) {
        return ComponentIII.ID.equals(profile.pack());
    }

    // Steps 1 to 5

    private static Decision decide(Evidence evidence, Optional<OfficialEdition> edition) {
        Decision structural = unidentified(evidence, edition).orElse(null);
        if (structural != null) {
            return structural;
        }
        OfficialEdition official = edition.orElseThrow();
        return Stream.<Supplier<Optional<Decision>>>of(
                        () -> incompatible(evidence, official), () -> inconclusive(evidence, official))
                .map(Supplier::get)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> compatible(evidence));
    }

    private static Optional<Decision> unidentified(Evidence evidence, Optional<OfficialEdition> edition) {
        return identityProblem(evidence, edition)
                .map(text ->
                        new Decision(ReferenceCompatibility.INCONCLUSIVE, STEP_1 + text + ", so it is INCONCLUSIVE."));
    }

    private static Optional<String> identityProblem(Evidence evidence, Optional<OfficialEdition> edition) {
        if (edition.isEmpty()) {
            return Optional.of("the profile has no official edition for " + evidence.quadrimestre());
        }
        if (evidence.scopeMismatch().isPresent()) {
            return Optional.of(
                    "the scope does not hold (" + evidence.scopeMismatch().get() + ")");
        }
        if (!SHA256.matcher(evidence.manifestSha256()).matches()
                || !FINGERPRINT.matcher(evidence.localSourceFingerprint()).matches()) {
            return Optional.of("the manifest hash or the local source fingerprint is not a SHA-256");
        }
        if (!evidence.historicalUniversePresent()) {
            return Optional.of("the official historical universe of teams is absent");
        }
        return Optional.empty();
    }

    private static Optional<Decision> incompatible(Evidence evidence, OfficialEdition edition) {
        List<String> diverging = new ArrayList<>();
        for (Dimension dimension : evidence.profile().dimensions()) {
            Optional<ProbeResult> result = resultOf(evidence, dimension);
            if (readingOf(edition, dimension) == OfficialReading.DIFFERENT
                    && result.filter(MethodologyCompatibilityEvaluator::completeAndDivergent)
                            .isPresent()) {
                diverging.add(dimension.id() + " (" + result.orElseThrow().probeId() + ")");
            }
        }
        return diverging.isEmpty()
                ? Optional.empty()
                : Optional.of(new Decision(
                        ReferenceCompatibility.INCOMPATIBLE,
                        "Step 2: the complete probe of the DIFFERENT dimension " + String.join(", ", diverging)
                                + " finds teams of the revision whose result the official reading changes, so it is"
                                + " INCOMPATIBLE."));
    }

    private static Optional<Decision> inconclusive(Evidence evidence, OfficialEdition edition) {
        return Stream.<Supplier<Optional<String>>>of(
                        () -> unobserved(evidence, edition),
                        () -> unknownDiverges(evidence, edition),
                        () -> typeOpen(evidence),
                        () -> siblingsNotCompatible(evidence))
                .map(Supplier::get)
                .flatMap(Optional::stream)
                .findFirst()
                .map(text ->
                        new Decision(ReferenceCompatibility.INCONCLUSIVE, STEP_3 + text + ", so it is INCONCLUSIVE."));
    }

    private static Optional<String> unobserved(Evidence evidence, OfficialEdition edition) {
        List<String> ids = new ArrayList<>();
        for (Dimension dimension : evidence.profile().dimensions()) {
            if (readingOf(edition, dimension) == OfficialReading.SAME) {
                continue;
            }
            Optional<ProbeResult> result = resultOf(evidence, dimension);
            if (result.isEmpty()) {
                ids.add(dimension.id() + " has no probe result");
            } else if (result.get().observability() != Observability.COMPLETE) {
                ids.add(dimension.id() + " (" + result.get().probeId() + ") is "
                        + result.get().observability());
            }
        }
        return ids.isEmpty()
                ? Optional.empty()
                : Optional.of("a dimension the edition does not read as SAME is not completely observed: "
                        + String.join(", ", ids));
    }

    private static Optional<String> unknownDiverges(Evidence evidence, OfficialEdition edition) {
        List<String> ids = new ArrayList<>();
        for (Dimension dimension : evidence.profile().dimensions()) {
            if (readingOf(edition, dimension) == OfficialReading.UNKNOWN
                    && resultOf(evidence, dimension)
                            .filter(MethodologyCompatibilityEvaluator::completeAndDivergent)
                            .isPresent()) {
                ids.add(dimension.id());
            }
        }
        return ids.isEmpty()
                ? Optional.empty()
                : Optional.of("the UNKNOWN dimension " + String.join(", ", ids)
                        + " diverges, and the sources do not say which reading the SIAPS applied");
    }

    private static Optional<String> typeOpen(Evidence evidence) {
        return evidence.coverage().leavesTypeOpen()
                ? Optional.of("the team type of the revision was read through the current type with another type,"
                        + " or not at all, in some team-month (coverage.fallback_disagreeing, coverage.unresolved)")
                : Optional.empty();
    }

    private static Optional<String> siblingsNotCompatible(Evidence evidence) {
        if (!isNotaFinal(evidence.profile())) {
            return Optional.empty();
        }
        Set<String> expected = new TreeSet<>();
        GatePack.all().forEach(pack -> expected.add(pack.packId()));
        List<String> problems = new ArrayList<>();
        for (String pack : expected) {
            ReferenceCompatibility verdict = evidence.siblingVerdicts().get(pack);
            if (verdict == null) {
                problems.add(pack + " has no verdict");
            } else if (!verdict.authorizesGate()) {
                problems.add(pack + " is " + verdict);
            }
        }
        return problems.isEmpty()
                ? Optional.empty()
                : Optional.of("the Nota Final rests on its sibling packs, and " + String.join(", ", problems));
    }

    private static Decision compatible(Evidence evidence) {
        MethodologyProfile profile = evidence.profile();
        if (profile.declaredConventions().isEmpty()
                && deltas(profile, profile.officialEdition(evidence.quadrimestre()))
                        .isEmpty()) {
            return new Decision(
                    ReferenceCompatibility.EXACT,
                    "Step 4: every dimension of " + profile.ruleVersion() + " reads SAME in " + evidence.quadrimestre()
                            + " and it declares no convention, so it is EXACT.");
        }
        List<String> conventionIds = profile.declaredConventions().stream()
                .map(MethodologyProfile.DeclaredConvention::id)
                .toList();
        String why = conventionIds.isEmpty()
                ? "every dimension that is not SAME has a complete probe without divergence"
                : "the declared conventions " + String.join(", ", conventionIds) + " rule out EXACT, and every"
                        + " dimension that is not SAME has a complete probe without divergence";
        return new Decision(
                ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE,
                "Step 5: " + why + ", so it is EQUIVALENT_FOR_REFERENCE.");
    }

    // Reading the evidence

    private static Optional<ProbeResult> resultOf(Evidence evidence, Dimension dimension) {
        return dimension.probeId().map(evidence.dimensionProbes()::get);
    }

    private static OfficialReading readingOf(OfficialEdition edition, Dimension dimension) {
        return edition.readings().get(dimension.id()).reading();
    }

    private static boolean completeAndDivergent(ProbeResult result) {
        return result.observability() == Observability.COMPLETE
                && result.divergent().orElse(0) > 0;
    }

    // The dossier's parts

    private static List<Source> sourcesOf(MethodologyProfile profile, Optional<OfficialEdition> edition) {
        if (edition.isPresent()) {
            return edition.get().sources();
        }
        Map<String, Source> all = new LinkedHashMap<>();
        profile.officialReadings()
                .values()
                .forEach(each -> each.sources().forEach(source -> all.put(source.id(), source)));
        return List.copyOf(all.values());
    }

    private static List<NormativeDelta> deltas(MethodologyProfile profile, Optional<OfficialEdition> edition) {
        if (edition.isEmpty()) {
            return List.of();
        }
        List<NormativeDelta> deltas = new ArrayList<>();
        for (Dimension dimension : profile.dimensions()) {
            Reading read = edition.get().readings().get(dimension.id());
            if (read.reading() != OfficialReading.SAME) {
                deltas.add(new NormativeDelta(
                        dimension.id(),
                        dimension.localReading(),
                        read.reading(),
                        Optional.ofNullable(read.officialReadingText()),
                        read.source().id(),
                        dimension.probeId(),
                        dimension.decisionRefs()));
            }
        }
        return deltas;
    }

    private static List<ProbeEntry> probeEntries(Evidence evidence) {
        List<ProbeEntry> entries = new ArrayList<>();
        for (Dimension dimension : evidence.profile().dimensions()) {
            resultOf(evidence, dimension)
                    .ifPresent(result -> entries.add(new ProbeEntry(Role.DIMENSION, dimension.id(), result)));
        }
        for (MethodologyProfile.DeclaredConvention convention :
                evidence.profile().declaredConventions()) {
            conventionResult(evidence, convention)
                    .ifPresent(result -> entries.add(new ProbeEntry(Role.CONVENTION, convention.id(), result)));
        }
        return entries;
    }

    private static List<ConventionEntry> conventions(Evidence evidence) {
        return evidence.profile().declaredConventions().stream()
                .map(convention -> new ConventionEntry(convention, conventionResult(evidence, convention)))
                .toList();
    }

    private static Optional<ProbeResult> conventionResult(
            Evidence evidence, MethodologyProfile.DeclaredConvention convention) {
        return convention.probeId().map(evidence.conventionProbes()::get);
    }
}
