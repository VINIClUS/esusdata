package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalCondition;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The hypertension condition of each person as of the cutoff (item 14, p. 1; item 4.1, p. 3–4; item
 * 15, p. 2). Only a listed code evaluated «por enfermeira(o) e/ou médica(o) da APS» (item 5, p. 1:
 * basis professional and a CBO of Quadro 02) and recorded «desde 2013» identifies the person; a self-reported condition does not (the ficha never cites it). Each code is decided by its
 * latest row; the person is interrupted only when every eligible code is «resolvidos» or
 * «concluídos» (AMB-C5-04).
 */
final class C5Conditions {

    /** «em pelo menos uma ocasião desde 2013» (items 5 and 14, p. 1). */
    static final LocalDate SINCE = LocalDate.of(2013, 1, 1);

    static final String SELF_REPORTED = "SELF_REPORTED";

    static final String PROFESSIONAL = "PROFESSIONAL";

    private static final Set<String> RESOLVED = Set.of("RESOLVIDO", "RESOLVED", "2", "CONCLUIDO", "CONCLUDED");
    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    /** What a person's eligible professional conditions say at the cutoff. */
    enum State {
        /** At least one eligible code is not resolved. */
        ACTIVE,
        /** «todas as condições ou problemas marcados como "resolvidos"» (item 15, p. 2). */
        ALL_RESOLVED
    }

    private C5Conditions() {}

    /** The state of every person with an eligible professional condition; others are absent. */
    static Map<String, State> professionalStates(List<CanonicalCondition> conditions, LocalDate cutoff) {
        Map<String, Map<String, CodeState>> byPerson = new HashMap<>();
        for (CanonicalCondition c : conditions) {
            LocalDate recorded = C5Event.date(c.recordedDate());
            if (identifies(c, recorded, cutoff)) {
                CodeState state = new CodeState(recorded, isResolved(c, cutoff));
                byPerson.computeIfAbsent(c.personKey(), k -> new HashMap<>())
                        .merge(codeKey(c), state, CodeState::latest);
            }
        }
        Map<String, State> states = new HashMap<>();
        byPerson.forEach((person, codes) -> states.put(person, stateOf(codes)));
        return states;
    }

    /** Whether the row names a listed code with any basis — the reconstructed population (ENG-36). */
    static boolean isEligible(CanonicalCondition c) {
        return C5Codes.isEligibleCondition(c.codeSystem(), c.code());
    }

    static boolean isSelfReported(CanonicalCondition c) {
        return SELF_REPORTED.equals(normalized(c.basis()));
    }

    /** {@code PROFESSIONAL}, or no basis at all (the source did not say it was self-reported). */
    static boolean isProfessional(CanonicalCondition c) {
        return c.basis() == null || PROFESSIONAL.equals(normalized(c.basis()));
    }

    /**
     * Rows with an unlisted code next to the list ({@link C5Codes#isUnlistedNeighbor}): shown as a
     * diagnostic for the reconciliation (AMB-C5-04), never included.
     */
    static long outOfListCount(List<CanonicalCondition> conditions) {
        return conditions.stream()
                .filter(c -> C5Codes.isUnlistedNeighbor(c.codeSystem(), c.code()))
                .count();
    }

    /** Upper case, no accents, trimmed; {@code null} stays {@code null}. */
    static String normalized(String text) {
        if (text == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(text.strip(), Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toUpperCase(Locale.ROOT);
    }

    private static boolean identifies(CanonicalCondition c, LocalDate recorded, LocalDate cutoff) {
        return recorded != null
                && !recorded.isBefore(SINCE)
                && !recorded.isAfter(cutoff)
                && isEligible(c)
                && isProfessional(c)
                && C5Codes.CBO_CONSULTA.matches(c.cbo());
    }

    private static boolean isResolved(CanonicalCondition c, LocalDate cutoff) {
        LocalDate resolvedOn = C5Event.date(c.resolvedDate());
        boolean resolvedByCutoff = resolvedOn == null || !resolvedOn.isAfter(cutoff);
        return c.status() != null && RESOLVED.contains(normalized(c.status())) && resolvedByCutoff;
    }

    private static String codeKey(CanonicalCondition c) {
        return C5Codes.conditionKey(c.codeSystem(), c.code());
    }

    private static State stateOf(Map<String, CodeState> codes) {
        boolean allResolved = codes.values().stream().allMatch(CodeState::resolved);
        return allResolved ? State.ALL_RESOLVED : State.ACTIVE;
    }

    /** One code's latest row; on the same date an unresolved row wins. */
    private record CodeState(LocalDate recorded, boolean resolved) {
        CodeState latest(CodeState other) {
            if (other.recorded.isAfter(recorded)) {
                return other;
            }
            if (other.recorded.isEqual(recorded) && !other.resolved) {
                return other;
            }
            return this;
        }
    }
}
