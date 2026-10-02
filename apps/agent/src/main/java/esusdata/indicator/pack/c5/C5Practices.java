package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        /** The same observation, undecided by the ficha for {@code reason}; its records stay as support. */
        Outcome undecided(String reason) {
            return new Outcome(code, met, reason, support, true);
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
     * in the MIAI, the procedure in the MIP (stage performed), or the field in a MIP/MIAC
     * measurement (AMB-C5-06). The visit form has no blood-pressure field in the canonical record.
     */
    private Outcome bloodPressure(String person) {
        Stream<C5Event> encounters = individual(person, C5Codes.CBO_AFERICAO_PA)
                .filter(e -> hasPressure(e.systolicMmhg(), e.diastolicMmhg())
                        || hasCode(e.proceduresPerformed(), C5Codes.SIGTAP_AFERICAO_PA))
                .map(C5Event::of);
        Stream<C5Event> procedureEvents = performed(person, C5Codes.CBO_AFERICAO_PA)
                .filter(p -> C5Codes.SIGTAP_AFERICAO_PA.equals(digits(p.sigtapCode())))
                .map(C5Event::of);
        Stream<C5Event> measured = measured(person, C5Codes.CBO_AFERICAO_PA)
                .filter(m -> hasPressure(m.systolicMmhg(), m.diastolicMmhg()))
                .map(C5Event::of);
        List<C5Event> found = Stream.of(encounters, procedureEvents, measured)
                .flatMap(Function.identity())
                .filter(e -> within(e, sixMonths))
                .toList();
        return latestOf(B, found);
    }

    /**
     * C — Quadro 04 (p. 5): weight and height on the same civil date, from any combination of
     * accepted records (AMB-C5-07); «01.01.04.002-4» alone gives both. Different days never meet.
     */
    private Outcome anthropometry(String person) {
        C5Anthropometry days = new C5Anthropometry(e -> within(e, twelveMonths));
        CboGroups cbo = C5Codes.CBO_ANTROPOMETRIA;
        individual(person, cbo)
                .forEach(e -> days.add(
                        C5Event.of(e),
                        notBlank(e.weightKg()) || C5Anthropometry.givesWeight(digitsOf(e.proceduresPerformed())),
                        notBlank(e.heightCm()) || C5Anthropometry.givesHeight(digitsOf(e.proceduresPerformed()))));
        measured(person, cbo).forEach(m -> days.add(C5Event.of(m), notBlank(m.weightKg()), notBlank(m.heightCm())));
        of(visits, person).stream()
                .filter(v -> cbo.matches(v.cbo()))
                .forEach(v -> days.add(C5Event.of(v), notBlank(v.weightKg()), notBlank(v.heightCm())));
        performed(person, cbo).forEach(p -> {
            List<String> code = List.of(digits(p.sigtapCode()));
            days.add(C5Event.of(p), C5Anthropometry.givesWeight(code), C5Anthropometry.givesHeight(code));
        });
        List<C5Event> support = days.latestComplete();
        return support.isEmpty() ? notMet(C, NOT_RECORDED, List.of()) : new Outcome(C, true, MET, support);
    }

    /**
     * D — Quadro 05 (p. 5): two home visits by ACS/TACS at least 30 days apart within 12 months,
     * whatever the outcome (AMB-C5-09) or the reason (mandatory in the LEDI). Fewer than two
     * distinct dates is no record of the practice; otherwise the latest and the earliest visit
     * support the decision, and they are 30 days apart exactly when some pair is.
     */
    private Outcome homeVisits(String person) {
        List<C5Event> found = of(visits, person).stream()
                .filter(v -> C5Codes.CBO_VISITA.matches(v.cbo()))
                .map(C5Event::of)
                .filter(e -> within(e, twelveMonths))
                .toList();
        if (found.stream().map(C5Event::date).distinct().count() < 2) {
            return notMet(D, NOT_RECORDED, List.of());
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
     * Procedures done (stage {@code PERFORMED}, or unknown) in a model of Quadros 03/04; a request
     * proves nothing (§1.7) and a dental record ({@code MIAO}) is not accepted.
     */
    private Stream<CanonicalProcedureEvent> performed(String person, CboGroups cbo) {
        return of(procedures, person).stream()
                .filter(p -> p.stage() == null || PERFORMED.equalsIgnoreCase(p.stage()))
                .filter(p -> C5Event.acceptedOrigin(p.origin()))
                .filter(p -> cbo.matches(p.cbo()));
    }

    /** Measurements (MIP or MIAC, AMB-C5-06) of a model of Quadros 03/04 by the practice's CBO. */
    private Stream<CanonicalMeasurement> measured(String person, CboGroups cbo) {
        return of(measurements, person).stream()
                .filter(m -> C5Event.acceptedOrigin(m.origin()))
                .filter(m -> cbo.matches(m.cbo()));
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
        return notBlank(systolic) && notBlank(diastolic);
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
