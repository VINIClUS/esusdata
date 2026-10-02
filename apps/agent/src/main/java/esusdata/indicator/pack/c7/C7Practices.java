package esusdata.indicator.pack.c7;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.pack.c7.C7Cohort.Member;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The four practices of the Quadro 01 (p. 5), each over its own subpopulation (item 23, pp. 2–3).
 * A practice is met by at least one qualifying record; repeated records count once (MET-32). Where
 * the ficha leaves a case open and the person's result depends on it, the decision is {@link
 * Outcome#AMBIGUOUS_PRACTICE} or {@link Outcome#AMBIGUOUS_DENOMINATOR} and the subgroup has no value.
 */
final class C7Practices {

    static final String PRATICA_CUMPRIDA = "PRATICA_CUMPRIDA";
    static final String PRATICA_NAO_CUMPRIDA = "PRATICA_NAO_CUMPRIDA";
    static final String AMB_C7_08 = "AMB_C7_08_HPV_MOLECULAR_ANTES_2026";
    static final String AMB_C7_06 = "AMB_C7_06_DOSE_HPV_ALEM_60_MESES";
    static final String AMB_C7_05 = "AMB_C7_05_HOMEM_TRANSGENERO_9_14";

    /** "60 meses" of the HPV molecular exam (Quadro 02) and the NT 8/2026 window of C7 (AMB-C7-06). */
    static final int MONTHS_60 = 60;

    static final int MONTHS_A = 36;
    static final int MONTHS_C = 12;
    static final int MONTHS_D = 24;

    private final Map<String, List<Fact>> procedures;
    private final Map<String, List<Fact>> encounters;
    private final Map<String, List<Fact>> doses;
    private final LocalDate reference;
    private final DateWindow window60;
    private final DateWindow windowA;
    private final DateWindow windowC;
    private final DateWindow windowD;
    private final boolean hpvMolecularCounts;

    enum Outcome {
        MET,
        NOT_MET,
        /** The ficha does not say whether the person's only evidence counts. */
        AMBIGUOUS_PRACTICE,
        /** The ficha does not say whether the person belongs to the subpopulation (AMB-C7-05). */
        AMBIGUOUS_DENOMINATOR
    }

    /** One record that may support a practice, already normalized. */
    record Fact(
            String personKey,
            SourceRef sourceRef,
            LocalDate date,
            Set<String> codes,
            String cbo,
            String cnes,
            String ine) {}

    /** A person's decision for one subgroup, with the distinct records that support it. */
    record Decision(Outcome outcome, String reason, List<Fact> support) {}

    C7Practices(CanonicalDataset data, YearMonth competencia) {
        this.reference = competencia.atEndOfMonth();
        this.window60 = DateWindow.lastCivilMonths(competencia, MONTHS_60);
        this.windowA = DateWindow.lastCivilMonths(competencia, MONTHS_A);
        this.windowC = DateWindow.lastCivilMonths(competencia, MONTHS_C);
        this.windowD = DateWindow.lastCivilMonths(competencia, MONTHS_D);
        this.hpvMolecularCounts = !competencia.isBefore(C7Codes.HPV_MOLECULAR_DESDE);
        this.procedures = byPerson(
                data.procedureEvents().stream().map(C7Practices::procedureFact).toList());
        this.encounters = byPerson(
                data.careEvents().stream().map(C7Practices::encounterFact).toList());
        this.doses = byPerson(
                data.immunizations().stream().map(C7Practices::doseFact).toList());
    }

    /** The subgroups of the person's age (item 23), A to D; 14 is in B and in C. */
    static boolean inSubgroup(String code, long age) {
        return switch (code) {
            case "A" -> age >= 25 && age <= 64;
            case "B" -> age >= C7Cohort.MIN_AGE && age <= 14;
            case "C" -> age >= 14 && age <= C7Cohort.MAX_AGE;
            case "D" -> age >= 50 && age <= C7Cohort.MAX_AGE;
            default -> throw new IllegalArgumentException("unknown C7 subgroup " + code);
        };
    }

    Decision decide(String code, Member member) {
        return switch (code) {
            case "A" -> cervical(member);
            case "B" -> hpvVaccine(member);
            case "C" -> sexualHealth(member);
            case "D" -> breast(member);
            default -> throw new IllegalArgumentException("unknown C7 subgroup " + code);
        };
    }

    /**
     * A (Quadro 02): a listed code by a physician or nurse in 36 months; 02.02.10.025-1 in 60
     * months only from the competência 2026-01 on. A record of it dated before 2026-01 counts in one
     * reading of the footnote 4 and not in the other (AMB-C7-08).
     */
    private Decision cervical(Member m) {
        LocalDate since = C7Codes.HPV_MOLECULAR_DESDE.atDay(1);
        Predicate<Fact> byProfessional = f -> C7Codes.MEDICOS_ENFERMEIROS.matches(f.cbo());
        Predicate<Fact> hpv = byProfessional.and(f -> hpvMolecularCounts
                && f.codes().contains(C7Codes.A_SIGTAP_HPV_MOLECULAR)
                && window60.contains(f.date()));
        Predicate<Fact> listed = byProfessional.and(f -> windowA.contains(f.date()) && hasAny(f, C7Codes.A_36_MESES));
        return decide(
                procedures.get(m.personKey()),
                listed.or(hpv.and(f -> !f.date().isBefore(since))),
                hpv.and(f -> f.date().isBefore(since)),
                AMB_C7_08);
    }

    /**
     * B (Quadro 03): a dose of 67 or 93 given between 9 and 14 years of age, by anyone. Only "do
     * sexo feminino" (item 23, c/d); whether item 4.1.2 adds trans men is AMB-C7-05. The ficha has
     * no window; a dose older than the 60 months of the NT 8/2026 is AMB-C7-06.
     */
    private Decision hpvVaccine(Member m) {
        if (m.transMan()) {
            return new Decision(Outcome.AMBIGUOUS_DENOMINATOR, AMB_C7_05, List.of());
        }
        Predicate<Fact> qualifying = f -> hasAny(f, C7Codes.B_VACINAS)
                && !f.date().isAfter(reference)
                && ageOn(m, f.date()) >= C7Cohort.MIN_AGE
                && ageOn(m, f.date()) <= 14;
        return decide(
                doses.get(m.personKey()),
                qualifying.and(f -> window60.contains(f.date())),
                qualifying.and(f -> !window60.contains(f.date())),
                AMB_C7_06);
    }

    /** C (Quadro 04; item 24, g): an encounter by a physician or nurse with a listed code, 12 months. */
    private Decision sexualHealth(Member m) {
        return decide(
                encounters.get(m.personKey()),
                f -> C7Codes.MEDICOS_ENFERMEIROS.matches(f.cbo())
                        && windowC.contains(f.date())
                        && hasAny(f, C7Codes.C_PROBLEMAS),
                f -> false,
                null);
    }

    /** D (Quadro 05): mammography requested, evaluated or recorded by a physician or nurse, 24 months. */
    private Decision breast(Member m) {
        return decide(
                procedures.get(m.personKey()),
                f -> C7Codes.MEDICOS_ENFERMEIROS.matches(f.cbo())
                        && windowD.contains(f.date())
                        && hasAny(f, C7Codes.D_CODIGOS),
                f -> false,
                null);
    }

    private static Decision decide(
            List<Fact> facts, Predicate<Fact> certain, Predicate<Fact> ambiguous, String ambiguityReason) {
        Map<SourceRef, Fact> support = new LinkedHashMap<>();
        boolean undecided = false;
        for (Fact f : facts == null ? List.<Fact>of() : facts) {
            if (certain.test(f)) {
                support.putIfAbsent(f.sourceRef(), f);
            } else if (ambiguous.test(f)) {
                undecided = true;
            }
        }
        if (!support.isEmpty()) {
            return new Decision(Outcome.MET, PRATICA_CUMPRIDA, List.copyOf(support.values()));
        }
        if (undecided) {
            return new Decision(Outcome.AMBIGUOUS_PRACTICE, ambiguityReason, List.of());
        }
        return new Decision(Outcome.NOT_MET, PRATICA_NAO_CUMPRIDA, List.of());
    }

    private static long ageOn(Member m, LocalDate date) {
        return date.isBefore(m.birth()) ? -1 : AgeAt.completedYears(m.birth(), date, C7Cohort.ANNIVERSARY);
    }

    private static boolean hasAny(Fact fact, Set<String> codes) {
        for (String code : fact.codes()) {
            if (codes.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private static Fact procedureFact(CanonicalProcedureEvent e) {
        return new Fact(
                e.personKey(),
                e.sourceRef(),
                LocalDate.parse(e.eventDate()),
                Set.of(C7Codes.normalized(e.sigtapCode())),
                e.cbo(),
                e.cnes(),
                e.ine());
    }

    /** CIAP-2, CID-10 and ABP codes evaluated in the encounter ("Bloco Avaliação"). */
    private static Fact encounterFact(CanonicalCareEvent e) {
        List<String> codes = new ArrayList<>(e.ciapCodes());
        codes.addAll(e.cidCodes());
        return new Fact(
                e.personKey(),
                e.sourceRef(),
                LocalDate.parse(e.careDate()),
                normalizedSet(codes),
                e.cbo(),
                e.cnes(),
                e.ine());
    }

    private static Fact doseFact(CanonicalImmunization e) {
        return new Fact(
                e.personKey(),
                e.sourceRef(),
                LocalDate.parse(e.applicationDate()),
                Set.of(C7Codes.normalized(e.immunobiologicalCode())),
                e.cbo(),
                e.cnes(),
                e.ine());
    }

    private static Set<String> normalizedSet(Collection<String> codes) {
        Set<String> set = new HashSet<>();
        for (String code : codes) {
            set.add(C7Codes.normalized(code));
        }
        return Set.copyOf(set);
    }

    private static Map<String, List<Fact>> byPerson(List<Fact> facts) {
        Map<String, List<Fact>> map = new HashMap<>();
        for (Fact fact : facts) {
            map.computeIfAbsent(fact.personKey(), k -> new ArrayList<>()).add(fact);
        }
        return map;
    }
}
