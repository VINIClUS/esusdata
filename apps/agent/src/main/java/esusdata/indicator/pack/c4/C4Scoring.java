package esusdata.indicator.pack.c4;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.Scores;
import esusdata.indicator.pack.c4.C4Cohort.Link;
import esusdata.indicator.pack.c4.C4Cohort.Subject;
import esusdata.indicator.pack.c4.C4Practices.Outcome;
import esusdata.indicator.pack.c4.C4Practices.Support;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Turns per-person practice decisions into the exact C4 score (§2.4: the mean of the points of
 * Quadro 01, never ×100), its components and the minimal evidence (ENG-36).
 */
final class C4Scoring {

    /**
     * AMB-C4-01 as the pack applies it (MET-23, P07): the ficha says practice D «não será
     * condicionante de pontuação para eAP, tipo 76» without saying what happens to its 20 points.
     */
    static final String EAP_AMBIGUITY = "AMB-C4-01: equipe eAP tipo 76 — a prática D «não será condicionante de"
            + " pontuação» e a ficha não diz o que acontece com os 20 pontos; sem pesos redistribuídos, as práticas"
            + " ficam exibidas separadamente e o escore fica indisponível até a reconciliação com o Siaps (MET-23,"
            + " P07).";

    /** One eligible person with the decision of every practice. */
    record Scored(Subject subject, SortedMap<String, Outcome> outcomes) {

        boolean eap76() {
            return subject.eap76();
        }

        /** The weights of the practices observed as met (Quadro 01), D included. */
        BigInteger observedPoints(List<ComponentSpec> specs) {
            return Scores.points(
                    specs.stream().filter(s -> outcomes.get(s.code()).met()).toList());
        }
    }

    private C4Scoring() {}

    static IndicatorResult result(PackDescriptor descriptor, EvaluationContext context, List<Scored> people) {
        List<ComponentSpec> specs = descriptor.components();
        BigInteger subjects = BigInteger.valueOf(people.size());
        BigInteger total = BigInteger.ZERO;
        for (Scored p : people) {
            total = total.add(p.observedPoints(specs));
        }
        List<ResultComponent> components = new ArrayList<>(specs.size());
        for (ComponentSpec spec : specs) {
            long met = people.stream()
                    .filter(p -> p.outcomes().get(spec.code()).met())
                    .count();
            components.add(ResultComponent.of(spec, BigInteger.valueOf(met), subjects));
        }
        List<String> limitations = new ArrayList<>(descriptor.standingLimitations());
        Optional<ExactRatio> mean = Scores.meanPoints(total, subjects);
        IndicatorStatus status = IndicatorStatus.COMPUTED;
        if (mean.isEmpty()) {
            status = IndicatorStatus.NO_DENOMINATOR;
        } else if (people.stream().anyMatch(Scored::eap76)) {
            status = IndicatorStatus.RULE_AMBIGUITY;
            limitations.add(0, EAP_AMBIGUITY);
        }
        ExactRatio value = status == IndicatorStatus.COMPUTED ? mean.orElseThrow() : null;
        // AMB-C4-01: «não compor escore» — no sum of points while the eAP 76 reading is open
        BigInteger numerator = status == IndicatorStatus.RULE_AMBIGUITY ? null : total;
        return new IndicatorResult(
                status,
                value == null ? null : value.toScaledBigDecimal(4).toPlainString(),
                numerator,
                subjects,
                descriptor.denominatorKind(),
                value == null ? null : Bands.QUALIDADE_C2_C7.classify(value).orElse(null),
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                value,
                components,
                true);
    }

    /** The person row of an excluded candidate. */
    static EvidenceItem excluded(Subject subject) {
        return row(subject, null, null, EvidenceDecision.EXCLUDED, subject.exclusion(), null);
    }

    /** The person row, one row per practice and the events behind each met practice. */
    static List<EvidenceItem> eligible(Scored scored, List<ComponentSpec> specs) {
        Subject subject = scored.subject();
        List<EvidenceItem> rows = new ArrayList<>();
        BigInteger points = scored.eap76() ? null : scored.observedPoints(specs);
        rows.add(row(subject, null, null, EvidenceDecision.ELIGIBLE, C4Reasons.ELIGIBLE, points));
        List<EvidenceItem> supports = new ArrayList<>();
        for (ComponentSpec spec : specs) {
            Outcome outcome = scored.outcomes().get(spec.code());
            rows.add(practice(scored, spec, outcome));
            for (Support s : outcome.supports()) {
                supports.add(row(subject, spec.code(), s, EvidenceDecision.SUPPORTING_EVENT, null, null));
            }
        }
        rows.addAll(supports);
        return rows;
    }

    private static EvidenceItem practice(Scored scored, ComponentSpec spec, Outcome outcome) {
        EvidenceDecision decision = outcome.met() ? EvidenceDecision.PRACTICE_MET : EvidenceDecision.PRACTICE_NOT_MET;
        if (scored.eap76() && C4Practices.D.equals(spec.code())) {
            return row(scored.subject(), spec.code(), null, decision, C4Reasons.PRACTICE_INFORMATIVE_EAP, null);
        }
        String reason = outcome.met() ? C4Reasons.PRACTICE_MET : C4Reasons.PRACTICE_NOT_MET;
        BigInteger points = outcome.met() ? spec.weight() : BigInteger.ZERO;
        return row(scored.subject(), spec.code(), null, decision, reason, points);
    }

    /** One evidence row about a person: the opaque key and the link, never a name, CPF or CNS. */
    private static EvidenceItem row(
            Subject subject,
            String component,
            Support support,
            EvidenceDecision decision,
            String reason,
            BigInteger points) {
        Link link = subject.link();
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                subject.personKey(),
                support == null ? null : support.sourceRef(),
                support == null ? null : support.date().toString(),
                component,
                decision,
                reason,
                points,
                link == null ? null : link.cnes(),
                link == null ? null : link.ine(),
                support == null ? null : support.cbo(),
                null);
    }
}
