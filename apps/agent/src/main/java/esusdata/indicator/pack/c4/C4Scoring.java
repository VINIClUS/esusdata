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
     * C4-LIM-18 (P07, C4-D1): practice D is credited in full to the people of eAP 76 teams, observed
     * or not (item 24 b).
     */
    static final String EAP_CREDIT =
            "C4-LIM-18/contagem: D creditada integralmente (%d pontos) para %d pessoa(s) de equipes"
                    + " eAP 76, conforme o item 24 b; observada em %d.";

    /** C4-LIM-19 (C4-D2): the people left out because their team is not a considered one. */
    static final String TEAM_EXCLUSIONS =
            "C4-LIM-19/contagem: %d pessoa(s) vinculada(s) a equipe fora da regra de tipo (70 ou"
                    + " 76 vigente no fim da competência) ficaram fora: %d de equipe sem tipo, %d de tipo conflitante e %d de"
                    + " outro tipo.";

    /** C4-LIM-20 (AMB-C4-04): a status outside 0/1/2 is diagnosed, not converted silently. */
    static final String UNKNOWN_STATUS = "C4-LIM-20: %d pessoa(s) com situação de condição nula ou fora de 0/1/2"
            + " foram mantidas como não resolvidas.";

    /** One eligible person with the decision of every practice. */
    record Scored(Subject subject, SortedMap<String, Outcome> outcomes) {

        boolean eap76() {
            return subject.eap76();
        }

        /** Whether practice {@code code} counts for this person: observed, or D credited to an eAP 76. */
        boolean counts(String code) {
            return outcomes.get(code).met() || creditedD(code);
        }

        /** D of an eAP 76 person who had no two valid visits: credited in full (C4-D1). */
        boolean creditedD(String code) {
            return eap76() && C4Practices.D.equals(code) && !outcomes.get(code).met();
        }

        /** The weights of the practices that count (Quadro 01), D credited for an eAP 76. */
        BigInteger points(List<ComponentSpec> specs) {
            return Scores.points(specs.stream().filter(s -> counts(s.code())).toList());
        }
    }

    private C4Scoring() {}

    static IndicatorResult result(PackDescriptor descriptor, EvaluationContext context, List<Scored> people) {
        List<ComponentSpec> specs = descriptor.components();
        BigInteger subjects = BigInteger.valueOf(people.size());
        BigInteger total = BigInteger.ZERO;
        for (Scored p : people) {
            total = total.add(p.points(specs));
        }
        Optional<ExactRatio> mean = Scores.meanPoints(total, subjects);
        IndicatorStatus status = mean.isEmpty() ? IndicatorStatus.NO_DENOMINATOR : IndicatorStatus.COMPUTED;
        ExactRatio value = mean.orElse(null);
        Optional<ExactRatio> shown = mean;
        BigInteger numerator = total;
        return new IndicatorResult(
                status,
                shown.map(v -> v.toScaledBigDecimal(4).toPlainString()).orElse(null),
                numerator,
                subjects,
                descriptor.denominatorKind(),
                shown.flatMap(Bands.QUALIDADE_C2_C7::classify).orElse(null),
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations(descriptor, people),
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                value,
                components(specs, people, subjects),
                true);
    }

    private static List<ResultComponent> components(
            List<ComponentSpec> specs, List<Scored> people, BigInteger subjects) {
        List<ResultComponent> components = new ArrayList<>(specs.size());
        for (ComponentSpec spec : specs) {
            long met = people.stream().filter(p -> p.counts(spec.code())).count();
            components.add(ResultComponent.of(spec, BigInteger.valueOf(met), subjects));
        }
        return components;
    }

    private static List<String> limitations(PackDescriptor descriptor, List<Scored> people) {
        List<String> limitations = new ArrayList<>();
        long eap = people.stream().filter(Scored::eap76).count();
        if (eap > 0) {
            long observed = people.stream()
                    .filter(p -> p.eap76() && p.outcomes().get(C4Practices.D).met())
                    .count();
            limitations.add(EAP_CREDIT.formatted(weightOfD(descriptor), eap, observed));
        }
        long unknownStatus = people.stream()
                .filter(p -> p.subject().unknownConditionStatus())
                .count();
        if (unknownStatus > 0) {
            limitations.add(UNKNOWN_STATUS.formatted(unknownStatus));
        }
        limitations.addAll(descriptor.standingLimitationLines());
        return limitations;
    }

    private static BigInteger weightOfD(PackDescriptor descriptor) {
        return descriptor.components().stream()
                .filter(c -> C4Practices.D.equals(c.code()))
                .findFirst()
                .orElseThrow()
                .weight();
    }

    /**
     * The people of the cohort left out because their team is not considered, by reason (C4-D2): a
     * disclosure for the municipal result, once.
     */
    static String teamExclusions(List<Subject> subjects) {
        long without = count(subjects, C4Reasons.TEAM_WITHOUT_TYPE);
        long conflict = count(subjects, C4Reasons.TEAM_TYPE_CONFLICT);
        long other = count(subjects, C4Reasons.TEAM_TYPE_OUT_OF_SCOPE);
        return without + conflict + other == 0
                ? null
                : TEAM_EXCLUSIONS.formatted(without + conflict + other, without, conflict, other);
    }

    private static long count(List<Subject> subjects, String reason) {
        return subjects.stream().filter(s -> reason.equals(s.exclusion())).count();
    }

    /** The person row of an excluded candidate. */
    static EvidenceItem excluded(Subject subject) {
        return row(subject, null, null, EvidenceDecision.EXCLUDED, subject.exclusion(), null);
    }

    /** The person row, one row per practice and the events behind each met practice. */
    static List<EvidenceItem> eligible(Scored scored, List<ComponentSpec> specs) {
        Subject subject = scored.subject();
        List<EvidenceItem> rows = new ArrayList<>();
        BigInteger points = scored.points(specs);
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
        boolean credited = scored.creditedD(spec.code());
        boolean met = outcome.met() || credited;
        EvidenceDecision decision = met ? EvidenceDecision.PRACTICE_MET : EvidenceDecision.PRACTICE_NOT_MET;
        String reason = credited
                ? C4Reasons.PRACTICE_CREDITED_EAP
                : outcome.met() ? C4Reasons.PRACTICE_MET : C4Reasons.PRACTICE_NOT_MET;
        BigInteger points = met ? spec.weight() : BigInteger.ZERO;
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
                support == null ? null : support.model());
    }
}
