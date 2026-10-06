package esusdata.indicator.sensitivity;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rebuilds {@link SubjectScore}s from the minimal evidence a rule emits (§1.8), so the harness
 * reads exactly what production decided. Event rows are ignored; a subject is kept when it is in
 * the denominator or depends on an ambiguity (a cohort-ambiguous C3 episode is not eligible but
 * still makes the result ambiguous).
 */
public final class EvidenceSubjects {

    private static final Pattern CODE = Pattern.compile("(AMB[-_]C\\d+[-_]\\d+|LACUNA[-_]L\\d+)");

    private EvidenceSubjects() {}

    /** The ambiguity codes named in a reason code, as {@code AMB-C2-03}; empty when it names none. */
    public static SortedSet<String> codesIn(String text) {
        SortedSet<String> codes = new TreeSet<>();
        if (text != null) {
            Matcher matcher = CODE.matcher(text);
            while (matcher.find()) {
                codes.add(matcher.group().replace('_', '-'));
            }
        }
        return codes;
    }

    /** The subjects of one outcome's evidence; {@code weights} maps a practice code to its points. */
    public static List<SubjectScore> of(List<EvidenceItem> evidence, Map<String, BigInteger> weights) {
        Map<String, Builder> byKey = new LinkedHashMap<>();
        for (EvidenceItem row : evidence) {
            if (row.subjectKind() != EvidenceSubjectKind.EVENT) {
                byKey.computeIfAbsent(row.subjectKey(), Builder::new).accept(row, weights);
            }
        }
        List<SubjectScore> subjects = new ArrayList<>();
        for (Builder builder : byKey.values()) {
            SubjectScore subject = builder.build();
            if (subject.eligible() || subject.ambiguous()) {
                subjects.add(subject);
            }
        }
        return subjects;
    }

    private static final class Builder {
        private final String key;
        private String ine;
        private boolean eligible;
        private BigInteger points = BigInteger.ZERO;
        private final SortedSet<String> cohortCodes = new TreeSet<>();
        private final SortedMap<String, SubjectScore.OpenPractice> open = new TreeMap<>();

        Builder(String key) {
            this.key = key;
        }

        void accept(EvidenceItem row, Map<String, BigInteger> weights) {
            EvidenceDecision decision = row.decision();
            if (decision == EvidenceDecision.ELIGIBLE || decision == EvidenceDecision.EXCLUDED) {
                if (row.component() == null) {
                    ine = row.ine();
                    eligible = decision == EvidenceDecision.ELIGIBLE;
                    cohortCodes.addAll(codesIn(row.reasonCode()));
                }
            } else if (decision == EvidenceDecision.PRACTICE_MET || decision == EvidenceDecision.PRACTICE_EXEMPT) {
                points = points.add(
                        row.points() == null ? weights.getOrDefault(row.component(), BigInteger.ZERO) : row.points());
            } else if (decision == EvidenceDecision.PRACTICE_AMBIGUOUS) {
                SortedSet<String> codes = codesIn(row.reasonCode());
                if (codes.isEmpty()) {
                    codes.add(row.reasonCode());
                }
                open.put(
                        row.component(),
                        new SubjectScore.OpenPractice(weights.getOrDefault(row.component(), BigInteger.ZERO), codes));
            }
        }

        SubjectScore build() {
            return new SubjectScore(key, ine, eligible, points, cohortCodes, open);
        }
    }
}
