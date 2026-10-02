package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.util.List;

/**
 * The Nota Final do Componente III (Tech Spec §2.4 "Consolidação quadrimestral", NT 8/2026; ficha
 * transcrita em {@code docs/metodologia/componente-iii-nt08-2026.md}). Not a rule over source
 * records: it reads the published monthly results of C1–C7 of one quadrimestre and is computed on
 * read, never acquired or enqueued (ADR 0030). Listed in the catalog so screens can show it.
 */
public final class ComponentIII {

    public static final String ID = "componente-iii-nota-final";
    public static final String RULE_VERSION = ID + "@0.1.0";

    /** NT 8/2026: weights 1/2/2/1/1/1/2 over C1–C7 for eSF/eAP. */
    public static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "cofin-quad-nt08-2026",
            "QUALIDADE_ESF_EAP",
            "Componente III",
            "Nota Final do Componente III (qualidade)",
            ValueKind.FINAL_SCORE,
            "pontos (0 a 10)",
            null,
            "componente-iii-exact-score@1",
            List.of(),
            List.of(
                    indicator("c1-mais-acesso", "C1 — Mais acesso", 1),
                    indicator("c2-desenvolvimento-infantil", "C2 — Cuidado no desenvolvimento infantil", 2),
                    indicator("c3-gestacao-puerperio", "C3 — Cuidado na gestação e puerpério", 2),
                    indicator("c4-cuidado-diabetes", "C4 — Cuidado da pessoa com diabetes", 1),
                    indicator("c5-cuidado-hipertensao", "C5 — Cuidado da pessoa com hipertensão", 1),
                    indicator("c6-cuidado-pessoa-idosa", "C6 — Cuidado da pessoa idosa", 1),
                    indicator("c7-prevencao-cancer", "C7 — Cuidado da mulher na prevenção do câncer", 2)),
            ReleaseGates.noneComplete(),
            List.of("Consolidação em implementação (ADR 0030): a nota ainda não é calculada."),
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://sisaps.saude.gov.br/sistemas/siaps/assets/files/NT_08-2025_cvat-8638ee08a7310014262c2326c234d35a.pdf",
                    "docs/metodologia/componente-iii-nt08-2026.md"),
            List.of(
                    "c1-mais-acesso",
                    "c2-desenvolvimento-infantil",
                    "c3-gestacao-puerperio",
                    "c4-cuidado-diabetes",
                    "c5-cuidado-hipertensao",
                    "c6-cuidado-pessoa-idosa",
                    "c7-prevencao-cancer"));

    private ComponentIII() {}

    /**
     * The consolidation this release computes the Nota Final with — the one seam the service calls,
     * so finishing the consolidation never touches the service.
     */
    public static ComponentIIIConsolidation consolidation() {
        return new PendingComponentIII();
    }

    private static ComponentSpec indicator(String pack, String label, long weight) {
        return new ComponentSpec(pack, label, ComponentKind.INDICATOR, BigInteger.valueOf(weight), "quadrimestre");
    }
}
