package esusdata.indicator.reconciliation;

import esusdata.indicator.pack.c1.C1Rule;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.pack.c5.C5Pack;
import esusdata.indicator.pack.c6.C6Pack;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A pack the Portão D checks: its id, its indicator code in the SIAPS and the latest SEI signature
 * date among its ficha and the NT 8/2026. The dates are the table of {@code
 * docs/indicadores/portoes/portao-d-conciliacao-siaps.md}, taken from the signature lines of
 * {@code docs/metodologia/fontes}; the NT 8/2026 was signed last on 2026-06-01, earlier than every
 * ficha, so the ficha's date is the floor of each pack.
 */
public record GatePack(String packId, String code, int siapsCode, LocalDate lastSignature) {

    private static final List<GatePack> ALL = List.of(
            new GatePack(C1Rule.INDICATOR_PACK, "C1", 110, LocalDate.of(2026, 6, 24)),
            new GatePack(C2Pack.ID, "C2", 108, LocalDate.of(2026, 6, 22)),
            new GatePack(C3Pack.ID, "C3", 107, LocalDate.of(2026, 6, 22)),
            new GatePack(C4Pack.ID, "C4", 105, LocalDate.of(2026, 6, 21)),
            new GatePack(C5Pack.ID, "C5", 104, LocalDate.of(2026, 6, 21)),
            new GatePack(C6Pack.ID, "C6", 106, LocalDate.of(2026, 6, 19)),
            new GatePack(C7Pack.ID, "C7", 109, LocalDate.of(2026, 6, 22)));

    /** The last of the six SEI signatures of the NT 8/2026. */
    static final LocalDate NT8_LAST_SIGNATURE = LocalDate.of(2026, 6, 1);

    /** The SIAPS code the Nota Final's rows carry in a snapshot: it is not an indicator. */
    static final int NOTA_FINAL_CODE = 0;

    /**
     * The Nota Final do Componente III ({@code siaps-nota-final-por-classe@1}, docs/indicadores/
     * portoes/portao-d-nota-final-siaps.md): not one of {@link #all()}. Its signature date is the
     * latest of the seven fichas (and the NT 8/2026), so its floor is the latest floor of C1–C7.
     */
    public static final GatePack NOTA_FINAL = new GatePack(
            ComponentIII.ID,
            "CIII",
            NOTA_FINAL_CODE,
            ALL.stream().map(GatePack::floor).max(LocalDate::compareTo).orElseThrow());

    /** True for the Nota Final, whose rule, check and SIAPS rows differ from those of C1–C7. */
    public boolean isNotaFinal() {
        return siapsCode == NOTA_FINAL_CODE;
    }

    /** The id of the automated check that decides this pack's D. */
    public String checkId() {
        return isNotaFinal() ? Comparison.CHECK_ID_NOTA_FINAL : Comparison.CHECK_ID;
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

    /** The latest signature date among the pack's ficha and the NT 8/2026. */
    public LocalDate floor() {
        return lastSignature.isAfter(NT8_LAST_SIGNATURE) ? lastSignature : NT8_LAST_SIGNATURE;
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
