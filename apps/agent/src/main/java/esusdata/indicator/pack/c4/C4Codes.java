package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CboGroups;
import esusdata.indicator.pack.PackSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Code tables of the C4 ficha («NOTA METODOLÓGICA C4 - CUIDADO DA PESSOA COM DIABETES», SEI
 * 0055986848; transcription in {@code docs/metodologia/c4-cuidado-diabetes.md}), versioned with the
 * pack. SIGTAP goes as digits only; CBO as the ficha writes it (four characters = family, with a
 * hyphen = occupation, AMB-C4-08). Nothing here is completed from memory: lists the ficha does not
 * enumerate stay out and become a limitation.
 */
public final class C4Codes {

    /** Médicos «2231 / 2251 / 2252 / 2253» and enfermeiros «2235» (item 24 c, p. 2). */
    private static final String MEDICO_2251 = "2251";

    private static final String MEDICO_2252 = "2252";
    private static final String MEDICO_2253 = "2253";
    private static final String MEDICO_2231 = "2231";
    private static final String ENFERMEIRO = "2235";

    /** «em pelo menos uma ocasião desde 2013» (itens 5 e 14, p. 1). */
    public static final LocalDate EVALUATED_SINCE = LocalDate.of(2013, 1, 1);

    /** CIAP-2 «T89; T90» (item 24 f, p. 3). */
    public static final List<String> CIAP = List.of("T89", "T90");

    /**
     * CID-10 «E10; E11; E14» (item 24 f, p. 3), categories «contemplando seus respectivos
     * subcódigos» (nota de rodapé 3, p. 7): matched by the three-character category.
     */
    public static final List<String> CID_CATEGORIES = List.of("E10", "E11", "E14");

    /** {@code 03.01.10.003-9} «Aferição da pressão arterial.» (Quadro 03, p. 5). */
    public static final String BLOOD_PRESSURE = "0301100039";

    /** {@code 01.01.04.002-4} «Avaliação antropométrica.» (Quadro 04, p. 5). */
    public static final String ANTHROPOMETRY = PackSupport.SIGTAP_ANTHROPOMETRY;

    /** {@code 01.01.04.008-3} «Medição de peso.» (Quadro 04, p. 5). */
    public static final String WEIGHT = PackSupport.SIGTAP_WEIGHT;

    /** {@code 01.01.04.007-5} «Medição de altura.» (Quadro 04, p. 5). */
    public static final String HEIGHT = PackSupport.SIGTAP_HEIGHT;

    /** {@code 02.02.01.050-3} «Dosagem de hemoglobina glicosilada.» (Quadro 06, p. 6). */
    public static final String HBA1C = "0202010503";

    /** {@code ABEX008} «Hemoglobina glicosilada.» (Quadro 06, p. 6; item 24 g, p. 3). */
    public static final String HBA1C_ABEX = "ABEX008";

    /** {@code 03.01.04.009-5} «Exame do pé diabético.» (Quadro 07, p. 6). */
    public static final String DIABETIC_FOOT = "0301040095";

    /** SIGTAP codes bound to {@code procedure_performed} (Quadros 03, 04, 06 e 07). */
    public static final List<String> PROCEDURE_CODES =
            List.of(BLOOD_PRESSURE, ANTHROPOMETRY, WEIGHT, HEIGHT, HBA1C, HBA1C_ABEX, DIABETIC_FOOT);

    /** Practice C's SIGTAP codes (Quadro 04, p. 5). */
    public static final List<String> ANTHROPOMETRY_CODES = List.of(ANTHROPOMETRY, WEIGHT, HEIGHT);

    /** Exams bound to {@code exam_request_evaluation}: «solicitada ou avaliada» (Quadro 06, p. 6). */
    public static final List<String> EXAM_CODES = List.of(HBA1C, HBA1C_ABEX);

    /** Prática A — Quadro 02 (p. 4) and item 24 c (p. 2): médicos e enfermeiros. */
    public static final CboGroups CBO_A = PackSupport.CONSULTATION_CBO;

    /**
     * Who evaluates the condition: «realizada por enfermeira(o) e/ou médica(o) da APS» (item 5, p. 1),
     * the CBO of item 24 c (p. 2).
     */
    public static final CboGroups CBO_CONDITION = CBO_A;

    /** Prática B — Quadro 03 (p. 4–5); without {@code 5151-05} (nota de rodapé 4, p. 7). */
    public static final CboGroups CBO_B = CboGroups.of(
            MEDICO_2251,
            MEDICO_2252,
            MEDICO_2253,
            MEDICO_2231,
            ENFERMEIRO,
            "3222",
            "2232",
            "2234",
            "2236",
            "2238",
            "2237",
            "2241",
            "2239",
            "3224");

    /** Prática C — Quadro 04 (p. 5–6); with {@code 5151-05}, without {@code 3224}. */
    public static final CboGroups CBO_C = CboGroups.of(
            MEDICO_2251,
            MEDICO_2252,
            MEDICO_2253,
            MEDICO_2231,
            ENFERMEIRO,
            "3222",
            "5151-05",
            "2232",
            "2234",
            "2236",
            "2238",
            "2237",
            "2241",
            "2239");

    /** Prática D — Quadro 05 (p. 6): only the occupations {@code 3222-55} (TACS) and {@code 5151-05} (ACS). */
    public static final CboGroups CBO_D = CboGroups.of("3222-55", "5151-05");

    /** Prática E — Quadro 06 (p. 6) as published, without {@code 2234} (AMB-C4-06). */
    public static final CboGroups CBO_E =
            CboGroups.of(MEDICO_2251, MEDICO_2252, MEDICO_2253, MEDICO_2231, ENFERMEIRO, "3222", "2232", "2237");

    /** Prática F — Quadro 07 (p. 6). */
    public static final CboGroups CBO_F =
            CboGroups.of(MEDICO_2251, MEDICO_2252, MEDICO_2253, MEDICO_2231, ENFERMEIRO, "2234", "2236", "2239");

    /** MIAC: «código 04, 05, 06 e 07, de forma específica ou compartilhada» (item 24 e, p. 3). */
    public static final List<Integer> COLLECTIVE_ACTIVITY_TYPES = List.of(4, 5, 6, 7);

    /** «intervalo mínimo de 30 (trinta) dias» (item 16, p. 2), counted as date difference (AMB-C4-03). */
    public static final long MIN_VISIT_INTERVAL_DAYS = 30;

    /** LEDI {@code SituacaoProblemasCondicoes}: 0 Ativo, 1 Latente, 2 Resolvido (DW {@code tb_dim_situacao_problema}). */
    public static final String RESOLVED_STATUS = "2";

    /** Every LEDI situação: 0 Ativo, 1 Latente, 2 Resolvido. */
    public static final List<String> CONDITION_STATUSES = List.of("0", "1", RESOLVED_STATUS);

    /** LEDI {@code MotivoSaida} 135 Óbito (DW {@code tb_dim_tipo_saida_cadastro}). */
    public static final String EXIT_DEATH = "135";

    /** LEDI {@code MotivoSaida} 136 Mudança de território (item 15, p. 2). */
    public static final String EXIT_TERRITORY_CHANGE = "136";

    /** «equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76» (item 24 b, p. 2). */
    public static final String ESF_TEAM_TYPE = "70";

    public static final String EAP_TEAM_TYPE = "76";

    /** The team types the ficha considers (item 24 b, p. 2), checked only when the source has the type. */
    public static final List<String> TEAM_TYPES = List.of(ESF_TEAM_TYPE, EAP_TEAM_TYPE);

    private static final Pattern SEPARATORS = Pattern.compile("[-.\\s]");

    /** Code systems as {@code condition_list} writes them (S-C4-02). */
    public static final String CODE_SYSTEM_CIAP = "CIAP2";

    public static final String CODE_SYSTEM_CID = "CID10";
    public static final String BASIS_PROFESSIONAL = "PROFESSIONAL";

    private C4Codes() {}

    /** True for {@code T89}/{@code T90} (CIAP-2) or any code of the categories E10, E11, E14 (CID-10). */
    public static boolean isEligibleCondition(String codeSystem, String code) {
        if (CODE_SYSTEM_CIAP.equals(codeSystem)) {
            return isEligibleCiap(code);
        }
        return CODE_SYSTEM_CID.equals(codeSystem) && isEligibleCid(code);
    }

    public static boolean isEligibleCiap(String code) {
        return code != null && CIAP.contains(normalized(code));
    }

    public static boolean isEligibleCid(String code) {
        if (code == null) {
            return false;
        }
        String cid = normalized(code);
        return cid.length() >= 3 && CID_CATEGORIES.contains(cid.substring(0, 3));
    }

    /** A code as compared here: upper case, without dots, hyphens or spaces. */
    static String normalized(String code) {
        return SEPARATORS.matcher(code).replaceAll("").toUpperCase(Locale.ROOT);
    }
}
