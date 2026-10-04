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

/** The children of one unit (the municipality or one team) added up into a result. */
final class C2Tally {

    private final PackDescriptor descriptor;
    private final int practices;
    private final long[] met;
    private final long[] ambiguous;
    private final SortedSet<String> ambiguities = new TreeSet<>();
    private BigInteger points = BigInteger.ZERO;
    private long subjects;
    private long ambiguousSubjects;
    private long teamTypeUnknown;
    private boolean completesTwo;
    private final SortedSet<String> cnes = new TreeSet<>();

    C2Tally(PackDescriptor descriptor) {
        this.descriptor = descriptor;
        this.practices = descriptor.components().size();
        this.met = new long[practices];
        this.ambiguous = new long[practices];
    }

    void add(ScoredChild child) {
        subjects++;
        points = points.add(child.certainPoints(descriptor.components()));
        if (child.ambiguous() || !child.member().cohortAmbiguities().isEmpty()) {
            ambiguousSubjects++;
        }
        ambiguities.addAll(child.member().cohortAmbiguities());
        if (child.teamTypeUnknown()) {
            teamTypeUnknown++;
        }
        completesTwo |= child.member().completesTwoInMonth();
        cnes.add(Objects.requireNonNullElse(child.member().cnes(), "")); // unknown never agrees
        for (int i = 0; i < practices; i++) {
            PracticeOutcome outcome = child.outcomes().get(i);
            if (outcome.scores()) {
                met[i]++;
            } else if (outcome.status() == PracticeOutcome.Status.AMBIGUOUS) {
                ambiguous[i]++;
                ambiguities.addAll(outcome.ambiguities());
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
        IndicatorStatus status;
        if (mean.isEmpty()) {
            status = IndicatorStatus.NO_DENOMINATOR;
        } else if (ambiguousSubjects > 0) {
            status = IndicatorStatus.RULE_AMBIGUITY;
        } else {
            status = IndicatorStatus.COMPUTED;
        }
        ExactRatio value = status == IndicatorStatus.COMPUTED ? mean.get() : null;
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
                completesTwo);
    }

    private List<ResultComponent> components(BigInteger denominator) {
        List<ResultComponent> components = new ArrayList<>(practices);
        for (int i = 0; i < practices; i++) {
            ComponentSpec spec = descriptor.components().get(i);
            BigInteger numerator = BigInteger.valueOf(met[i]);
            if (ambiguous[i] > 0) {
                components.add(new ResultComponent(
                        spec.code(),
                        spec.kind(),
                        spec.weight(),
                        numerator,
                        denominator,
                        null,
                        IndicatorStatus.RULE_AMBIGUITY));
            } else {
                components.add(ResultComponent.of(spec, numerator, denominator));
            }
        }
        return components;
    }

    private List<String> limitations() {
        List<String> limitations = new ArrayList<>(descriptor.standingLimitations());
        if (ambiguousSubjects > 0) {
            limitations.add(ambiguousSubjects
                    + " criança(s) com prática ou inclusão indeterminada (ambiguidade da ficha ou dado indisponível: "
                    + String.join(", ", ambiguities)
                    + "): valor indisponível (RULE_AMBIGUITY); o numerador soma só as práticas certas"
                    + " (limite inferior).");
        }
        if (teamTypeUnknown > 0) {
            limitations.add(teamTypeUnknown + " criança(s) de equipe sem tipo comprovado na fonte (lacuna L1):"
                    + " a pontuação integral da prática D para eAP tipo 76 não foi aplicada.");
        }
        return limitations;
    }
}
