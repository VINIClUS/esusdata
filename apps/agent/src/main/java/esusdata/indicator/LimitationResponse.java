package esusdata.indicator;

import esusdata.indicator.model.Limitation;
import java.util.List;

/**
 * One standing limitation as the API shows it (S2): {@code kind} is {@code BLOCKING_GAP} (the only
 * one that keeps a result from being released), {@code DECLARED_CONVENTION} or {@code
 * OUT_OF_REACH}. {@code text} does not repeat the code; the plain {@code standingLimitations}
 * strings of the same response do.
 */
public record LimitationResponse(String code, String kind, String text) {

    public static LimitationResponse of(Limitation limitation) {
        return new LimitationResponse(limitation.code(), limitation.kind().name(), limitation.text());
    }

    public static List<LimitationResponse> of(List<Limitation> limitations) {
        return limitations.stream().map(LimitationResponse::of).toList();
    }
}
