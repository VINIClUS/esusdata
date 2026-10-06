package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.sensitivity.EvidenceSubjects;
import esusdata.indicator.sensitivity.PackSensitivity;
import esusdata.indicator.sensitivity.ReadingRow;
import esusdata.indicator.sensitivity.SubjectReadings;
import esusdata.indicator.sensitivity.SubjectScore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sensitivity of C2 to the cohort ambiguities (AMB-C2-02, AMB-C2-03) and to the practice-level
 * ones. The cohort ambiguities are not in the evidence, so they are read from {@link
 * C2Cohort#classify} — the very code production runs — for each eligible child. The baseline is
 * checked against the production outcome: denominator, certain points and ambiguous children.
 * Practice-level ambiguities get bounds, not per-reading numbers: {@link Readings#decide} fixes no
 * reading for a child and the candidates flip children in different directions.
 */
public final class C2Sensitivity implements PackSensitivity {

    private static final Pattern AMBIGUOUS_CHILDREN = Pattern.compile("^(\\d+) criança\\(s\\) com prática");

    @Override
    public PackReport run(CanonicalDataset data, EvaluationContext context) {
        C2Pack pack = new C2Pack();
        RuleOutcome outcome = pack.evaluate(data, context);
        String id = C2Pack.ID;
        if (PackSensitivity.unsupported(outcome)) {
            return new PackReport(
                    id, PackSensitivity.status(outcome), List.of(), List.of("extrato sem a capacidade pedida"));
        }
        List<SubjectScore> subjects = withCohortCodes(
                EvidenceSubjects.of(outcome.evidence(), PackSensitivity.weights(pack.descriptor())), data, context);
        requireBaselineEqualsProduction(outcome, subjects);
        List<ReadingRow> rows = new ArrayList<>(SubjectReadings.baseline(id, subjects));
        rows.addAll(SubjectReadings.frequencies(id, subjects));
        rows.addAll(SubjectReadings.cohortReadings(id, subjects));
        rows.addAll(SubjectReadings.practiceBounds(id, subjects));
        rows.addAll(PackSensitivity.limitationRows(id, pack.descriptor(), outcome));
        return new PackReport(id, PackSensitivity.status(outcome), rows, List.of());
    }

    /** Adds to each eligible child the cohort ambiguities {@link C2Cohort} reports for it. */
    static List<SubjectScore> withCohortCodes(
            List<SubjectScore> subjects, CanonicalDataset data, EvaluationContext context) {
        Map<String, CanonicalPerson> persons = new HashMap<>();
        data.persons().forEach(p -> persons.putIfAbsent(p.personKey(), p));
        Map<String, List<CanonicalRegistration>> registrations = new HashMap<>();
        data.registrations()
                .forEach(r -> registrations
                        .computeIfAbsent(r.personKey(), k -> new ArrayList<>())
                        .add(r));
        List<SubjectScore> enriched = new ArrayList<>(subjects.size());
        for (SubjectScore subject : subjects) {
            C2Cohort.Member member = C2Cohort.classify(
                    persons.get(subject.key()), registrations.getOrDefault(subject.key(), List.of()), context);
            enriched.add(subject.withCohortCodes(member.cohortAmbiguities()));
        }
        return enriched;
    }

    /** The harness must reproduce production before it is trusted on any reading. */
    static void requireBaselineEqualsProduction(RuleOutcome outcome, List<SubjectScore> subjects) {
        SubjectReadings.Tally tally = SubjectReadings.Tally.of(subjects);
        var result = outcome.result();
        if (result.denominator() != null && result.denominator().longValueExact() != tally.denominator()) {
            throw new IllegalStateException("C2 baseline: denominator differs from production");
        }
        if (result.numerator() != null && !result.numerator().equals(tally.points())) {
            throw new IllegalStateException("C2 baseline: points differ from production");
        }
        long productionAmbiguous = result.limitations().stream()
                .map(AMBIGUOUS_CHILDREN::matcher)
                .filter(Matcher::find)
                .mapToLong(m -> Long.parseLong(m.group(1)))
                .findFirst()
                .orElse(0);
        if (productionAmbiguous != tally.ambiguous()) {
            throw new IllegalStateException("C2 baseline: ambiguous children differ from production");
        }
    }
}
