package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CboGroups;
import java.util.List;

/**
 * Códigos da ficha C2 (Nota Metodológica C2, SEI 0054824593, edição de 19–22/06/2026), literais e
 * com a página de cada um; transcrição em {@code docs/metodologia/c2-desenvolvimento-infantil.md}.
 * SIGTAP vai só com dígitos; CBO como a ficha escreve, comparado por {@link CboGroups} (quatro
 * dígitos = família, seis = ocupação, AMB-C2-13). Os códigos de domínio da fonte (motivo e desfecho
 * da visita, saída do cadastro, local de atendimento) vêm do dicionário do DW/LEDI
 * ({@code docs/discovery/2026-10-02-dw-dicionario-c2-c7.md}), não da ficha, e ficam marcados assim.
 */
@SuppressWarnings("PMD.DataClass") // tabela de códigos congelados da ficha
public final class C2Codes {

    /** Versão destas tabelas: muda quando a ficha mudar. */
    public static final String VERSION = "c2-codes@2026-06";

    /** 24 c (p.2) e Quadro 02 (p.5): consultas de A e B — médicos e enfermeiros. */
    public static final List<String> CONSULT_CBO = List.of("2251", "2252", "2253", "2231", "2235");

    /** Quadro 03 (p.5): registros de peso e altura da prática C. */
    public static final List<String> ANTHROPOMETRY_CBO = List.of(
            "2251", "2252", "2253", "2231", "2235", "3222", "5151-05", "2232", "2234", "2236", "2238", "2237", "2241",
            "2239");

    /** Quadro 04 (p.6): visitas da prática D — TACS e ACS (ocupações de seis dígitos). */
    public static final List<String> VISIT_CBO = List.of("3222-55", "5151-05");

    /** 24 d (p.2): grupos de CBO de procedimentos (usados para o CBO de quem registra a vacina, AMB-C2-11). */
    public static final List<String> PROCEDURE_CBO = List.of(
            "2235", "2231", "2251", "2252", "2253", "2232", "2234", "2236", "2238", "2237", "2241", "3222", "2239",
            "5151-05", "3222-55");

    /** 24 f (p.3) e Quadro 03 (p.5): {@code 01.01.04.002-4} Avaliação antropométrica. */
    public static final String ANTHROPOMETRIC_EVALUATION = "0101040024";

    /** 24 f (p.3) e Quadro 03 (p.5): {@code 01.01.04.008-3} Medição de peso. */
    public static final String WEIGHT_MEASUREMENT = "0101040083";

    /** 24 f (p.3) e Quadro 03 (p.5): {@code 01.01.04.007-5} Medição de altura. */
    public static final String HEIGHT_MEASUREMENT = "0101040075";

    /** 24 f (p.3) e Quadro 03 (p.5): {@code 03.01.01.026-9} Avaliação do crescimento na puericultura. */
    public static final String GROWTH_EVALUATION = "0301010269";

    /** 24 f (p.3): {@code 03.01.01.027-7} Avaliação do desenvolvimento da criança na puericultura. */
    public static final String CHILD_DEVELOPMENT_SIGTAP = "0301010277";

    /** 24 f (p.3): {@code 03.01.01.025-0} Teleconsulta na atenção primária. */
    public static final String TELECONSULT_SIGTAP = "0301010250";

    /** Os seis códigos SIGTAP do 24 f (p.3), parâmetro {@code procedure_codes}. */
    public static final List<String> PROCEDURE_CODES = List.of(
            ANTHROPOMETRIC_EVALUATION,
            WEIGHT_MEASUREMENT,
            HEIGHT_MEASUREMENT,
            GROWTH_EVALUATION,
            CHILD_DEVELOPMENT_SIGTAP,
            TELECONSULT_SIGTAP);

    /** 24 g (p.3) grupo 1, componentes difteria, tétano e pertussis (DTP/DTPa na sigla do código). */
    public static final List<String> DTP = List.of("29", "39", "42", "43", "46", "47", "58");

    /** 24 g (p.3) grupo 1, componente hepatite B. */
    public static final List<String> HEPATITIS_B = List.of("09", "42", "43");

    /** 24 g (p.3) grupo 1, componente Haemophilus influenzae tipo b. */
    public static final List<String> HIB = List.of("17", "29", "39", "42", "43");

    /** 24 g (p.3) grupo 2, pólio inativada (VIP). */
    public static final List<String> POLIO = List.of("22", "29", "43", "58");

    /** 24 g (p.3) grupo 3, sarampo, caxumba e rubéola (SCR, SCRV). */
    public static final List<String> MMR = List.of("24", "56");

    /** 24 g (p.4) grupo 4, pneumocócica (VPC10, VPC13, VPC15, VPC20). */
    public static final List<String> PNEUMOCOCCAL = List.of("26", "59", "106", "107");

    /** 24 g (p.3): {@code 09} Vacina hepatite B — a única cuja dose ao nascer é discutida (AMB-C2-09 ii). */
    public static final String HEPATITIS_B_ONLY = "09";

    /** Os 16 códigos distintos do Quadro 05 (p.6), parâmetro {@code immunobiological_codes}. */
    public static final List<String> IMMUNOBIOLOGICAL_CODES =
            List.of("09", "17", "22", "24", "26", "29", "39", "42", "43", "46", "47", "56", "58", "59", "106", "107");

    /**
     * Motivo da visita "recém-nascido" (24 e, p.3). Domínio da fonte: coluna {@code
     * st_acomp_recem_nascido} de {@code tb_fat_visita_domiciliar} (dicionário do DW).
     */
    public static final String VISIT_REASON_NEWBORN = "ACOMP_RECEM_NASCIDO";

    /** Motivo da visita "criança" (24 e, p.3). Domínio da fonte: coluna {@code st_acomp_crianca}. */
    public static final String VISIT_REASON_CHILD = "ACOMP_CRIANCA";

    /** Desfecho "visita realizada" (LEDI 1, dicionário do DW); a ficha C2 não cita o desfecho (AMB-C2-08 iv). */
    public static final String VISIT_OUTCOME_DONE = "1";

    /** Saída do cadastro por óbito (LEDI {@code MotivoSaida} 135, dicionário do DW). */
    public static final String EXIT_DEATH = "135";

    /** Saída do cadastro por mudança de território (LEDI 136; item 15, p.2). */
    public static final String EXIT_TERRITORY_CHANGE = "136";

    /** Local de atendimento "domicílio" (LEDI 4, dicionário do DW, achado 6): atendimento domiciliar. */
    public static final String HOME_CARE_LOCATION = "4";

    /** Tipo de equipe eAP (24 b, p.2): "A boa prática (D) considera a pontuação integral para eAP, tipo 76." */
    public static final String TEAM_TYPE_EAP = "76";

    /** Ambiguity codes of the transcription (AMB-C2-xx) and the DW gap L3, as evidence reports them. */
    static final String AMB_C2_01 = "AMB-C2-01";

    static final String AMB_C2_02 = "AMB-C2-02";

    static final String AMB_C2_04 = "AMB-C2-04";

    static final String AMB_C2_06 = "AMB-C2-06";

    static final String AMB_C2_07 = "AMB-C2-07";

    static final String AMB_C2_08 = "AMB-C2-08";

    static final String AMB_C2_09 = "AMB-C2-09";

    static final String AMB_C2_10 = "AMB-C2-10";

    static final String AMB_C2_11 = "AMB-C2-11";

    static final String AMB_C2_15 = "AMB-C2-15";

    static final String LACUNA_L3 = "LACUNA-L3";

    /** Information models as the canonical records name them. */
    static final String MIAI = "MIAI";

    static final String MIP = "MIP";
    static final String MIAC = "MIAC";
    static final String MIVDT = "MIVDT";

    /** Team types the 24 b considers (eSF 70, eAP 76). */
    static final List<String> CONSIDERED_TEAM_TYPES = List.of("70", TEAM_TYPE_EAP);

    static final CboGroups CONSULT = new CboGroups(CONSULT_CBO);
    static final CboGroups ANTHROPOMETRY = new CboGroups(ANTHROPOMETRY_CBO);
    static final CboGroups VISIT = new CboGroups(VISIT_CBO);
    static final CboGroups PROCEDURE = new CboGroups(PROCEDURE_CBO);

    private C2Codes() {}
}
