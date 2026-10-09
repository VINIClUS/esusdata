package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The local figures against the official ones, team by team, as a diagnostic (spec section 9.5):
 * how many teams were compared and how many differ in score, concept, final note and final class,
 * and the largest absolute score difference. It is recorded in the dossier and never read by the
 * decision table: a local calculation error must reach the gate as {@code FAILED}, not hide as
 * {@code INCONCLUSIVE}.
 *
 * <p>Only counts are versioned, masked below 10. The per-team lines, with the INE, are {@link
 * #localDetail()}: local disk only, and not in {@link #versionedForm()} or {@link #toString()}.
 *
 * @param teamsCompared teams with a local and an official side
 * @param scoreDiffers teams whose score differs
 * @param conceptDiffers teams whose concept differs
 * @param finalNoteDiffers teams whose final note differs
 * @param finalClassDiffers teams whose final class differs
 * @param maxAbsScoreDifference the largest absolute score difference, zero when none differs
 * @param localDetail one line per team that differs; never versioned
 */
public record OfficialFieldComparison(
        int teamsCompared,
        int scoreDiffers,
        int conceptDiffers,
        int finalNoteDiffers,
        int finalClassDiffers,
        BigDecimal maxAbsScoreDifference,
        List<String> localDetail) {

    /**
     * The fields of one side of a team. A field that the pack does not have (the Nota Final has no
     * score or concept, an indicator has no final note) is {@code null} on both sides and is not
     * compared.
     */
    public record Values(BigDecimal score, Classification concept, BigDecimal finalNote, Classification finalClass) {}

    /** One team, local against official. */
    public record TeamPair(String ine, Values local, Values official) {

        public TeamPair {
            Objects.requireNonNull(ine, "ine");
            Objects.requireNonNull(local, "local");
            Objects.requireNonNull(official, "official");
        }
    }

    public OfficialFieldComparison {
        Objects.requireNonNull(maxAbsScoreDifference, "maxAbsScoreDifference");
        localDetail = List.copyOf(localDetail);
        if (teamsCompared < 0
                || scoreDiffers < 0
                || conceptDiffers < 0
                || finalNoteDiffers < 0
                || finalClassDiffers < 0
                || maxAbsScoreDifference.signum() < 0) {
            throw new IllegalArgumentException("a comparison count or difference is never negative");
        }
    }

    /** Compares every pair; a field present on one side only counts as a difference. */
    public static OfficialFieldComparison of(List<TeamPair> pairs) {
        Tally tally = new Tally();
        pairs.forEach(tally::add);
        return tally.comparison(pairs.size());
    }

    /** The counts as the dossier shows them: masked below 10, the largest difference as a plain decimal. */
    public Map<String, String> versionedForm() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("teams_compared", SummaryWriter.mask(teamsCompared));
        form.put("score_differs", SummaryWriter.mask(scoreDiffers));
        form.put("concept_differs", SummaryWriter.mask(conceptDiffers));
        form.put("final_note_differs", SummaryWriter.mask(finalNoteDiffers));
        form.put("final_class_differs", SummaryWriter.mask(finalClassDiffers));
        form.put("max_abs_score_difference", ReferenceFormats.plain(maxAbsScoreDifference));
        return Collections.unmodifiableMap(form);
    }

    /** The versioned form only: the per-team lines carry INEs. */
    @Override
    public String toString() {
        return "OfficialFieldComparison" + versionedForm();
    }

    /** Accumulates the pairs. */
    private static final class Tally {
        private static final String VS = " vs ";
        private int score;
        private int concept;
        private int note;
        private int finalClass;
        private BigDecimal max = BigDecimal.ZERO;
        private final List<String> detail = new ArrayList<>();

        void add(TeamPair pair) {
            List<String> differing = new ArrayList<>();
            Values local = pair.local();
            Values official = pair.official();
            if (differs(local.score(), official.score())) {
                score++;
                differing.add("score " + local.score() + VS + official.score());
                if (local.score() != null && official.score() != null) {
                    max = max.max(local.score().subtract(official.score()).abs());
                }
            }
            if (!Objects.equals(local.concept(), official.concept())) {
                concept++;
                differing.add("concept " + local.concept() + VS + official.concept());
            }
            if (differs(local.finalNote(), official.finalNote())) {
                note++;
                differing.add("final note " + local.finalNote() + VS + official.finalNote());
            }
            if (!Objects.equals(local.finalClass(), official.finalClass())) {
                finalClass++;
                differing.add("final class " + local.finalClass() + VS + official.finalClass());
            }
            if (!differing.isEmpty()) {
                detail.add(pair.ine() + ": local vs official, " + String.join("; ", differing));
            }
        }

        private static boolean differs(BigDecimal local, BigDecimal official) {
            if (local == null || official == null) {
                return (local == null) != (official == null);
            }
            return local.compareTo(official) != 0;
        }

        OfficialFieldComparison comparison(int compared) {
            return new OfficialFieldComparison(compared, score, concept, note, finalClass, max, detail);
        }
    }
}
