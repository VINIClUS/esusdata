package esusdata.indicator.pack.c7;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.pack.c7.C7Cohort.Member;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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

    private final Map<String, List<Fact>> procedures;
    private final Map<String, List<Fact>> encounters;
    private final Map<String, List<Fact>> doses;
    private final LocalDate reference;
    private final DateWindow hpvMolecularWindow;
    private final Map<C7Subgroup, DateWindow> windows = new EnumMap<>(C7Subgroup.class);
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

    /**
     * A person's decision for one subgroup: the distinct records that support it, or — when the
     * practice is ambiguous — the records that made it so.
     */
    record Decision(Outcome outcome, String reason, List<Fact> support) {}

    C7Practices(CanonicalDataset data, YearMonth competencia) {
        this.reference = competencia.atEndOfMonth();
        for (C7Subgroup subgroup : C7Subgroup.values()) {
            windows.put(subgroup, DateWindow.lastCivilMonths(competencia, subgroup.months()));
        }
        this.hpvMolecularWindow = DateWindow.lastCivilMonths(competencia, C7Subgroup.HPV_MOLECULAR_MONTHS);
        this.hpvMolecularCounts = !competencia.isBefore(C7Codes.HPV_MOLECULAR_DESDE);
        this.procedures = byPerson(data.procedureEvents().stream()
                .map(e -> new Fact(
                        e.personKey(),
                        e.sourceRef(),
                        LocalDate.parse(e.eventDate()),
                        C7Codes.normalizedSet(List.of(e.sigtapCode())),
                        e.cbo(),
                        e.cnes(),
                        e.ine()))
                .toList());
        // CIAP-2, CID-10 and ABP codes evaluated in the encounter ("Bloco Avaliação")
        this.encounters = byPerson(data.careEvents().stream()
                .map(e -> new Fact(
                        e.personKey(),
                        e.sourceRef(),
                        LocalDate.parse(e.careDate()),
                        C7Codes.normalizedSet(e.ciapCodes(), e.cidCodes()),
                        e.cbo(),
                        e.cnes(),
                        e.ine()))
                .toList());
        this.doses = byPerson(data.immunizations().stream()
                .map(e -> new Fact(
                        e.personKey(),
                        e.sourceRef(),
                        LocalDate.parse(e.applicationDate()),
                        C7Codes.normalizedSet(List.of(e.immunobiologicalCode())),
                        e.cbo(),
                        e.cnes(),
                        e.ine()))
                .toList());
    }

    Decision decide(C7Subgroup subgroup, Member member) {
        return switch (subgroup) {
            case A -> cervical(member);
            case B -> hpvVaccine(member);
            case C -> byProfessional(encounters, member, C7Subgroup.C, C7Codes.C_PROBLEMAS);
            case D -> byProfessional(procedures, member, C7Subgroup.D, C7Codes.D_CODIGOS);
        };
    }

    /**
     * A (Quadro 02): a listed code by a physician or nurse in 36 months; 02.02.10.025-1 in 60
     * months only from the competência 2026-01 on. A record of it dated before 2026-01 counts in one
     * reading of the footnote 4 and not in the other (AMB-C7-08).
     */
    private Decision cervical(Member m) {
        LocalDate since = C7Codes.HPV_MOLECULAR_DESDE.atDay(1);
        Predicate<Fact> professional = f -> C7Codes.MEDICOS_ENFERMEIROS.matches(f.cbo());
        Predicate<Fact> hpv = professional.and(f -> hpvMolecularCounts
                && f.codes().contains(C7Codes.A_SIGTAP_HPV_MOLECULAR)
                && hpvMolecularWindow.contains(f.date()));
        Predicate<Fact> listed =
                professional.and(f -> windows.get(C7Subgroup.A).contains(f.date()) && hasAny(f, C7Codes.A_36_MESES));
        return decide(
                procedures.get(m.personKey()),
                listed.or(hpv.and(f -> !f.date().isBefore(since))),
                hpv.and(f -> f.date().isBefore(since)),
                AMB_C7_08);
    }

    /**
     * B (Quadro 03): a dose of 67 or 93 given from the 9th birthday on, by anyone. A member of B is
     * at most 14 on the reference day, so every dose up to it was given at 14 or less. Only "do sexo
     * feminino" (item 23, c/d); whether item 4.1.2 adds trans men is AMB-C7-05. The ficha has no
     * window; a dose older than the 60 months of the NT 8/2026 is AMB-C7-06.
     */
    private Decision hpvVaccine(Member m) {
        if (m.transMan()) {
            return new Decision(Outcome.AMBIGUOUS_DENOMINATOR, AMB_C7_05, List.of());
        }
        DateWindow window = windows.get(C7Subgroup.B);
        Predicate<Fact> qualifying = f -> hasAny(f, C7Codes.B_VACINAS)
                && !f.date().isAfter(reference)
                && !f.date().isBefore(AgeAt.anniversaryYears(m.birth(), C7Subgroup.B.minAge(), C7Cohort.ANNIVERSARY));
        return decide(
                doses.get(m.personKey()),
                qualifying.and(f -> window.contains(f.date())),
                qualifying.and(f -> !window.contains(f.date())),
                AMB_C7_06);
    }

    /** C (Quadro 04; item 24, g) and D (Quadro 05): a listed code by a physician or nurse in the window. */
    private Decision byProfessional(Map<String, List<Fact>> facts, Member m, C7Subgroup subgroup, Set<String> codes) {
        DateWindow window = windows.get(subgroup);
        return decide(
                facts.get(m.personKey()),
                f -> C7Codes.MEDICOS_ENFERMEIROS.matches(f.cbo()) && window.contains(f.date()) && hasAny(f, codes),
                f -> false,
                null);
    }

    private static Decision decide(
            List<Fact> facts, Predicate<Fact> certain, Predicate<Fact> ambiguous, String ambiguityReason) {
        Map<SourceRef, Fact> support = new LinkedHashMap<>();
        Map<SourceRef, Fact> undecided = new LinkedHashMap<>();
        for (Fact f : facts == null ? List.<Fact>of() : facts) {
            if (certain.test(f)) {
                support.putIfAbsent(f.sourceRef(), f);
            } else if (ambiguous.test(f)) {
                undecided.putIfAbsent(f.sourceRef(), f);
            }
        }
        if (!support.isEmpty()) {
            return new Decision(Outcome.MET, PRATICA_CUMPRIDA, List.copyOf(support.values()));
        }
        if (!undecided.isEmpty()) {
            return new Decision(Outcome.AMBIGUOUS_PRACTICE, ambiguityReason, List.copyOf(undecided.values()));
        }
        return new Decision(Outcome.NOT_MET, PRATICA_NAO_CUMPRIDA, List.of());
    }

    private static boolean hasAny(Fact fact, Set<String> codes) {
        for (String code : fact.codes()) {
            if (codes.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<Fact>> byPerson(List<Fact> facts) {
        Map<String, List<Fact>> map = new HashMap<>();
        for (Fact fact : facts) {
            map.computeIfAbsent(fact.personKey(), k -> new ArrayList<>()).add(fact);
        }
        return map;
    }
}
