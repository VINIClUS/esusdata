package esusdata.indicator.pack.c2;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.Scores;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The children of one unit (the municipality or one team) added up into a result. Every child of
 * the cohort completes two years in the competência (AMB-C2-03), so a unit with a child has a
 * monthly cohort event and a unit without one has no denominator.
 */
final class C2Tally {

    private static final int D_INDEX = 3;

    private final PackDescriptor descriptor;
    private final int practices;
    private final long[] met;
    private BigInteger points = BigInteger.ZERO;
    private long subjects;
    private long eapChildren;
    private long eapObserved;
    private final SortedSet<String> cnes = new TreeSet<>();

    C2Tally(PackDescriptor descriptor) {
        this.descriptor = descriptor;
        this.practices = descriptor.components().size();
        this.met = new long[practices];
    }

    void add(ScoredChild child) {
        subjects++;
        points = points.add(child.points(descriptor.components()));
        if (child.eap76()) {
            eapChildren++;
            if (child.observedD()) {
                eapObserved++;
            }
        }
        cnes.add(Objects.requireNonNullElse(child.member().cnes(), "")); // unknown never agrees
        for (int i = 0; i < practices; i++) {
            if (child.outcomes().get(i).scores()) {
                met[i]++;
            }
        }
    }

    /** The CNES of the unit's children when they agree on one, else {@code null} — never the first by order. */
    String cnes() {
        return cnes.size() == 1 && !cnes.contains("") ? cnes.first() : null;
    }

    /** The unit's result before the release gates: exact, with every practice counted apart. */
    IndicatorResult result(EvaluationContext context) {
        BigInteger denominator = BigInteger.valueOf(subjects);
        Optional<ExactRatio> mean = Scores.meanPoints(points, denominator);
        ExactRatio value = mean.orElse(null);
        IndicatorStatus status = mean.isEmpty() ? IndicatorStatus.NO_DENOMINATOR : IndicatorStatus.COMPUTED;
        Classification classification =
                value == null ? null : C2Pack.BANDS.classify(value).orElse(null);
        return new IndicatorResult(
                status,
                value == null ? null : value.toScaledBigDecimal(4).toPlainString(),
                points,
                denominator,
                descriptor.denominatorKind(),
                classification,
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations(),
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                value,
                components(denominator),
                subjects > 0);
    }

    private List<ResultComponent> components(BigInteger denominator) {
        List<ResultComponent> components = new ArrayList<>(practices);
        for (int i = 0; i < practices; i++) {
            ComponentSpec spec = descriptor.components().get(i);
            components.add(ResultComponent.of(spec, BigInteger.valueOf(met[i]), denominator));
        }
        return components;
    }

    private List<String> limitations() {
        List<String> limitations = new ArrayList<>(descriptor.standingLimitationLines());
        if (eapChildren > 0) {
            limitations.add(C2Pack.EAP_CREDIT.formatted(
                    descriptor.components().get(D_INDEX).weight(), eapChildren, eapObserved));
        }
        return limitations;
    }
}
