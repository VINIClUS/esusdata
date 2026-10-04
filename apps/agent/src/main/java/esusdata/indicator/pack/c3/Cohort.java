package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The cohort of one competência, in the contract's order: activity in the competência (4.1),
 * link at the cutoff (item 14), death (item 15), abortion (24 g), the ficha's ambiguities and,
 * last, eligibility with the end date used (MET-21).
 */
final class Cohort {

    /** Whether an episode is "ativa na competência" (4.1). */
    enum Activity {
        ACTIVE,
        /**
         * Not active under every reading of the dates: only the boundary day D + 42 falls in the
         * competência (AMB-C3-04), or the readings disagree (AMB-C3-03).
         */
        BOUNDARY,
        INACTIVE
    }

    private final LocalDate firstDay;
    private final LocalDate cutoff;
    private final TeamTypes teamTypes;

    Cohort(YearMonth competencia, LocalDate cutoff, TeamTypes teamTypes) {
        this.teamTypes = teamTypes;
        this.firstDay = competencia.atDay(1);
        this.cutoff = cutoff;
    }

    /** Across the readings of the dates: inactive in all, or the combined activity. */
    Activity activity(Episode episode) {
        List<Activity> perReading = new ArrayList<>();
        for (GestationWindow reading : episode.readings()) {
            perReading.add(activity(reading));
        }
        if (perReading.stream().allMatch(a -> a == Activity.INACTIVE)) {
            return Activity.INACTIVE;
        }
        return perReading.stream().allMatch(a -> a == Activity.ACTIVE) ? Activity.ACTIVE : Activity.BOUNDARY;
    }

    /** The cohort decision of an active (or boundary) episode. */
    Verdict decide(Episode episode, PersonRecords person, RegistrationLink link, Activity activity) {
        LocalDate end = episode.primary().end();
        Verdict personal = personal(person, link, end);
        if (personal != null) {
            return personal;
        }
        Verdict abortion = Verdict.agreed(perReading(episode, r -> abortion(person, r)), end);
        if (abortion != null) {
            return abortion;
        }
        if (activity != Activity.ACTIVE) {
            boolean anyActive = episode.readings().stream().anyMatch(r -> activity(r) == Activity.ACTIVE);
            return Verdict.ambiguous(anyActive ? Ambiguity.AMB_C3_03 : Ambiguity.AMB_C3_04, end);
        }
        if (episode.datesAmbiguity() != null) {
            return Verdict.ambiguous(episode.datesAmbiguity(), end);
        }
        Verdict code = Verdict.agreed(perReading(episode, r -> pregnancyCode(person, r, end)), end);
        if (code != null) {
            return code;
        }
        String reason = switch (episode.primary().endSource()) {
            case RECORDED_OUTCOME -> C3Reasons.ELEGIVEL_DESFECHO_REGISTRADO;
            case LPC_RESOLUTION -> C3Reasons.ELEGIVEL_DESFECHO_RESOLUCAO_LPC;
            case SUBSTITUTE_294 -> C3Reasons.ELEGIVEL_DATA_SUBSTITUTIVA_294D;
        };
        return Verdict.eligible(reason, end);
    }

    /**
     * Link, team type (24 b: only types 70 and 76 when the type is known) and death, which do not
     * depend on the episode's dates; {@code null} when they hold.
     */
    Verdict personal(PersonRecords person, RegistrationLink link, LocalDate eventDate) {
        if (C3Reasons.EXCLUIDO_OBITO.equals(link.exclusion())) {
            return Verdict.excluded(C3Reasons.EXCLUIDO_OBITO, link.since());
        }
        if (link.exclusion() != null) {
            return Verdict.excluded(link.exclusion(), eventDate);
        }
        if (teamTypes.outOfScope(link.ine())) {
            return Verdict.excluded(C3Reasons.EXCLUIDO_EQUIPE_FORA_DO_ESCOPO, eventDate);
        }
        for (CanonicalPerson record : person.persons()) {
            LocalDate death = C3Dates.parse(record.deathDate());
            if (death != null && !death.isAfter(cutoff)) {
                return Verdict.excluded(C3Reasons.EXCLUIDO_OBITO, death);
            }
        }
        return null;
    }

    /** True when {@code date} falls in {@code [first day of the competência, cutoff]}. */
    boolean inCompetencia(LocalDate date) {
        return C3Dates.within(date, firstDay, cutoff);
    }

    LocalDate firstDay() {
        return firstDay;
    }

    private Activity activity(GestationWindow reading) {
        boolean overlaps =
                !reading.dum().isAfter(cutoff) && !reading.lastCertainDay().isBefore(firstDay);
        if (overlaps) {
            return Activity.ACTIVE;
        }
        return inCompetencia(reading.boundaryDay()) ? Activity.BOUNDARY : Activity.INACTIVE;
    }

    private static List<Verdict> perReading(Episode episode, Function<GestationWindow, Verdict> check) {
        List<Verdict> verdicts = new ArrayList<>();
        for (GestationWindow reading : episode.readings()) {
            verdicts.add(check.apply(reading));
        }
        return verdicts;
    }

    /**
     * 24 g: an exact exclusion code in {@code [DUM, D]} excludes (from the earliest such date); a
     * code that matches only by prefix is AMB-C3-08; a code in {@code (D, D + 42]}, or an LPC
     * condition that is not active or latent ("ativos", AMB-C3-07 (ii)), is AMB-C3-07.
     */
    private static Verdict abortion(PersonRecords person, GestationWindow reading) {
        LocalDate exact = null;
        LocalDate prefix = null;
        LocalDate other = null;
        for (CodeAt code : exclusionCodes(person)) {
            switch (code.bucket(reading)) {
                case EXCLUDES -> exact = earliest(exact, code.date());
                case PREFIX_ONLY -> prefix = earliest(prefix, code.date());
                case UNDECIDED -> other = earliest(other, code.date());
                case OUTSIDE -> {
                    // another episode's code
                }
            }
        }
        if (exact != null) {
            return Verdict.excluded(C3Reasons.EXCLUIDO_ABORTO, exact);
        }
        if (prefix != null) {
            return Verdict.ambiguous(Ambiguity.AMB_C3_08, prefix);
        }
        return other == null ? null : Verdict.ambiguous(Ambiguity.AMB_C3_07, other);
    }

    /** What a 24 g code means for one reading of an episode. */
    private enum Bucket {
        EXCLUDES,
        PREFIX_ONLY,
        UNDECIDED,
        OUTSIDE
    }

    /**
     * A 24 g code found on a date; {@code active} is false for an LPC condition that is not active
     * or latent ("ativos", AMB-C3-07 (ii)).
     */
    private record CodeAt(LocalDate date, CodeMatch match, boolean active) {
        Bucket bucket(GestationWindow reading) {
            if (reading.inPregnancy(date)) {
                if (!active) {
                    return Bucket.UNDECIDED;
                }
                return match == CodeMatch.EXACT ? Bucket.EXCLUDES : Bucket.PREFIX_ONLY;
            }
            boolean puerperium = C3Dates.within(date, reading.end().plusDays(1), reading.boundaryDay());
            return puerperium ? Bucket.UNDECIDED : Bucket.OUTSIDE;
        }
    }

    private static List<CodeAt> exclusionCodes(PersonRecords person) {
        List<CodeAt> codes = new ArrayList<>();
        for (CanonicalCareEvent event : person.individualCare()) {
            add(codes, event.careDate(), CodeMatch.of(event, C3Codes.EXCLUSION_CIAP, C3Codes.EXCLUSION_CID), true);
        }
        for (CanonicalPregnancyOutcome outcome : person.outcomes()) {
            for (String code : outcome.codes()) {
                add(
                        codes,
                        outcome.outcomeDate(),
                        CodeMatch.any(code, C3Codes.EXCLUSION_CIAP, C3Codes.EXCLUSION_CID),
                        true);
            }
        }
        for (CanonicalCondition condition : person.conditions()) {
            add(
                    codes,
                    condition.recordedDate(),
                    CodeMatch.of(condition, C3Codes.EXCLUSION_CIAP, C3Codes.EXCLUSION_CID),
                    C3Codes.active(condition));
        }
        return codes;
    }

    private static void add(List<CodeAt> codes, String date, CodeMatch match, boolean active) {
        LocalDate parsed = C3Dates.parse(date);
        if (match.found() && parsed != null) {
            codes.add(new CodeAt(parsed, match, active));
        }
    }

    /**
     * 24 f: a pregnancy code in some MIAI, or in the LPC, within {@code [DUM, D]}. Exact ⇒ no
     * objection; only by prefix ⇒ AMB-C3-08; none ⇒ AMB-C3-03 (iii).
     */
    private static Verdict pregnancyCode(PersonRecords person, GestationWindow reading, LocalDate eventDate) {
        CodeMatch best = CodeMatch.NONE;
        for (CanonicalCareEvent event : person.individualCare()) {
            if (reading.inPregnancy(C3Dates.parse(event.careDate()))) {
                best = CodeMatch.better(best, CodeMatch.of(event, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID));
            }
        }
        for (CanonicalCondition condition : person.conditions()) {
            if (reading.inPregnancy(C3Dates.parse(condition.recordedDate()))) {
                best = CodeMatch.better(best, CodeMatch.of(condition, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID));
            }
        }
        return switch (best) {
            case EXACT -> null;
            case PREFIX -> Verdict.ambiguous(Ambiguity.AMB_C3_08, eventDate);
            case NONE -> Verdict.ambiguous(Ambiguity.AMB_C3_03, eventDate);
        };
    }

    private static LocalDate earliest(LocalDate held, LocalDate candidate) {
        return held == null || candidate.isBefore(held) ? candidate : held;
    }
}
