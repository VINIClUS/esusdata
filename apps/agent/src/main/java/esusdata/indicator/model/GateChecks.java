package esusdata.indicator.model;

import java.time.LocalDate;
import java.util.List;

/**
 * The two gates evaluated on every result rather than recorded in the registry (ADR 0032): Portão
 * B from what the pack declares about itself, Portão C from what the source can serve. The
 * executor runs them; a pack never does.
 */
public final class GateChecks {

    public static final String CALCULATION_MODEL_CHECK = "sem-lacuna-bloqueante@1";
    public static final String ADAPTER_CHECK = "capacidades-validadas@1";

    private GateChecks() {}

    private static String iso(LocalDate on) {
        return on == null ? null : on.toString();
    }

    /**
     * Portão B passes when the pack declares no blocking limitation. A null {@code on} is a read
     * not tied to a run (the pack catalog). Today every standing limitation
     * blocks; the split into blocking and disclosed limitations lives in {@link
     * PackDescriptor#blockingLimitations()} alone.
     */
    public static GateCheck calculationModel(PackDescriptor descriptor, LocalDate on) {
        int blocking = descriptor.blockingLimitations().size();
        if (blocking == 0) {
            return new GateCheck(GateCheck.State.PASSED, CALCULATION_MODEL_CHECK, iso(on), List.of(), null);
        }
        return new GateCheck(
                GateCheck.State.FAILED,
                CALCULATION_MODEL_CHECK,
                iso(on),
                List.of(),
                blocking + " limitação(ões) permanente(s) bloqueante(s)");
    }

    /** Portão C passes when no capability the pack reads lacks a {@code VALIDATED} entry. */
    public static GateCheck adapter(List<String> missingCapabilities, LocalDate on) {
        if (missingCapabilities.isEmpty()) {
            return new GateCheck(GateCheck.State.PASSED, ADAPTER_CHECK, iso(on), List.of(), null);
        }
        return new GateCheck(
                GateCheck.State.FAILED,
                ADAPTER_CHECK,
                iso(on),
                List.of(),
                "capacidades sem validação: " + String.join(", ", missingCapabilities));
    }
}
