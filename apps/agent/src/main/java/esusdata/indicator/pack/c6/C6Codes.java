package esusdata.indicator.pack.c6;

import esusdata.indicator.model.CboGroups;
import java.util.List;

/**
 * Code tables of the C6 ficha (SEI nº 0056053813, junho de 2026; transcrição em {@code
 * docs/metodologia/c6-cuidado-pessoa-idosa.md}), literal and versioned with {@link C6Pack#RULE_VERSION}.
 * SIGTAP goes digits only; CBO goes as the ficha writes it and is compared by {@link CboGroups}
 * (four characters = family, six = occupation).
 */
final class C6Codes {

    /** Item 5 (p. 1): «idade ≥ 60 anos de vida»; item 14 (p. 1) and items 4.1/4.3 (p. 4). */
    static final int MINIMUM_AGE_YEARS = 60;

    /** «nos últimos 12 meses» — Quadro 01 (p. 4), every practice. */
    static final int WINDOW_MONTHS = 12;

    /** Prática C: «intervalo mínimo de 30 (trinta) dias entre as visitas» — item 16 (p. 2), Quadro 01 (p. 4). */
    static final int MINIMUM_VISIT_INTERVAL_DAYS = 30;

    /** Prática A, Quadro 02 (p. 4): médicos {@code 2251, 2252, 2253, 2231} e enfermeiros {@code 2235}. */
    static final CboGroups CONSULTATION_CBO = CboGroups.of("2251", "2252", "2253", "2231", "2235");

    /**
     * Prática B, Quadro 03 (p. 4–5): médicos, enfermeiros, {@code 3222}, {@code 5151-05}, {@code
     * 2232}, {@code 2234}, {@code 2236}, {@code 2238}, {@code 2237}, {@code 2241} e {@code 2239}
     * (o Quadro 03 prevalece sobre o item 24 d, AMB-C6-11).
     */
    static final CboGroups ANTHROPOMETRY_CBO = CboGroups.of(
            "2251", "2252", "2253", "2231", "2235", "3222", "5151-05", "2232", "2234", "2236", "2238", "2237", "2241",
            "2239");

    /** Prática C, Quadro 04 (p. 5): {@code 3222-55} TACS e {@code 5151-05} ACS. */
    static final CboGroups HOME_VISIT_CBO = CboGroups.of("3222-55", "5151-05");

    /** Quadro 03 (p. 5) e item 24 f (p. 3): «01.01.04.002-4 - Avaliação antropométrica». */
    static final String SIGTAP_ANTHROPOMETRIC_ASSESSMENT = "0101040024";

    /** Quadro 03 (p. 5) e item 24 f (p. 3): «01.01.04.008-3 - Medição de peso». */
    static final String SIGTAP_WEIGHT = "0101040083";

    /** Quadro 03 (p. 5) e item 24 f (p. 3): «01.01.04.007-5 - Medição de altura». */
    static final String SIGTAP_HEIGHT = "0101040075";

    /** The SIGTAP codes the pack binds to {@code procedure_performed} (prática B only; AMB-C6-06). */
    static final List<String> PROCEDURE_CODES = List.of(SIGTAP_ANTHROPOMETRIC_ASSESSMENT, SIGTAP_WEIGHT, SIGTAP_HEIGHT);

    /**
     * Prática D, Quadro 05 (p. 5) e item 24 g (p. 3): «33 – Vacina influenza trivalente» e «77 -
     * Vacina influenza tetravalente».
     */
    static final List<String> INFLUENZA_CODES = List.of("33", "77");

    /** Item 24 b (p. 2): «equipes de Saúde da Família (eSF), […] tipo 70». */
    static final String TEAM_TYPE_ESF = "70";

    /** Item 24 b (p. 2): «equipes de Atenção Primária (eAP), tipo […] 76». */
    static final String TEAM_TYPE_EAP = "76";

    /**
     * Item 15 (p. 1): «“Saída do cidadão do cadastro” com a opção “Mudança de território”». The code
     * is not in the ficha: LEDI {@code MotivoSaida} 136 (dicionário do DW, {@code
     * tb_dim_tipo_saida_cadastro}), a convention to confirm in the live validation.
     */
    static final String EXIT_CHANGE_OF_TERRITORY = "136";

    /** Item 15 (p. 1): «Óbito no CadSUS»; locally only LEDI {@code MotivoSaida} 135 and {@code dt_obito}. */
    static final String EXIT_DEATH = "135";

    private C6Codes() {}
}
