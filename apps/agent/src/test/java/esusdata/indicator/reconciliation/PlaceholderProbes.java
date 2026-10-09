package esusdata.indicator.reconciliation;

import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.pack.c5.C5Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.util.List;

/**
 * The probes not implemented, with the structural probes that decide the verdict of their pack
 * (spec 2026-10-08 §9.4, ADR 0034 §5; user decision b'). They are registered in the {@link
 * MethodologyProbeCatalog} so that every probe a profile names resolves, and each answers {@code
 * NONE} saying it is not implemented, never that it could not look. The blockers are the only
 * values the consistency test accepts: the probes whose dimensions this installation cannot
 * observe completely in any quadrimestre.
 */
public final class PlaceholderProbes {

    private static final List<String> C2_BLOCKERS = List.of("c2.consult.puericultura-filter");
    private static final List<String> C3_BLOCKERS = List.of("c3.codes.pregnancy-puerperium", "c3.miac.counting-rule");
    private static final List<String> C4_BLOCKERS = List.of("c4.condition.code-list", "c4.condition.entry-history");
    private static final List<String> C5_BLOCKERS = List.of("c5.condition.entry-history");

    private PlaceholderProbes() {}

    /** Every placeholder, in the order of the profiles. */
    public static List<MethodologyProbe> all() {
        String notaFinal = ComponentIII.ID;
        return List.of(
                new PlaceholderProbe("c2.vaccine.transcription", C2Pack.ID, C2_BLOCKERS),
                new PlaceholderProbe("c2.cbo.weight-height", C2Pack.ID, C2_BLOCKERS),
                new PlaceholderProbe("c3.team.eap-credit", C3Pack.ID, C3_BLOCKERS),
                new PlaceholderProbe("c3.cbo.practices", C3Pack.ID, C3_BLOCKERS),
                new PlaceholderProbe("c3.exams.sigtap-additions", C3Pack.ID, C3_BLOCKERS),
                new PlaceholderProbe("c4.cbo.weight-height", C4Pack.ID, C4_BLOCKERS),
                new PlaceholderProbe("c5.cbo.weight-height", C5Pack.ID, C5_BLOCKERS),
                new PlaceholderProbe("ciii.monthly-rule-versions", notaFinal, List.of()),
                new PlaceholderProbe("ciii.team-universe", notaFinal, List.of()),
                new PlaceholderProbe("ciii.class-vs-payment", notaFinal, List.of()));
    }
}
