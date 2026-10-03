package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The four good practices of Quadro 01 (p. 4) for one eligible person, each decided at most once
 * however many records prove it (MET-32), with the minimal records that support the decision.
 * Windows are civil months ending with the competência (AMB-C5-02) and nothing after the cutoff
 * counts. Every record needs a CBO of its practice's quadro (AMB-C5-08).
 */
final class C5Practices {

    static final String A = "A";
    static final String B = "B";
    static final String C = "C";
    static final String D = "D";

    static final String MET = "CUMPRIDA";
    static final String NOT_RECORDED = "SEM_REGISTRO_NA_JANELA";
    static final String SHORT_INTERVAL = "INTERVALO_MENOR_QUE_30_DIAS";

    /** «com intervalo mínimo de 30 (trinta) dias» (item 16, p. 2), in calendar days (AMB-C5-03). */
    static final long MIN_VISIT_INTERVAL_DAYS = 30;

    private static final String INDIVIDUAL = "INDIVIDUAL";
    private static final String PERFORMED = "PERFORMED";
    private static final Pattern NOT_DIGIT = Pattern.compile("\\D");

    /** Quadro 03: the SIGTAP code from the MIAI or the MIP. */
    private static final Set<String> BLOOD_PRESSURE_PROCEDURES = Set.of(C5Event.MIAI, C5Event.MIP);

    /** Quadro 04: SIGTAP codes only from the MIP; in the MIAI only the PEC's own fields count. */
    private static final Set<String> ANTHROPOMETRY_PROCEDURES = Set.of(C5Event.MIP);

    /** Measurements count only from a collective activity (MIAC); a MIP measure needs its code. */
    private static final Set<String> COLLECTIVE = Set.of(C5Event.MIAC);

    /**
     * Orders records by date; {@link BinaryOperator#maxBy} and {@link BinaryOperator#minBy} keep
     * the first one read on the same date.
     */
    private static final Comparator<C5Event> BY_DATE = Comparator.comparing(C5Event::date);

    private final DateWindow sixMonths;
    private final DateWindow twelveMonths;
    private final LocalDate cutoff;
    private final Map<String, List<CanonicalCareEvent>> careEvents;
    private final Map<String, List<CanonicalProcedureEvent>> procedures;
    private final Map<String, List<CanonicalMeasurement>> measurements;
    private final Map<String, List<CanonicalHomeVisit>> visits;

    /**
     * One practice's decision, with the records behind it in evidence order. An {@code ambiguous}
     * practice is one the ficha does not decide for the person (AMB-C5-01): {@code met} then only
     * says whether it was observed, and it earns no points.
     */
    record Outcome(String code, boolean met, String reasonCode, List<C5Event> support, boolean ambiguous) {
        Outcome {
            support = List.copyOf(support);
        }

        Outcome(String code, boolean met, String reasonCode, List<C5Event> support) {
            this(code, met, reasonCode, support, false);
        }

        /**
         * The same observation, undecided by the ficha for {@code reason}; its records stay as
         * support and the reason code keeps what was observed ({@code AMB-C5-01:CUMPRIDA}), so the
         * observed count can be rebuilt from the evidence (ENG-36).
         */
        Outcome undecided(String reason) {
            return new Outcome(code, met, reason + ":" + reasonCode, support, true);
        }
    }

    C5Practices(CanonicalDataset data, EvaluationContext context) {
        sixMonths = DateWindow.lastCivilMonths(context.competencia(), 6);
        twelveMonths = DateWindow.lastCivilMonths(context.competencia(), 12);
        cutoff = context.dataCutoff();
        careEvents = byPerson(data.careEvents(), CanonicalCareEvent::personKey);
        procedures = byPerson(data.procedureEvents(), CanonicalProcedureEvent::personKey);
        measurements = byPerson(data.measurements(), CanonicalMeasurement::personKey);
        visits = byPerson(data.homeVisits(), CanonicalHomeVisit::personKey);
    }

    /** Practices A, B, C and D, in that order. */
    List<Outcome> evaluate(String person) {
        return List.of(consultation(person), bloodPressure(person), anthropometry(person), homeVisits(person));
    }

    /**
     * A — Quadro 02 (p. 4): an individual encounter (MIAI) by a physician or nurse «com
     * identificação do Problema/Condição Avaliada» (item 24 e, p. 2), presential or remote; any
     * problem, not only hypertension. SIGTAP consultation procedures do not count (AMB-C5-05).
     */
    private Outcome consultation(String person) {
        List<C5Event> found = individual(person, C5Codes.CBO_CONSULTA)
                .filter(e -> hasAny(e.ciapCodes()) || hasAny(e.cidCodes()))
                .map(C5Event::of)
                .filter(e -> within(e, sixMonths))
                .toList();
        return latestOf(A, found);
    }

    /**
     * B — Quadro 03 (p. 4–5): the PEC's «pressão arterial» (mmHg) field or SIGTAP «03.01.10.003-9»
     * in the MIAI; the SIGTAP code in a procedure from the MIAI or the MIP («com os códigos SIGTAP
     * especificados»); or the field in a collective activity (MIAC, AMB-C5-06). A MIP measurement
     * without the code does not count, and the visit form has no blood-pressure field in the
     * canonical record (lacuna L6).
     */
    private Outcome bloodPressure(String person) {
        Stream<C5Event> encounters = individual(person, C5Codes.CBO_AFERICAO_PA)
                .filter(e -> hasPressure(e.systolicMmhg(), e.diastolicMmhg())
                        || hasCode(e.proceduresPerformed(), C5Codes.SIGTAP_AFERICAO_PA))
                .map(C5Event::of);
        Stream<C5Event> procedureEvents = performed(person, C5Codes.CBO_AFERICAO_PA, BLOOD_PRESSURE_PROCEDURES)
                .filter(p -> C5Codes.SIGTAP_AFERICAO_PA.equals(digits(p.sigtapCode())))
                .map(C5Event::of);
        Stream<C5Event> collective = collective(person, C5Codes.CBO_AFERICAO_PA)
                .filter(m -> hasPressure(m.systolicMmhg(), m.diastolicMmhg()))
                .map(C5Event::of);
        List<C5Event> found = Stream.of(encounters, procedureEvents, collective)
                .flatMap(Function.identity())
                .filter(e -> within(e, sixMonths))
                .toList();
        return latestOf(B, found);
    }

    /**
     * C — Quadro 04 (p. 5): weight and height on the same civil date, from any combination of
     * accepted records (AMB-C5-07): the PEC's own fields in the MIAI, the MIAC and the visit of an
     * ACS/TACS with its reason (item 24 e), or the SIGTAP codes in a MIP procedure —
     * «01.01.04.002-4» alone gives both. Different days never meet.
     */
    private Outcome anthropometry(String person) {
        C5Anthropometry days = new C5Anthropometry(e -> within(e, twelveMonths));
        CboGroups cbo = C5Codes.CBO_ANTROPOMETRIA;
        individual(person, cbo).forEach(e -> days.add(C5Event.of(e), positive(e.weightKg()), positive(e.heightCm())));
        collective(person, cbo).forEach(m -> days.add(C5Event.of(m), positive(m.weightKg()), positive(m.heightCm())));
        reportedVisits(person).forEach(v -> days.add(C5Event.of(v), positive(v.weightKg()), positive(v.heightCm())));
        performed(person, cbo, ANTHROPOMETRY_PROCEDURES).forEach(p -> {
            List<String> code = List.of(digits(p.sigtapCode()));
            days.add(C5Event.of(p), C5Anthropometry.givesWeight(code), C5Anthropometry.givesHeight(code));
        });
        List<C5Event> support = days.latestComplete();
        return support.isEmpty() ? notMet(C, NOT_RECORDED, List.of()) : new Outcome(C, true, MET, support);
    }

    /**
     * D — Quadro 05 (p. 5): two home visits by ACS/TACS «com preenchimento do ‘‘motivo da visita’’»
     * (item 24 e, p. 2–3) at least 30 days apart within 12 months, whatever the outcome
     * (AMB-C5-09). Fewer than two distinct dates is no record of the practice (its visits still
     * shown); otherwise the latest and the earliest visit support the decision, and they are 30
     * days apart exactly when some pair is.
     */
    private Outcome homeVisits(String person) {
        List<C5Event> found = reportedVisits(person)
                .map(C5Event::of)
                .filter(e -> within(e, twelveMonths))
                .toList();
        if (found.stream().map(C5Event::date).distinct().count() < 2) {
            return notMet(D, NOT_RECORDED, found.stream().distinct().toList());
        }
        C5Event latest = found.stream().reduce(BinaryOperator.maxBy(BY_DATE)).orElseThrow();
        C5Event earliest = found.stream().reduce(BinaryOperator.minBy(BY_DATE)).orElseThrow();
        List<C5Event> support = List.of(latest, earliest);
        boolean met = ChronoUnit.DAYS.between(earliest.date(), latest.date()) >= MIN_VISIT_INTERVAL_DAYS;
        return met ? new Outcome(D, true, MET, support) : notMet(D, SHORT_INTERVAL, support);
    }

    private Stream<CanonicalCareEvent> individual(String person, CboGroups cbo) {
        return of(careEvents, person).stream()
                .filter(e -> INDIVIDUAL.equalsIgnoreCase(e.form()))
                .filter(e -> cbo.matches(e.cbo()));
    }

    /**
     * Procedures done (stage {@code PERFORMED}) in one of {@code models}; a request proves nothing
     * (§1.7), and a missing stage or origin is not assumed.
     */
    private Stream<CanonicalProcedureEvent> performed(String person, CboGroups cbo, Set<String> models) {
        return of(procedures, person).stream()
                .filter(p -> PERFORMED.equalsIgnoreCase(p.stage()))
                .filter(p -> C5Event.originIn(p.origin(), models))
                .filter(p -> cbo.matches(p.cbo()));
    }

    /** Collective-activity measurements (MIAC, AMB-C5-06) by the practice's CBO. */
    private Stream<CanonicalMeasurement> collective(String person, CboGroups cbo) {
        return of(measurements, person).stream()
                .filter(m -> C5Event.originIn(m.origin(), COLLECTIVE))
                .filter(m -> cbo.matches(m.cbo()));
    }

    /** Visits by ACS/TACS (Quadro 05) with at least one reason filled in (item 24 e). */
    private Stream<CanonicalHomeVisit> reportedVisits(String person) {
        return of(visits, person).stream()
                .filter(v -> C5Codes.CBO_VISITA.matches(v.cbo()))
                .filter(v -> hasAny(v.reasonCodes()));
    }

    private boolean within(C5Event e, DateWindow window) {
        return e.date() != null && window.contains(e.date()) && !e.date().isAfter(cutoff);
    }

    /** The practice met by its most recent record; on the same date the first one read. */
    private static Outcome latestOf(String code, List<C5Event> found) {
        if (found.isEmpty()) {
            return notMet(code, NOT_RECORDED, List.of());
        }
        C5Event latest = found.stream().reduce(BinaryOperator.maxBy(BY_DATE)).orElseThrow();
        return new Outcome(code, true, MET, List.of(latest));
    }

    private static Outcome notMet(String code, String reason, List<C5Event> support) {
        return new Outcome(code, false, reason, support);
    }

    private static boolean hasPressure(String systolic, String diastolic) {
        return positive(systolic) && positive(diastolic);
    }

    /** A measure counts only as a decimal greater than zero; invalid text does not count. */
    private static boolean positive(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return new BigDecimal(value.strip()).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasAny(List<String> codes) {
        return codes.stream().anyMatch(C5Practices::notBlank);
    }

    private static boolean hasCode(List<String> codes, String code) {
        return digitsOf(codes).contains(code);
    }

    private static List<String> digitsOf(List<String> codes) {
        return codes.stream().map(C5Practices::digits).toList();
    }

    private static String digits(String code) {
        return code == null ? "" : NOT_DIGIT.matcher(code).replaceAll("");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> of(Map<String, List<T>> index, String person) {
        return index.getOrDefault(person, List.of());
    }

    private static <T> Map<String, List<T>> byPerson(List<T> records, Function<T, String> key) {
        Map<String, List<T>> index = new HashMap<>();
        for (T r : records) {
            index.computeIfAbsent(key.apply(r), k -> new ArrayList<>()).add(r);
        }
        return index;
    }
}
