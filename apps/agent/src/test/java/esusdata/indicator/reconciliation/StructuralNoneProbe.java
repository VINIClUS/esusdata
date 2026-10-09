package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * A real probe whose honest answer on this installation is {@link Observability#NONE}, because of
 * a structural cause and not because nobody wrote it (spec 2026-10-08 §9.4; contrast {@link
 * PlaceholderProbe}). It is for the dimensions whose other reading cannot be computed from any
 * dataset this installation can hold:
 *
 * <ul>
 *   <li>{@link Cause#NOT_SPECIFIABLE}: the other reading is the text the 2026 ficha revoked (the
 *       lists of codes of C3 and C4, the counting rule of the MIAC), which was not accessed (note
 *       of editions, R1). There is no alternative to compute;
 *   <li>{@link Cause#ENTRY_HISTORY}: the condition text of C4 and C5 says «desde 2013», and the
 *       other reading looks for the condition in individual encounters since then, which the pack
 *       reads for twelve months only. The probe looks at the window of the encounter extract: if
 *       it does not reach 2013 it says so; if it ever does, it says that no computation exists yet
 *       rather than answering zero.
 * </ul>
 *
 * <p>It never returns a count: an unobservable probe is not one that found zero.
 */
public final class StructuralNoneProbe implements MethodologyProbe {

    /** Why the answer is {@link Observability#NONE}. */
    public enum Cause {
        /** The other reading is a revoked text that was not accessed. */
        NOT_SPECIFIABLE,
        /** The other reading needs encounters older than the window the extract reads. */
        ENTRY_HISTORY
    }

    private static final LocalDate EVALUATED_SINCE = LocalDate.of(2013, 1, 1);

    private static final String NOT_A_PACK =
            "The other reading is computed on the source records of a pack, which the Nota Final context does not"
                    + " carry.";

    private static final String REVOKED_TEXT =
            "The other reading is the text of the revoked edition of the ficha, which was not accessed, so there is no"
                    + " alternative to compute (edicoes-oficiais-siaps.md, R1).";

    private static final String WINDOW_TOO_SHORT =
            "The other reading looks for the condition in individual encounters since 2013, and the extract of the"
                    + " pack reads the encounters of a shorter window, so they are not in the data.";

    private static final String WINDOW_REACHES_2013 =
            "The extract reaches 2013, but the computation of the other reading from the encounters is not"
                    + " implemented.";

    private final String id;
    private final String packId;
    private final Cause cause;

    /**
     * @param id the probe id the profile names
     * @param packId the pack it serves
     * @param cause why it is unobservable
     */
    public StructuralNoneProbe(String id, String packId, Cause cause) {
        this.id = id;
        this.packId = packId;
        this.cause = cause;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> packs() {
        return Set.of(packId);
    }

    @Override
    public ProbeResult evaluate(ProbeContext context) {
        return switch (context) {
            case NotaFinalProbeContext notaFinal -> ProbeResult.none(id, NOT_A_PACK);
            case PackProbeContext pack -> ProbeResult.none(id, reason(pack));
        };
    }

    private String reason(PackProbeContext context) {
        return switch (cause) {
            case NOT_SPECIFIABLE -> REVOKED_TEXT;
            case ENTRY_HISTORY -> reachesBack(context) ? WINDOW_REACHES_2013 : WINDOW_TOO_SHORT;
        };
    }

    /** Whether every month of the context read the encounters back to 2013. */
    private static boolean reachesBack(PackProbeContext context) {
        return context.inputs().stream().map(PackInput::data).allMatch(data -> {
            Optional<DateWindow> window = data.windowOf(Capabilities.CARE_ENCOUNTER);
            return window.isPresent() && !window.get().start().isAfter(EVALUATED_SINCE);
        });
    }
}
