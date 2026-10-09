package esusdata.indicator.reconciliation;

import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.TeamTimeline;
import esusdata.indicator.model.TeamTimeline.Kind;
import esusdata.indicator.model.TeamTimeline.Resolution;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the type of the revision's teams was read on the last day of each month of a quadrimestre
 * (ADR 0034 section 5, step 3): the audited trail, or the team's current type standing in for a past
 * nobody recorded ({@code CURRENT_FALLBACK}). One count per team and month:
 *
 * <ul>
 *   <li>{@code audited}: the type came from the audit trail. If it is not the revision's, it is
 *       counted in {@code typeDisagreeing} too: the gate's fail-closed handles that, it is not a
 *       methodology condition;
 *   <li>{@code fallbackAgreeing} and {@code fallbackDisagreeing}: the type is the current one and
 *       it is, or is not, the revision's. Only the second one leaves the verdict inconclusive;
 *   <li>{@code unresolved}: no type on the day, or two contradictory ones.
 * </ul>
 *
 * <p>The versioned form masks every count like the rest of the dossier.
 *
 * @param teamMonths team-months looked at
 * @param audited team-months whose type came from the audit trail
 * @param fallbackAgreeing team-months read through the current type, equal to the revision's
 * @param fallbackDisagreeing team-months read through the current type, another than the revision's
 * @param unresolved team-months with no type, or a conflict, on the last day
 * @param typeDisagreeing audited team-months whose type is not the revision's
 */
public record TeamTypeCoverage(
        int teamMonths,
        int audited,
        int fallbackAgreeing,
        int fallbackDisagreeing,
        int unresolved,
        int typeDisagreeing) {

    private static final Map<String, String> CODE_OF_TYPE = Map.of(SiapsParser.ESF, "70", SiapsParser.EAP, "76");

    public TeamTypeCoverage {
        if (teamMonths < 0
                || audited < 0
                || fallbackAgreeing < 0
                || fallbackDisagreeing < 0
                || unresolved < 0
                || typeDisagreeing < 0) {
            throw new IllegalArgumentException("a coverage count is never negative");
        }
        if (audited + fallbackAgreeing + fallbackDisagreeing + unresolved != teamMonths) {
            throw new IllegalArgumentException(
                    "every team-month is audited, read through the fallback or unresolved, exactly once");
        }
        if (typeDisagreeing > audited) {
            throw new IllegalArgumentException("only an audited team-month can disagree on the type by itself");
        }
    }

    /**
     * Reads, for each team of the revision and each month of {@code inputs}, the type on the last day
     * of the month against the revision's type ({@code eSF} is 70, {@code eAP} is 76).
     *
     * @param inputs the monthly inputs of the quadrimestre
     * @param revisionTeams INE to {@code eSF} or {@code eAP}
     */
    public static TeamTypeCoverage of(List<PackInput> inputs, Map<String, String> revisionTeams) {
        Tally tally = new Tally();
        for (PackInput input : inputs) {
            TeamTimeline timeline = TeamTimeline.of(input.data().teams());
            YearMonth month = input.context().competencia();
            revisionTeams.forEach(
                    (ine, type) -> tally.add(timeline.typeOn(ine, month.atEndOfMonth()), CODE_OF_TYPE.get(type)));
        }
        return tally.coverage();
    }

    /** True when a team-month stands on a type that may not be the revision's, or on none. */
    public boolean leavesTypeOpen() {
        return fallbackDisagreeing > 0 || unresolved > 0;
    }

    /** The counts as the dossier shows them: masked below 10, keys in a fixed order. */
    public Map<String, String> versionedForm() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("team_months", SummaryWriter.mask(teamMonths));
        form.put("audited", SummaryWriter.mask(audited));
        form.put("fallback_agreeing", SummaryWriter.mask(fallbackAgreeing));
        form.put("fallback_disagreeing", SummaryWriter.mask(fallbackDisagreeing));
        form.put("unresolved", SummaryWriter.mask(unresolved));
        form.put("type_disagreeing", SummaryWriter.mask(typeDisagreeing));
        return Collections.unmodifiableMap(form);
    }

    /** Accumulates the team-months. */
    private static final class Tally {
        private int teamMonths;
        private int audited;
        private int fallbackAgreeing;
        private int fallbackDisagreeing;
        private int unresolved;
        private int typeDisagreeing;

        void add(Resolution resolution, String revisionCode) {
            teamMonths++;
            if (resolution.kind() != Kind.TYPE) {
                unresolved++;
                return;
            }
            boolean agrees = resolution.code().equals(revisionCode);
            if (CanonicalTeam.CURRENT_FALLBACK.equals(resolution.typeSource())) {
                if (agrees) {
                    fallbackAgreeing++;
                } else {
                    fallbackDisagreeing++;
                }
                return;
            }
            audited++;
            if (!agrees) {
                typeDisagreeing++;
            }
        }

        TeamTypeCoverage coverage() {
            return new TeamTypeCoverage(
                    teamMonths, audited, fallbackAgreeing, fallbackDisagreeing, unresolved, typeDisagreeing);
        }
    }
}
