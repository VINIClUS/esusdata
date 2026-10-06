package esusdata.indicator.sensitivity;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sensitivity of one pack to the readings of its ambiguities, over a real canonical dataset.
 * Implementations evaluate the pack the way production does (ungated: only counts and evidence are
 * read, never the published value) and return aggregates only.
 */
public interface PackSensitivity {

    /** The pack's result and the rows of its readings for one competência. */
    PackReport run(CanonicalDataset data, EvaluationContext context);

    /** What the harness reports about one pack. */
    record PackReport(String pack, String status, List<ReadingRow> rows, List<String> notes) {
        public PackReport {
            rows = List.copyOf(rows);
            notes = List.copyOf(notes);
        }
    }

    /** The points each practice or subgroup of the pack is worth, by code. */
    static Map<String, BigInteger> weights(PackDescriptor descriptor) {
        Map<String, BigInteger> weights = new HashMap<>();
        descriptor.components().forEach(spec -> weights.put(spec.code(), spec.weight()));
        return weights;
    }

    /** The result's own status, as text. */
    static String status(RuleOutcome outcome) {
        return outcome.result().status().name();
    }

    /** True when the pack could not read the extract at all (no subjects, no counts). */
    static boolean unsupported(RuleOutcome outcome) {
        return outcome.result().status() == IndicatorStatus.UNSUPPORTED_SOURCE;
    }

    /**
     * The ambiguity codes the result and each team result mention beyond the pack's standing
     * limitations — what fired for this data — one row per code and unit.
     */
    static List<ReadingRow> limitationRows(String pack, PackDescriptor descriptor, RuleOutcome outcome) {
        List<ReadingRow> rows = new ArrayList<>();
        rows.addAll(limitationRows(
                pack, descriptor, ReadingRow.MUNICIPALITY, outcome.result().limitations()));
        for (TeamResult team : outcome.teams()) {
            String unit = team.ine() == null ? "(sem INE)" : team.ine();
            rows.addAll(limitationRows(pack, descriptor, unit, team.result().limitations()));
        }
        return rows;
    }

    private static List<ReadingRow> limitationRows(
            String pack, PackDescriptor descriptor, String unit, List<String> limitations) {
        return limitations.stream()
                .filter(text -> !descriptor.standingLimitations().contains(text))
                .flatMap(text -> EvidenceSubjects.codesIn(text).stream())
                .distinct()
                .map(code ->
                        new ReadingRow(pack, code, ReadingRow.LIMITATION, unit, null, null, null, null, null, null))
                .toList();
    }
}
