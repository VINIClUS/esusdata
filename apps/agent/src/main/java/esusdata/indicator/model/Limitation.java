package esusdata.indicator.model;

import java.util.Objects;

/**
 * One standing limitation of a pack (S2 of the gates-and-ambiguities effort): a stable code, the
 * final disclosure text of its decision record, and what kind of limitation it is. Only {@link
 * Kind#BLOCKING_GAP} keeps a result from being released (Portão B); the other two travel with every
 * result as disclosure.
 *
 * <p>{@code text} does not repeat the code. {@link #display()} is the one published string, {@code
 * "C4-LIM-01: ..."}, so what a result's {@code limitations} lists still shows the code.
 *
 * @param code stable code, {@code Cx-LIM-nn}
 * @param text the disclosure text, without the code prefix
 * @param kind the class the decision record gives it
 */
public record Limitation(String code, String text, Kind kind) {

    /**
     * What a limitation is. {@code OUT_OF_REACH}: a local PEC cannot see it by construction (other
     * installations, CadSUS, SCNES, SIGTAP, the SIAPS calendar, or a field this installation's DW
     * does not have); never blocks. {@code DECLARED_CONVENTION}: a reading the decision record
     * fixed. {@code BLOCKING_GAP}: data the local PEC has and the pack does not read, or a rule of
     * the ficha it does not apply; the only kind that blocks.
     */
    public enum Kind {
        BLOCKING_GAP,
        DECLARED_CONVENTION,
        OUT_OF_REACH
    }

    public Limitation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
    }

    public static Limitation blockingGap(String code, String text) {
        return new Limitation(code, text, Kind.BLOCKING_GAP);
    }

    public static Limitation convention(String code, String text) {
        return new Limitation(code, text, Kind.DECLARED_CONVENTION);
    }

    public static Limitation outOfReach(String code, String text) {
        return new Limitation(code, text, Kind.OUT_OF_REACH);
    }

    public boolean blocks() {
        return kind == Kind.BLOCKING_GAP;
    }

    /** The published string: the code, then the text. */
    public String display() {
        return code + ": " + text;
    }
}
