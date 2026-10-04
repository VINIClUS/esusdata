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
    public static final String RULE_VERSION = ID + "@0.2.0";

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
            List.of(
                    "Dependência dos portões de C1–C7: a Nota Final só existe quando os resultados mensais dos sete"
                            + " indicadores do quadrimestre estão publicados e calculados; um mês bloqueado, ausente ou"
                            + " sem valor deixa a unidade sem nota, nunca zero nem peso redistribuído (MET-17).",
                    "AMB-CIII-01: os meses dos quadrimestres (jan–abr, mai–ago, set–dez) seguem a convenção da"
                            + " Tech Spec (MET-05); a NT 8/2026 não os define.",
                    "AMB-CIII-02/03/04: a média quadrimestral é a média aritmética simples dos meses, classificada sobre"
                            + " o valor exato, sem arredondar, pelas faixas da ficha de cada indicador, que prevalecem"
                            + " sobre os exemplos do Quadro 1.",
                    "AMB-CIII-05: \"A\" do Quadro 2 é lido como o fator do conceito, pelo exemplo do item 4.3.2,"
                            + " não pelo rótulo da coluna.",
                    "AMB-CIII-06/07: indicador sem mês elegível (C2/C3) ou com mês monitorado sem denominador (C1,"
                            + " C4–C7) fica indisponível e a unidade fica sem Nota Final.",
                    "AMB-CIII-08: a suspensão de pagamento (item 4.1.1) não é aplicada; os meses válidos para"
                            + " pagamento são informação externa ao PEC.",
                    "AMB-CIII-09/10: a classificação financeira da Portaria GM/MS nº 10.994/2026 lê"
                            + " \"quadrimestre\" como o quadrimestre avaliado; o regime de Q3/2026 é derivado.",
                    "AMB-CIII-12: o resultado mensal local não reproduz o prazo de envio ao Siaps nem a extração no"
                            + " 20º dia útil e pode divergir do Siaps.",
                    "AMB-CIII-13: equipes novas (item 2.6 e § 7º da Portaria) não são tratadas: a contagem do"
                            + " \"segundo recálculo\" não está definida.",
                    "Pesos do Quadro 2 (eSF/eAP) aplicados a toda equipe: o tipo de equipe não está na fonte (lacuna L1 de"
                            + " docs/discovery/2026-10-02-dw-dicionario-c2-c7.md);"
                            + " eSB e eMulti têm quadros próprios, fora do escopo."),
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
        return new Nt08Consolidation();
    }

    private static ComponentSpec indicator(String pack, String label, long weight) {
        return new ComponentSpec(pack, label, ComponentKind.INDICATOR, BigInteger.valueOf(weight), "quadrimestre");
    }
}
