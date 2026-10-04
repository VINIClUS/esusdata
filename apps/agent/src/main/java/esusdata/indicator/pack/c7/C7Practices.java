package esusdata.indicator.pack.c7;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalProcedureEvent;
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
import java.util.TreeSet;
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

    private static final String PROFESSIONAL = "PROFESSIONAL";

    private final Map<String, List<Fact>> procedures;
    private final Map<String, List<Fact>> encounters;
    private final Map<String, List<Fact>> doses;
    private final Map<String, List<Fact>> problems;
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

    /**
     * One record that may support a practice, already normalized: {@code codes} are SIGTAP/AB,
     * CIAP-2/ABP or vaccine codes, {@code cids} the CID-10 of an encounter (never mixed with CIAP-2:
     * CIAP N93 is not CID N93). {@code modality} names the information model and stage the
     * evidence shows; {@code content} identifies the same event written twice (MET-32).
     */
    record Fact(
            String personKey,
            SourceRef sourceRef,
            LocalDate date,
            Set<String> codes,
            Set<String> cids,
            String cbo,
            String cnes,
            String ine,
            String modality,
            String content) {}

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
                .filter(C7Practices::acceptedProcedure)
                .map(e -> fact(
                        e.personKey(),
                        e.sourceRef(),
                        e.eventDate(),
                        C7Codes.normalizedSet(List.of(e.sigtapCode())),
                        Set.of(),
                        e.cbo(),
                        e.cnes(),
                        e.ine(),
                        e.origin() + ":" + e.stage()))
                .toList());
        // the "Bloco Avaliação" of the encounter (C) and, for A and D, ABP022/ABP023 evaluated
        List<Fact> evaluated = new ArrayList<>();
        for (CanonicalCareEvent e : data.careEvents()) {
            evaluated.add(fact(
                    e.personKey(),
                    e.sourceRef(),
                    e.careDate(),
                    C7Codes.normalizedSet(e.ciapCodes()),
                    C7Codes.normalizedSet(e.cidCodes()),
                    e.cbo(),
                    e.cnes(),
                    e.ine(),
                    "MIAI:" + e.form()));
        }
        this.encounters = byPerson(evaluated);
        this.problems = byPerson(data.conditions().stream()
                .filter(c -> PROFESSIONAL.equals(c.basis()))
                .map(c -> fact(
                        c.personKey(),
                        c.sourceRef(),
                        c.recordedDate(),
                        C7Codes.normalizedSet(List.of(c.code())),
                        Set.of(),
                        c.cbo(),
                        null,
                        null,
                        "MIAI:PROBLEMA_AVALIADO"))
                .toList());
        this.doses = byPerson(data.immunizations().stream()
                .map(e -> fact(
                        e.personKey(),
                        e.sourceRef(),
                        e.applicationDate(),
                        C7Codes.normalizedSet(List.of(e.immunobiologicalCode())),
                        Set.of(),
                        e.cbo(),
                        e.cnes(),
                        e.ine(),
                        Boolean.TRUE.equals(e.transcription())
                                ? "MIV:TRANSCRICAO:" + e.doseCode()
                                : "MIV:" + e.doseCode()))
                .toList());
    }

    /**
     * Quadros 02 and 05 (pp. 5–6): the MIAI (exams requested or evaluated) and the MIP. A record of
     * another model, stage or with neither is never accepted silently as one of them.
     */
    private static boolean acceptedProcedure(CanonicalProcedureEvent e) {
        boolean miai = "MIAI".equals(e.origin()) && ("REQUESTED".equals(e.stage()) || "EVALUATED".equals(e.stage()));
        boolean mip = "MIP".equals(e.origin()) && "PERFORMED".equals(e.stage());
        return miai || mip;
    }

    private static Fact fact(
            String personKey,
            SourceRef ref,
            String date,
            Set<String> codes,
            Set<String> cids,
            String cbo,
            String cnes,
            String ine,
            String modality) {
        String content = String.join(
                "|",
                personKey,
                date,
                String.valueOf(new TreeSet<>(codes)),
                String.valueOf(new TreeSet<>(cids)),
                modality);
        return new Fact(personKey, ref, LocalDate.parse(date), codes, cids, cbo, cnes, ine, modality, content);
    }

    Decision decide(C7Subgroup subgroup, Member member) {
        return switch (subgroup) {
            case A -> cervical(member);
            case B -> hpvVaccine(member);
            case C -> byProfessional(encounters, member, C7Subgroup.C, C7Practices::sexualHealthCode);
            case D -> breast(member);
        };
    }

    /**
     * A (Quadro 02): a listed exam, or "ABP022" evaluated, by a physician or nurse in 36 months; 02.02.10.025-1 in 60
     * months only from the competência 2026-01 on. A record of it dated before 2026-01 counts in one
     * reading of the footnote 4 and not in the other (AMB-C7-08).
     */
    private Decision cervical(Member m) {
        LocalDate since = C7Codes.HPV_MOLECULAR_DESDE.atDay(1);
        Predicate<Fact> professional = f -> C7Codes.MEDICOS_ENFERMEIROS.matches(f.cbo());
        Predicate<Fact> hpv = professional.and(f -> hpvMolecularCounts
                && f.codes().contains(C7Codes.A_SIGTAP_HPV_MOLECULAR)
                && hpvMolecularWindow.contains(f.date()));
        Predicate<Fact> listed = professional.and(f -> windows.get(C7Subgroup.A).contains(f.date())
                && (hasAny(f, C7Codes.A_36_MESES) || f.codes().contains(C7Codes.A_ABP)));
        return decide(
                concat(procedures.get(m.personKey()), problems.get(m.personKey())),
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

    /** C (Quadro 04; item 24, g): a listed CIAP-2/ABP or CID-10 evaluated by a physician or nurse. */
    private Decision byProfessional(
            Map<String, List<Fact>> facts, Member m, C7Subgroup subgroup, Predicate<Fact> codes) {
        return decide(
                facts.get(m.personKey()), professionalIn(windows.get(subgroup)).and(codes), f -> false, null);
    }

    /**
     * D (Quadro 05): mammography in the MIAI (S/A) or MIP, or "ABP023 Rastreamento de câncer de
     * mama" evaluated, by a physician or nurse in 24 months.
     */
    private Decision breast(Member m) {
        Predicate<Fact> inWindow = professionalIn(windows.get(C7Subgroup.D));
        return decide(
                concat(procedures.get(m.personKey()), problems.get(m.personKey())),
                inWindow.and(f -> hasAny(f, C7Codes.D_CODIGOS) || f.codes().contains(C7Codes.D_ABP)),
                f -> false,
                null);
    }

    private static Predicate<Fact> professionalIn(DateWindow window) {
        return f -> C7Codes.MEDICOS_ENFERMEIROS.matches(f.cbo()) && window.contains(f.date());
    }

    private static boolean sexualHealthCode(Fact f) {
        if (hasAny(f, C7Codes.C_CIAP2_ABP)) {
            return true;
        }
        for (String cid : f.cids()) {
            if (C7Codes.cidListed(cid)) {
                return true;
            }
        }
        return false;
    }

    private static List<Fact> concat(List<Fact> first, List<Fact> second) {
        List<Fact> all = new ArrayList<>();
        if (first != null) {
            all.addAll(first);
        }
        if (second != null) {
            all.addAll(second);
        }
        return all;
    }

    private static Decision decide(
            List<Fact> facts, Predicate<Fact> certain, Predicate<Fact> ambiguous, String ambiguityReason) {
        Map<String, Fact> support = new LinkedHashMap<>();
        Map<String, Fact> undecided = new LinkedHashMap<>();
        for (Fact f : facts == null ? List.<Fact>of() : facts) {
            if (certain.test(f)) {
                support.putIfAbsent(f.content(), f);
            } else if (ambiguous.test(f)) {
                undecided.putIfAbsent(f.content(), f);
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
