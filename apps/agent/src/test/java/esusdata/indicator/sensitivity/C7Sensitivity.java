package esusdata.indicator.sensitivity;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c7.C7Codes;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.pack.c7.C7Rule;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Sensitivity of C7 to AMB-C7-05, AMB-C7-06 and AMB-C7-08, from the ungated {@link C7Rule#compute}.
 *
 * <p>AMB-C7-06 (subgroup B) and AMB-C7-08 (subgroup A): a person's practice is ambiguous exactly
 * when no record counts for certain and some record counts under one reading, so "conta" decides
 * it as met and "não conta" as not met — promoted from the evidence, nothing recomputed. AMB-C7-05
 * (trans men of 9–14 in B) leaves such persons out of the denominator; the "incluir" reading
 * re-tags them as female before the rule runs ({@code FEMININO}, no gender identity, every row of
 * the person's key) and so needs their practice evaluated for real. Only {@code hpvVaccine} reads
 * the trans-man mark, so only subgroup B moves.
 */
public final class C7Sensitivity implements PackSensitivity {

    private static final String A = "A";
    private static final String B = "B";
    private static final String CODE_05 = "AMB-C7-05";
    private static final String CODE_06 = "AMB-C7-06";
    private static final String CODE_08 = "AMB-C7-08";
    private static final String CODE_B = CODE_05 + "+" + CODE_06;
    private static final String CODE_ALL = "AMB-C7-05+06+08";
    private static final int PERCENT = 100;
    private static final int SCALE = 4;

    /** One subgroup's people in one unit: the decided ones and those the ficha leaves open. */
    static final class Subgroup {
        long met;
        long notMet;
        long openPractice;
        long openDenominator;

        long denominator() {
            return met + notMet + openPractice;
        }

        long numerator(boolean practiceCounts) {
            return practiceCounts ? met + openPractice : met;
        }
    }

    @Override
    public PackReport run(CanonicalDataset data, EvaluationContext context) {
        PackDescriptor descriptor = new C7Pack().descriptor();
        RuleOutcome baseline = C7Rule.compute(data, context);
        if (PackSensitivity.unsupported(baseline)) {
            return new PackReport(
                    C7Pack.ID, PackSensitivity.status(baseline), List.of(), List.of("extrato sem a capacidade pedida"));
        }
        Map<String, Map<String, Subgroup>> base = counts(baseline);
        requireBaselineEqualsProduction(baseline, base);
        Map<String, Map<String, Subgroup>> included = base;
        List<CanonicalPerson> retagged = retagTransMen(data.persons());
        if (!retagged.equals(data.persons())) {
            included = counts(C7Rule.compute(Datasets.replacingPersons(data, retagged), context));
        }
        List<ReadingRow> rows = new ArrayList<>();
        for (String unit : base.keySet()) {
            rows.addAll(unitRows(unit, base, included, descriptor));
        }
        rows.addAll(PackSensitivity.limitationRows(C7Pack.ID, descriptor, baseline));
        return new PackReport(C7Pack.ID, PackSensitivity.status(baseline), rows, List.of());
    }

    private static List<ReadingRow> unitRows(
            String unit,
            Map<String, Map<String, Subgroup>> base,
            Map<String, Map<String, Subgroup>> included,
            PackDescriptor descriptor) {
        Map<String, Subgroup> cells = base.get(unit);
        Map<String, Subgroup> includedCells = included.getOrDefault(unit, cells);
        Subgroup a = cells.getOrDefault(A, new Subgroup());
        Subgroup b = cells.getOrDefault(B, new Subgroup());
        List<ReadingRow> rows = new ArrayList<>();
        addFrequency(rows, CODE_05, unit, b.openDenominator);
        addFrequency(rows, CODE_06, unit, b.openPractice);
        addFrequency(rows, CODE_08, unit, a.openPractice);
        rows.addAll(subgroupRows(unit, a, b, includedCells.getOrDefault(B, new Subgroup())));
        rows.addAll(combinedRows(unit, cells, includedCells.getOrDefault(B, new Subgroup()), descriptor));
        return rows;
    }

    /** Subgroup A under each reading of AMB-C7-08 and B under the four of AMB-C7-05 × AMB-C7-06. */
    private static List<ReadingRow> subgroupRows(String unit, Subgroup a, Subgroup b, Subgroup includedB) {
        List<ReadingRow> rows = new ArrayList<>();
        for (boolean counts08 : new boolean[] {true, false}) {
            rows.add(componentRow(
                    CODE_08, label08(counts08), unit, A, a.openPractice, a.numerator(counts08), a.denominator()));
        }
        for (boolean include05 : new boolean[] {false, true}) {
            Subgroup source = include05 ? includedB : b;
            for (boolean counts06 : new boolean[] {true, false}) {
                rows.add(componentRow(
                        CODE_B,
                        label05(include05) + " · " + label06(counts06),
                        unit,
                        B,
                        b.openDenominator + b.openPractice,
                        source.numerator(counts06),
                        source.denominator()));
            }
        }
        return rows;
    }

    /** The weighted score under each of the eight combinations of the three readings. */
    private static List<ReadingRow> combinedRows(
            String unit, Map<String, Subgroup> cells, Subgroup includedB, PackDescriptor descriptor) {
        Subgroup a = cells.getOrDefault(A, new Subgroup());
        Subgroup b = cells.getOrDefault(B, new Subgroup());
        List<ReadingRow> rows = new ArrayList<>();
        for (boolean include05 : new boolean[] {false, true}) {
            Map<String, Subgroup> scored = new HashMap<>(cells);
            scored.put(B, include05 ? includedB : b);
            for (boolean counts06 : new boolean[] {true, false}) {
                for (boolean counts08 : new boolean[] {true, false}) {
                    rows.add(new ReadingRow(
                            C7Pack.ID,
                            CODE_ALL,
                            label05(include05) + " · " + label06(counts06) + " · " + label08(counts08),
                            unit,
                            null,
                            a.openPractice + b.openDenominator + b.openPractice,
                            null,
                            BigInteger.valueOf(smallestSubgroup(scored, descriptor)),
                            score(scored, descriptor, counts06, counts08),
                            null));
                }
            }
        }
        return rows;
    }

    private static void addFrequency(List<ReadingRow> rows, String code, String unit, long affected) {
        if (affected > 0) {
            rows.add(new ReadingRow(
                    C7Pack.ID, code, ReadingRow.FREQUENCY, unit, null, affected, null, null, null, null));
        }
    }

    private static ReadingRow componentRow(
            String code,
            String reading,
            String unit,
            String component,
            long affected,
            long numerator,
            long denominator) {
        return new ReadingRow(
                C7Pack.ID,
                code,
                reading,
                unit,
                component,
                affected,
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator),
                percent(numerator, denominator),
                0L);
    }

    private static String percent(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return new ExactRatio(BigInteger.valueOf(numerator * PERCENT), BigInteger.valueOf(denominator))
                .toScaledBigDecimal(SCALE)
                .toPlainString();
    }

    /** The people of the smallest subgroup: what the combined score is masked by. */
    private static long smallestSubgroup(Map<String, Subgroup> cells, PackDescriptor descriptor) {
        return descriptor.components().stream()
                .mapToLong(
                        spec -> cells.getOrDefault(spec.code(), new Subgroup()).denominator())
                .min()
                .orElse(0);
    }

    /** The weighted score of the four subgroups; {@code null} when one has no denominator (P10). */
    private static String score(
            Map<String, Subgroup> cells, PackDescriptor descriptor, boolean counts06, boolean counts08) {
        ExactRatio total = ExactRatio.zero();
        for (ComponentSpec spec : descriptor.components()) {
            Subgroup cell = cells.getOrDefault(spec.code(), new Subgroup());
            boolean counts = switch (spec.code()) {
                case A -> counts08;
                case B -> counts06;
                default -> true;
            };
            long denominator = cell.denominator();
            if (denominator == 0) {
                return null;
            }
            total = total.plus(
                    new ExactRatio(BigInteger.valueOf(cell.numerator(counts)), BigInteger.valueOf(denominator))
                            .times(spec.weight()));
        }
        return total.toScaledBigDecimal(SCALE).toPlainString();
    }

    private static String label05(boolean include) {
        return include ? "05: homem trans 9–14 incluído em B" : "05: homem trans 9–14 fora de B";
    }

    private static String label06(boolean counts) {
        return counts ? "06: dose fora de 60 meses conta" : "06: dose fora de 60 meses não conta";
    }

    private static String label08(boolean counts) {
        return counts ? "08: 02.02.10.025-1 antes de 2026-01 conta" : "08: 02.02.10.025-1 antes de 2026-01 não conta";
    }

    /** Per unit (the municipality, then each INE) and subgroup, the people by decision. */
    static Map<String, Map<String, Subgroup>> counts(RuleOutcome outcome) {
        Map<String, String> ineOf = new HashMap<>();
        for (EvidenceItem row : outcome.evidence()) {
            if (row.decision() == EvidenceDecision.ELIGIBLE && row.component() == null) {
                ineOf.put(row.subjectKey(), row.ine());
            }
        }
        Map<String, Map<String, Subgroup>> units = new LinkedHashMap<>();
        units.put(ReadingRow.MUNICIPALITY, new TreeMap<>());
        for (EvidenceItem row : outcome.evidence()) {
            if (row.component() == null || !ineOf.containsKey(row.subjectKey())) {
                continue;
            }
            String ine = ineOf.get(row.subjectKey());
            for (String unit : new String[] {ReadingRow.MUNICIPALITY, ine}) {
                if (unit != null) {
                    add(
                            units.computeIfAbsent(unit, k -> new TreeMap<>())
                                    .computeIfAbsent(row.component(), k -> new Subgroup()),
                            row);
                }
            }
        }
        return units;
    }

    private static void add(Subgroup cell, EvidenceItem row) {
        switch (row.decision()) {
            case PRACTICE_MET -> cell.met++;
            case PRACTICE_NOT_MET -> cell.notMet++;
            case PRACTICE_AMBIGUOUS -> {
                if (row.reasonCode() != null && row.reasonCode().startsWith("AMB_C7_05")) {
                    cell.openDenominator++;
                } else {
                    cell.openPractice++;
                }
            }
            default -> {
                // support rows carry no decision of their own
            }
        }
    }

    /** Re-tags every row of each trans man (sex M, identity 149) as female without identity. */
    static List<CanonicalPerson> retagTransMen(List<CanonicalPerson> persons) {
        Set<String> transMen = new HashSet<>();
        for (CanonicalPerson person : persons) {
            if (C7Codes.SEXO_MASCULINO.equals(person.sex())
                    && C7Codes.IDENTIDADE_HOMEM_TRANSGENERO.equals(person.genderIdentity())) {
                transMen.add(person.personKey());
            }
        }
        List<CanonicalPerson> retagged = new ArrayList<>(persons.size());
        for (CanonicalPerson person : persons) {
            retagged.add(
                    transMen.contains(person.personKey())
                            ? new CanonicalPerson(
                                    person.sourceRef(),
                                    person.municipalityIbge(),
                                    person.personKey(),
                                    person.birthDate(),
                                    C7Codes.SEXO_FEMININO,
                                    null,
                                    person.deathDate())
                            : person);
        }
        return retagged;
    }

    /** The harness must reproduce production before it is trusted on any reading. */
    private static void requireBaselineEqualsProduction(
            RuleOutcome outcome, Map<String, Map<String, Subgroup>> counts) {
        requireSame(ReadingRow.MUNICIPALITY, outcome.result().components(), counts);
        for (TeamResult team : outcome.teams()) {
            requireSame(team.ine(), team.result().components(), counts);
        }
    }

    private static void requireSame(
            String unit, List<ResultComponent> components, Map<String, Map<String, Subgroup>> counts) {
        for (ResultComponent component : components) {
            Subgroup cell = counts.getOrDefault(unit, Map.of()).getOrDefault(component.code(), new Subgroup());
            if (component.numerator().longValueExact() != cell.numerator(false)
                    || component.denominator().longValueExact() != cell.denominator()) {
                throw new IllegalStateException(
                        "C7 baseline: subgroup " + component.code() + " differs from production");
            }
        }
    }
}
