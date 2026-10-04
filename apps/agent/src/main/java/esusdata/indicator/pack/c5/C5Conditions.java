package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalCondition;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The hypertension conditions of each person as of the cutoff.
 *
 * <ul>
 *   <li>Identification (item 5, p. 1): a listed code evaluated «por enfermeira(o) e/ou médica(o) da
 *       APS» — basis {@code PROFESSIONAL} and a CBO of Quadro 02 — recorded «desde 2013». A
 *       self-reported condition never identifies (the ficha does not cite it).
 *   <li>Interruption (item 15, p. 2): «todas as condições ou problemas marcados como "resolvidos" no
 *       PEC», read from every professional row of a listed code up to the cutoff, whoever evaluated
 *       it. Each code is decided by its latest row; on the same date an unresolved row wins.
 * </ul>
 *
 * <p>Status and basis outside the contract's vocabulary are never converted in silence: an unknown
 * status counts as not resolved, an unknown basis does not identify, and both are counted for the
 * AMB-C5-04 diagnostic.
 */
final class C5Conditions {

    /** «em pelo menos uma ocasião desde 2013» (items 5 and 14, p. 1). */
    static final LocalDate SINCE = LocalDate.of(2013, 1, 1);

    private static final String PROFESSIONAL = "PROFESSIONAL";
    private static final String SELF_REPORTED = "SELF_REPORTED";
    private static final Set<String> BASES = Set.of(PROFESSIONAL, SELF_REPORTED);

    /** LEDI {@code 2} and the textual readings of «resolvidos» or «concluídos» (item 4.1, p. 3–4). */
    private static final Set<String> RESOLVED = Set.of("2", "RESOLVIDO", "RESOLVED", "CONCLUIDO", "CONCLUDED");

    /** LEDI {@code 0} active and {@code 1} latent, and their textual readings. */
    private static final Set<String> NOT_RESOLVED = Set.of("0", "1", "ATIVO", "ACTIVE", "LATENTE", "LATENT");

    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    /** What a person's listed professional conditions say at the cutoff. */
    enum State {
        /** At least one listed code is not resolved. */
        ACTIVE,
        /** «todas as condições ou problemas marcados como "resolvidos"» (item 15, p. 2). */
        ALL_RESOLVED
    }

    private final Set<String> identified = new HashSet<>();
    private final Set<String> notSelfReported = new HashSet<>();
    private final Map<String, Map<String, CodeState>> codes = new HashMap<>();
    private long outOfVocabulary;

    private C5Conditions(List<CanonicalCondition> conditions, LocalDate cutoff) {
        for (CanonicalCondition c : conditions) {
            if (isEligible(c)) {
                read(c, cutoff);
            }
        }
    }

    static C5Conditions of(List<CanonicalCondition> conditions, LocalDate cutoff) {
        return new C5Conditions(conditions, cutoff);
    }

    /** Whether the row names a listed code with any basis — the reconstructed population (ENG-36). */
    static boolean isEligible(CanonicalCondition c) {
        return C5Codes.isEligibleCondition(c.codeSystem(), c.code());
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

    /** Identified by a physician's or nurse's evaluation since 2013 (item 5, p. 1). */
    boolean isIdentified(String person) {
        return identified.contains(person);
    }

    /** Has a listed row that is not self-reported (professional or of unknown basis). */
    boolean hasNonSelfReportedRow(String person) {
        return notSelfReported.contains(person);
    }

    /** The state of an identified person's listed codes. */
    State state(String person) {
        boolean allResolved =
                codes.getOrDefault(person, Map.of()).values().stream().allMatch(CodeState::resolved);
        return allResolved ? State.ALL_RESOLVED : State.ACTIVE;
    }

    /** Listed rows whose status or basis is outside the vocabulary (AMB-C5-04 diagnostic). */
    long outOfVocabularyCount() {
        return outOfVocabulary;
    }

    private void read(CanonicalCondition c, LocalDate cutoff) {
        String basis = normalized(c.basis());
        String status = normalized(c.status());
        if (!in(BASES, basis) || (!in(RESOLVED, status) && !in(NOT_RESOLVED, status))) {
            outOfVocabulary++;
        }
        if (!SELF_REPORTED.equals(basis)) {
            notSelfReported.add(c.personKey());
        }
        LocalDate recorded = C5Event.date(c.recordedDate());
        if (!PROFESSIONAL.equals(basis) || recorded == null || recorded.isAfter(cutoff)) {
            return;
        }
        if (!recorded.isBefore(SINCE) && C5Codes.CBO_CONSULTA.matches(c.cbo())) {
            identified.add(c.personKey());
        }
        CodeState state = new CodeState(recorded, isResolved(status, c, cutoff));
        codes.computeIfAbsent(c.personKey(), k -> new HashMap<>())
                .merge(C5Codes.conditionKey(c.codeSystem(), c.code()), state, CodeState::latest);
    }

    private static boolean isResolved(String status, CanonicalCondition c, LocalDate cutoff) {
        LocalDate resolvedOn = C5Event.date(c.resolvedDate());
        boolean resolvedByCutoff = resolvedOn == null || !resolvedOn.isAfter(cutoff);
        return in(RESOLVED, status) && resolvedByCutoff;
    }

    /** {@link Set#of} rejects {@code null} lookups; a missing value is in no vocabulary. */
    private static boolean in(Set<String> vocabulary, String value) {
        return value != null && vocabulary.contains(value);
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
