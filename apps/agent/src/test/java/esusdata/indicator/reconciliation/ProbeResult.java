package esusdata.indicator.reconciliation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * What a {@link MethodologyProbe} found on the data of one reference (spec §9.4): how much of what
 * it asks it could observe, how many subjects the other reading applies to differently
 * ({@code affected}), and how many have their decision, NM, DN, score or class changed by it
 * ({@code divergent}). A subject is a person, an episode or a team: the probe says which in its
 * reason or detail.
 *
 * <p>An unobservable probe is never a zero. The counts are {@link OptionalInt}s and a {@link
 * Observability#NONE} result has none, so nothing downstream can read "could not look" as "looked
 * and found nothing": {@link #complete} is the only way to say zero. A {@link Observability#PARTIAL}
 * result has both counts as lower bounds. Both of them need a {@code reason}, which names what could
 * not be observed and, as it is versioned as written, never how many subjects or which.
 *
 * <p>What may leave the machine is {@link #versionedForm()} (and {@link #maskedSummary()}, which says
 * the same in a line): counts below 10 are {@code <10}. The {@code localDetail} may hold one line
 * per team or subject, INEs included, stays on the local disk, and appears in neither of them nor in
 * {@link #toString()}. A writer of versioned evidence must use {@link #versionedForm()} and nothing
 * else of this record: serializing the record itself would write the detail.
 *
 * @param probeId the probe that produced it ({@link MethodologyProbe#id()})
 * @param observability how much of the data the probe could observe
 * @param affected subjects the other reading applies to differently; absent exactly when {@code NONE}
 * @param divergent subjects or teams whose result changes under the other reading; absent exactly
 *     when {@code NONE}. Both counts are lower bounds when {@code PARTIAL}
 * @param reason why the probe could not observe everything; required unless {@code COMPLETE}, and
 *     empty when there is nothing to say
 * @param localDetail local-only lines, possibly per team; never versioned
 */
public record ProbeResult(
        String probeId,
        Observability observability,
        OptionalInt affected,
        OptionalInt divergent,
        String reason,
        List<String> localDetail) {

    private static final String PROBE_ID = "probe_id";
    private static final String AFFECTED = "affected";
    private static final String DIVERGENT = "divergent";

    public ProbeResult {
        if (probeId == null || !MethodologyProfile.STABLE_ID.matcher(probeId).matches()) {
            throw new IllegalArgumentException("probe id must look like c2.cohort.second-birthday, not " + probeId);
        }
        Objects.requireNonNull(observability, "observability");
        Objects.requireNonNull(affected, AFFECTED);
        Objects.requireNonNull(divergent, DIVERGENT);
        reason = reason == null ? "" : reason.strip();
        localDetail = List.copyOf(localDetail);
        requireCounts(probeId, observability, affected, divergent);
        if (observability != Observability.COMPLETE && reason.isEmpty()) {
            throw new IllegalArgumentException(
                    probeId + " is " + observability + " and needs a reason that says what could not be observed");
        }
    }

    /** Every subject was observable: {@code affected} and {@code divergent} are exact, and zero means zero. */
    public static ProbeResult complete(String probeId, int affected, int divergent, List<String> localDetail) {
        return new ProbeResult(
                probeId, Observability.COMPLETE, OptionalInt.of(affected), OptionalInt.of(divergent), "", localDetail);
    }

    /** Part of the subjects was observable: the counts are lower bounds and {@code reason} says what is missing. */
    public static ProbeResult partial(
            String probeId, int affected, int divergent, String reason, List<String> localDetail) {
        return new ProbeResult(
                probeId,
                Observability.PARTIAL,
                OptionalInt.of(affected),
                OptionalInt.of(divergent),
                reason,
                localDetail);
    }

    /** Nothing was observable: no counts, which is not zero counts, and {@code reason} says why. */
    public static ProbeResult none(String probeId, String reason) {
        return new ProbeResult(
                probeId, Observability.NONE, OptionalInt.empty(), OptionalInt.empty(), reason, List.of());
    }

    private static void requireCounts(
            String probeId, Observability observability, OptionalInt affected, OptionalInt divergent) {
        boolean counted = observability != Observability.NONE;
        if (affected.isPresent() != counted || divergent.isPresent() != counted) {
            throw new IllegalArgumentException(
                    counted
                            ? probeId + " is " + observability + " and counts both affected and divergent"
                            : probeId + " is NONE and has no counts: an unobservable probe is not one that found zero");
        }
        if (affected.stream().anyMatch(count -> count < 0) || divergent.stream().anyMatch(count -> count < 0)) {
            throw new IllegalArgumentException(probeId + " has a negative count");
        }
    }

    /**
     * The result in one line for a Markdown table: counts masked below 10, lower bounds marked as
     * such, and nothing said of a count that does not exist.
     */
    public String maskedSummary() {
        return switch (observability) {
            case COMPLETE -> AFFECTED + " " + masked(affected) + "; " + DIVERGENT + " " + masked(divergent);
            case PARTIAL ->
                "partial (lower bounds): " + AFFECTED + " " + masked(affected) + "; " + DIVERGENT + " "
                        + masked(divergent);
            case NONE -> "not observable";
        };
    }

    /**
     * The only form of this result that may be versioned, with keys in a fixed order: {@code
     * probe_id}, {@code observability}, then {@code affected} and {@code divergent} (masked, and
     * absent for {@code NONE}) and {@code reason} when there is one. The local detail is not in it.
     */
    public Map<String, String> versionedForm() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(PROBE_ID, probeId);
        form.put("observability", observability.name());
        affected.ifPresent(count -> form.put(AFFECTED, SummaryWriter.mask(count)));
        divergent.ifPresent(count -> form.put(DIVERGENT, SummaryWriter.mask(count)));
        if (!reason.isEmpty()) {
            form.put("reason", reason);
        }
        return Collections.unmodifiableMap(form);
    }

    private static String masked(OptionalInt count) {
        return SummaryWriter.mask(count.orElseThrow());
    }

    /** The versioned form only: a log line or an assertion message must not carry the local detail. */
    @Override
    public String toString() {
        return "ProbeResult" + versionedForm();
    }
}
