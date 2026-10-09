package esusdata.indicator.reconciliation;

import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.MethodologyCompatibilityEvaluator.Evidence;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredConvention;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredLimitation;
import esusdata.indicator.reconciliation.MethodologyProfile.Dimension;
import esusdata.indicator.reconciliation.MethodologyProfile.OfficialEdition;
import esusdata.indicator.reconciliation.MethodologyProfile.Reading;
import esusdata.indicator.reconciliation.MethodologyProfile.Source;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An invented profile of three dimensions (one {@code SAME} everywhere and without a probe, one
 * with a probe whose reading the first quadrimestre sets, one more of the same kind), one declared
 * limitation and, on request, one declared convention with a probe; and the evidence of a clean
 * reference over it. For the tests of the evaluator and of the writer.
 */
final class CompatibilityFixtures {

    static final String Q1 = "2026Q1";
    static final String REFERENCE_ID = "sp-3541307-2026q1-c1-team-r1";
    static final String PACK = "fx-pack";
    static final String RULE_VERSION = "fx-pack@1.0.0";
    static final String SAME_DIMENSION = "fx.same.reading";
    static final String FIRST_DIMENSION = "fx.first.reading";
    static final String SECOND_DIMENSION = "fx.second.reading";
    static final String FIRST_PROBE = "fx.first.probe";
    static final String SECOND_PROBE = "fx.second.probe";
    static final String CONVENTION = "fx.conv.reading";
    static final String CONVENTION_PROBE = "fx.conv.probe";
    static final String LIMITATION = "oor.l1.cadsus";
    static final String MANIFEST_SHA = "a".repeat(64);
    static final String FINGERPRINT = "sha256:" + "b".repeat(64);
    static final String REF = "AMB-FX-01";

    private static final Source SOURCE = new Source(
            "ficha-fx-2026",
            "Ficha FX",
            "https://example.org/ficha-fx",
            LocalDate.of(2026, 1, 2),
            MethodologyProfile.Source.DocumentKind.FICHA);

    private CompatibilityFixtures() {}

    /** The profile of {@link #PACK}: the first quadrimestre reads the two probed dimensions as given. */
    static MethodologyProfile profile(OfficialReading first, OfficialReading second, boolean convention) {
        return profile(PACK, RULE_VERSION, first, second, convention);
    }

    static MethodologyProfile profile(
            String pack, String ruleVersion, OfficialReading first, OfficialReading second, boolean convention) {
        Map<String, Reading> readings = new LinkedHashMap<>();
        readings.put(SAME_DIMENSION, reading(OfficialReading.SAME));
        readings.put(FIRST_DIMENSION, reading(first));
        readings.put(SECOND_DIMENSION, reading(second));
        List<DeclaredConvention> conventions = convention
                ? List.of(new DeclaredConvention(
                        CONVENTION, "the local reading", List.of(REF), "k3.fx", Optional.of(CONVENTION_PROBE)))
                : List.of();
        return new MethodologyProfile(
                pack,
                ruleVersion,
                List.of(
                        new Dimension(SAME_DIMENSION, "same", List.of(REF), Optional.empty()),
                        new Dimension(FIRST_DIMENSION, "first", List.of(REF), FIRST_PROBE),
                        new Dimension(SECOND_DIMENSION, "second", List.of(REF), SECOND_PROBE)),
                List.of(),
                List.of(new DeclaredLimitation(LIMITATION, "CadSUS is not in the installation", List.of(REF))),
                conventions,
                Map.of(Q1, new OfficialEdition("fichas-2026-v1", List.of(SOURCE), readings)));
    }

    private static Reading reading(OfficialReading reading) {
        return new Reading(reading, reading == OfficialReading.DIFFERENT ? "the official text" : null, SOURCE);
    }

    static ProbeResult clean(String probeId) {
        return ProbeResult.complete(probeId, 0, 0, List.of());
    }

    static Builder evidence(MethodologyProfile profile) {
        return new Builder(profile);
    }

    /** The evidence of a clean reference: every probe complete without divergence, nothing open. */
    static final class Builder {
        private final MethodologyProfile profile;
        private final Map<String, ProbeResult> dimensionProbes = new HashMap<>();
        private final Map<String, ProbeResult> conventionProbes = new HashMap<>();
        private final Map<String, ReferenceCompatibility> siblings = new HashMap<>();
        private String quadrimestre = Q1;
        private Optional<String> scopeMismatch = Optional.empty();
        private boolean universe = true;
        private String manifestSha = MANIFEST_SHA;
        private TeamTypeCoverage coverage = new TeamTypeCoverage(40, 40, 0, 0, 0, 0);
        private OfficialFieldComparison fields = OfficialFieldComparison.of(List.of());

        private Builder(MethodologyProfile profile) {
            this.profile = profile;
            profile.requiredProbeIds().forEach(id -> dimensionProbes.put(id, clean(id)));
            profile.conventionProbeIds().forEach(id -> conventionProbes.put(id, clean(id)));
        }

        Builder dimension(ProbeResult result) {
            dimensionProbes.put(result.probeId(), result);
            return this;
        }

        Builder withoutDimensionProbe(String probeId) {
            dimensionProbes.remove(probeId);
            return this;
        }

        Builder convention(ProbeResult result) {
            conventionProbes.put(result.probeId(), result);
            return this;
        }

        Builder quadrimestre(String value) {
            quadrimestre = value;
            return this;
        }

        Builder scopeMismatch(String value) {
            scopeMismatch = Optional.of(value);
            return this;
        }

        Builder universe(boolean present) {
            universe = present;
            return this;
        }

        Builder manifestSha(String value) {
            manifestSha = value;
            return this;
        }

        Builder coverage(TeamTypeCoverage value) {
            coverage = value;
            return this;
        }

        Builder fields(OfficialFieldComparison value) {
            fields = value;
            return this;
        }

        Builder sibling(String pack, ReferenceCompatibility verdict) {
            siblings.put(pack, verdict);
            return this;
        }

        /** Every sibling pack of the Nota Final with {@code verdict}. */
        Builder allSiblings(ReferenceCompatibility verdict) {
            GatePack.all().forEach(pack -> siblings.put(pack.packId(), verdict));
            return this;
        }

        Evidence build() {
            return new Evidence(
                    profile,
                    quadrimestre,
                    REFERENCE_ID,
                    manifestSha,
                    FINGERPRINT,
                    scopeMismatch,
                    universe,
                    dimensionProbes,
                    conventionProbes,
                    coverage,
                    fields,
                    siblings);
        }

        CompatibilityDossier dossier() {
            return MethodologyCompatibilityEvaluator.evaluate(build());
        }
    }

    /** The profile of the Nota Final: one dimension that reads SAME, no probe. */
    static MethodologyProfile notaFinalProfile() {
        return new MethodologyProfile(
                ComponentIII.ID,
                ComponentIII.ID + "@1.0.0",
                List.of(new Dimension(SAME_DIMENSION, "same", List.of(REF), Optional.empty())),
                List.of(),
                List.of(),
                List.of(),
                Map.of(
                        Q1,
                        new OfficialEdition(
                                "fichas-2026-v1",
                                List.of(SOURCE),
                                Map.of(SAME_DIMENSION, reading(OfficialReading.SAME)))));
    }
}
