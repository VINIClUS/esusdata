package esusdata.indicator.reconciliation;

import esusdata.indicator.pack.c1.C1Rule;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.pack.c5.C5Pack;
import esusdata.indicator.pack.c6.C6Pack;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A pack the Portão D checks: its id, its code and its indicator code in the SIAPS. Which SIAPS
 * quadrimestre is compared is not a property of the pack: the references are pre-registered in the
 * policy by id ({@link ReferenceSet}), and no date selects or excludes one (ADR 0034).
 */
public record GatePack(String packId, String code, int siapsCode) {

    private static final List<GatePack> ALL = List.of(
            new GatePack(C1Rule.INDICATOR_PACK, "C1", 110),
            new GatePack(C2Pack.ID, "C2", 108),
            new GatePack(C3Pack.ID, "C3", 107),
            new GatePack(C4Pack.ID, "C4", 105),
            new GatePack(C5Pack.ID, "C5", 104),
            new GatePack(C6Pack.ID, "C6", 106),
            new GatePack(C7Pack.ID, "C7", 109));

    /** The SIAPS code the Nota Final's rows carry in a snapshot: it is not an indicator. */
    static final int NOTA_FINAL_CODE = 0;

    /**
     * The Nota Final do Componente III ({@code siaps-nota-final-por-classe@2}, docs/indicadores/
     * portoes/portao-d-nota-final-siaps.md): not one of {@link #all()}.
     */
    public static final GatePack NOTA_FINAL = new GatePack(ComponentIII.ID, "CIII", NOTA_FINAL_CODE);

    /** True for the Nota Final, whose rule, check and SIAPS rows differ from those of C1–C7. */
    public boolean isNotaFinal() {
        return siapsCode == NOTA_FINAL_CODE;
    }

    /** The id of the automated check that decides this pack's D: the one the policy declares for it. */
    public String checkId() {
        return ReferencePolicy.checkFor(packId);
    }

    /** The document that fixes this pack's D rule. */
    public String ruleDocument() {
        return "docs/indicadores/portoes/"
                + (isNotaFinal() ? "portao-d-nota-final-siaps.md" : "portao-d-conciliacao-siaps.md");
    }

    /** The seven packs and the Nota Final: everything the live check decides. */
    public static List<GatePack> allWithNotaFinal() {
        List<GatePack> everything = new ArrayList<>(ALL);
        everything.add(NOTA_FINAL);
        return List.copyOf(everything);
    }

    public static List<GatePack> all() {
        return ALL;
    }

    public static Optional<GatePack> bySiapsCode(int siapsCode) {
        return ALL.stream().filter(pack -> pack.siapsCode == siapsCode).findFirst();
    }

    public static Optional<GatePack> byPackId(String packId) {
        return ALL.stream().filter(pack -> pack.packId.equals(packId)).findFirst();
    }
}
