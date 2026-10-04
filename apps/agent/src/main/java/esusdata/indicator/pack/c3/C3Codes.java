package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CboGroups;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The code tables of the C3 ficha (Nota Metodológica C3, SEI 0054619475, junho/2026), transcribed
 * literally in {@code docs/metodologia/c3-gestacao-puerperio.md}. Every list cites its page. SIGTAP
 * is written with digits only, CID-10 without the dot; CBO as the ficha writes it, compared by
 * {@link CboGroups} (four characters = family, six = occupation, AMB-C3-20). Lists the ficha does
 * not enumerate (the "códigos rápidos ABP") are not completed here (AMB-C3-10).
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // each list copies its quadro literally; CBO repeat by design
final class C3Codes {

    // ---- 24 f (p.3): "CID-10 e CIAP-2 para considerar uma gestação" ----

    static final List<String> PREGNANCY_CIAP = List.of("W03", "W78", "W79", "W81", "W84", "W85");

    static final List<String> PREGNANCY_CID = cid(
            "O10", "O11", "O12", "O13", "O14", "O15", "O16", "O20", "O21", "O22", "O23", "O24", "O25", "O26", "O28",
            "O29", "O30", "O31", "O32", "O33", "O34", "O35", "O36", "O40", "O41", "O43", "O44", "O46", "O47", "O48",
            "O75.2", "O75.3", "O98", "O99.0", "O99.1", "O99.2", "O99.3", "O99.4", "O99.5", "O99.6", "O99.7", "Z32.1",
            "Z33", "Z34", "Z35", "Z36", "Z64.0");

    // ---- 24 f (p.3): "CID-10 e/ou CIAP-2 para puerpério" ----

    /**
     * The CIAP-2 puerperium list without "48" and "49": the ficha writes them without a chapter
     * letter and they are not mapped (AMB-C3-09, limitation).
     */
    static final List<String> PUERPERIUM_CIAP =
            List.of("P29", "W18", "W19", "W70", "W90", "W91", "W92", "W93", "W94", "W95", "W96");

    static final List<String> PUERPERIUM_CID = cid(
            "F53", "F53.0", "F53.1", "F53.8", "F53.9", "M83.0", "O10", "O15.2", "O26.6", "O72.2", "O72.3", "O85", "O86",
            "O87", "O90", "O91", "O92", "O94", "O98", "O99", "Z37.0", "Z37.1", "Z37.2", "Z37.3", "Z37.4", "Z37.5",
            "Z37.6", "Z37.7", "Z37.9", "Z38", "Z39");

    // ---- 24 g (p.3): "CID-10 e/ou CIAP-2 ativos considerados para critérios de exclusão" ----

    static final List<String> EXCLUSION_CIAP = List.of("W82", "W83");

    static final List<String> EXCLUSION_CID = cid("O02", "O02.1", "O03", "O04", "O05", "O06", "Z30.3");

    // ---- CBO ----

    /** 24 c (p.2) and Quadro 02 (p.5): consultations of A, B and I — médicos and enfermeiros. */
    static final CboGroups CONSULT_CBO = CboGroups.of("2251", "2252", "2253", "2231", "2235");

    /**
     * Quadro 03 (p.6): blood pressure (C). Without 5151-05 (AMB-C3-14). "3224 Técnicos em Saúde
     * Bucal" read as the TSB occupations 3224-05 and 3224-25 (dicionário do DW); another occupation
     * of the family 3224 is AMB-C3-20.
     */
    static final CboGroups BLOOD_PRESSURE_CBO = CboGroups.of(
            "2251", "2252", "2253", "2231", "2235", "3222", "2232", "2234", "2236", "2238", "2237", "2241", "2239",
            "3224-05", "3224-25");

    /** The ACS occupation the Quadro 03 leaves out while accepting the MIVDT (AMB-C3-14 (i)). */
    static final CboGroups ACS_CBO = CboGroups.of("5151-05");

    /** Quadro 04 (p.6–7): weight and height (D). With 5151-05, without 3224. */
    static final CboGroups ANTHROPOMETRY_CBO = CboGroups.of(
            "2251", "2252", "2253", "2231", "2235", "3222", "5151-05", "2232", "2234", "2236", "2238", "2237", "2241",
            "2239");

    /**
     * Quadro 05 (p.7): home visits (E, J) by ACS or TACS. The quadro writes the TACS as the family
     * {@code 3222}, the 24 d as the occupation {@code 3222-55}; another occupation of the family
     * (técnico/auxiliar de enfermagem) is AMB-C3-16 (iii).
     */
    static final CboGroups VISIT_CBO = CboGroups.of("5151-05", "3222-55");

    static final CboGroups VISIT_CBO_FAMILY = CboGroups.of("3222");

    /** 24 c/d (p.2–3): every CBO the ficha lists; dTpa outside them is AMB-C3-13. */
    static final CboGroups LISTED_CBO = CboGroups.of(
            "2235", "2231", "2251", "2252", "2253", "2232", "2234", "2236", "2238", "2237", "2241", "3222", "2239",
            "5151-05", "3222-55", "3224");

    /** Quadro 07 (p.7): tests and evaluated exams (G, H). */
    static final CboGroups TEST_CBO = CboGroups.of("2251", "2252", "2253", "2231", "2235", "2234", "3222");

    /** The Quadro 07 CBO outside the 24 c consultation list: their MIAI records are AMB-C3-18 (iv). */
    static final CboGroups TEST_CBO_OUTSIDE_CONSULT = CboGroups.of("2234", "3222");

    /**
     * Quadro 08 (p.8): oral health (K) — cirurgião-dentista and TSB, the TSB read as the
     * occupations 3224-05 (TSB) and 3224-25 (TSB da ESF) of the DW dictionary.
     */
    static final CboGroups DENTAL_CBO = CboGroups.of("2232", "3224-05", "3224-25");

    /** The family 3224 (ASB, protético …): only "talvez" for C and K (AMB-C3-20). */
    static final CboGroups ORAL_HEALTH_FAMILY = CboGroups.of("3224");

    // ---- SIGTAP (24 h, p.3–4; Quadros 03, 04 e 07), digits only ----

    /** Quadro 03 (p.6): 03.01.10.003-9 Aferição da pressão arterial. */
    static final String BLOOD_PRESSURE_SIGTAP = "0301100039";

    /** Quadro 04 (p.7): 01.01.04.002-4 Avaliação antropométrica (no values: AMB-C3-15 (i)). */
    static final String ANTHROPOMETRIC_EVALUATION_SIGTAP = "0101040024";

    /** Quadro 04 (p.7): 01.01.04.008-3 Medição de peso. */
    static final String WEIGHT_SIGTAP = "0101040083";

    /** Quadro 04 (p.7): 01.01.04.007-5 Medição de altura. */
    static final String HEIGHT_SIGTAP = "0101040075";

    /**
     * 24 h (p.3–4): consultation codes no quadro cites (AMB-C3-12) — 03.01.01.003-0, 006-4, 011-0,
     * 012-9, 013-7 and 025-0.
     */
    static final List<String> CONSULT_SIGTAP =
            List.of("0301010030", "0301010064", "0301010110", "0301010129", "0301010137", "0301010250");

    /** Quadro 07 (p.8), agent named in the code's own description: sífilis. */
    static final List<String> SYPHILIS_SIGTAP =
            List.of("0214010074", "0214010082", "0214010252", "0202031098", "0202031110", "0202031179");

    /** Quadro 07 (p.8): HIV. */
    static final List<String> HIV_SIGTAP =
            List.of("0214010040", "0214010279", "0214010058", "0213010780", "0213010500", "0202030300");

    /** Quadro 07 (p.8): hepatite B. */
    static final List<String> HEPATITIS_B_SIGTAP =
            List.of("0214010104", "0214010236", "0202030784", "0202030970", "0213010208");

    /** Quadro 07 (p.8): hepatite C. */
    static final List<String> HEPATITIS_C_SIGTAP = List.of("0214010090", "0214010309", "0202030059", "0202030679");

    /**
     * Quadro 07 (p.8): 02.02.03.031-8 Anti-HTLV-1 + HTLV-2, which names none of the four agents
     * (AMB-C3-18 (ii)).
     */
    static final String HTLV_SIGTAP = "0202030318";

    /** The 22 SIGTAP of the Quadro 07 (p.8). */
    static final List<String> TEST_SIGTAP =
            concat(SYPHILIS_SIGTAP, HIV_SIGTAP, HEPATITIS_B_SIGTAP, HEPATITIS_C_SIGTAP, List.of(HTLV_SIGTAP));

    /** The 32 SIGTAP of the 24 h (p.3–4): what {@code procedure_performed} is asked for. */
    static final List<String> PROCEDURE_SIGTAP = concat(
            List.of(ANTHROPOMETRIC_EVALUATION_SIGTAP, WEIGHT_SIGTAP, HEIGHT_SIGTAP, BLOOD_PRESSURE_SIGTAP),
            CONSULT_SIGTAP,
            TEST_SIGTAP);

    // ---- Vacina (24 i, p.4; Quadro 06, p.7) ----

    /** "57 - Vacina dTpa adulto": "1 dose a cada gestação, a partir da vigésima semana". */
    static final String DTPA_ADULT = "57";

    // ---- MIAC (24 e, p.3; Quadros 04 e 08) ----

    /** "Atividade código 05 e 06" (24 e; Quadros 04 e 08). */
    static final List<String> MIAC_ACTIVITY_TYPES = List.of("05", "06");

    /*
     * "Práticas em Saúde" — 24 e: "códigos 01, 02, 04"; Quadro 04 (p.7, D): "código 01"; Quadro 08
     * (p.8, K): "códigos 02 e 04". The ficha numbers them as the FAC/CDS form does; the DW returns
     * LEDI PraticasEmSaude codes. Declared reading (AMB-C3-19; docs/discovery/capacidades-dw-v2.md
     * §3.7): 01 antropometria → LEDI 20, 02 aplicação tópica de flúor → LEDI 2, 04 escovação dental
     * supervisionada → LEDI 9.
     */

    /** LEDI codes of the 24 e practices (01, 02, 04), used for C. */
    static final List<String> MIAC_PRACTICES = List.of("20", "2", "9");

    /** LEDI code of the Quadro 04 practice (01, antropometria), used for D. */
    static final List<String> MIAC_PRACTICES_ANTHROPOMETRY = List.of("20");

    /** LEDI codes of the Quadro 08 practices (02, 04), used for K. */
    static final List<String> MIAC_PRACTICES_ORAL_HEALTH = List.of("2", "9");

    // ---- Lista de problemas e condições (LPC) ----

    /** What {@code condition_list} is asked for: the CIAP-2 of 24 f (gestação, puerpério) and 24 g. */
    static final List<String> CONDITION_CIAP = distinct(PREGNANCY_CIAP, PUERPERIUM_CIAP, EXCLUSION_CIAP);

    /** The CID-10 of 24 f and 24 g, without the dot: the query matches by category prefix. */
    static final List<String> CONDITION_CID = distinct(PREGNANCY_CID, PUERPERIUM_CID, EXCLUSION_CID);

    /** Condition status "2" (resolvido): its resolution date is the pregnancy outcome candidate (L2). */
    static final String CONDITION_RESOLVED = "2";

    /** Condition statuses "0" (ativo) and "1" (latente): the "ativos" of 24 f/g. */
    static final List<String> CONDITION_ACTIVE = List.of("0", "1");

    // ---- Modelos de informação (origem dos registros) ----

    static final String ORIGIN_MIAI = "MIAI";
    static final String ORIGIN_MIP = "MIP";
    static final String ORIGIN_MIAC = "MIAC";

    // ---- Equipes (24 b, p.2) ----

    /** CNES team type 76, eAP: E and J "consideram a pontuação integral" (24 b). */
    static final String EAP_TEAM_TYPE = "76";

    /** CNES team types 70 (eSF) and 76 (eAP): the teams the ficha considers (24 b). */
    static final List<String> TEAM_TYPES_IN_SCOPE = List.of("70", EAP_TEAM_TYPE);

    /** The pregnancy condition the PEC resolves on the outcome (guia T3, manual do PEC). */
    static final String PREGNANCY_CONDITION_CIAP = "W78";

    /** The largest gestational age LEDI accepts, in weeks. */
    static final int MAX_GESTATIONAL_WEEKS = 42;

    // ---- Saída do cadastro (item 15, p.1–2; LEDI MotivoSaida, dicionário do DW) ----

    /** LEDI {@code MotivoSaida} 135 — Óbito. */
    static final String EXIT_DEATH = "135";

    /** LEDI {@code MotivoSaida} 136 — Mudança de território. */
    static final String EXIT_CHANGE_OF_TERRITORY = "136";

    private C3Codes() {}

    /** A source token as compared here: stripped, upper case, {@code ""} for {@code null}. */
    static String token(String text) {
        return text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
    }

    /** True when the LPC condition is marked resolved ("2"). */
    static boolean resolved(CanonicalCondition condition) {
        return CONDITION_RESOLVED.equals(token(condition.status()));
    }

    /** True when the LPC condition is active or latent ("0", "1"); an unknown status is not. */
    static boolean active(CanonicalCondition condition) {
        return CONDITION_ACTIVE.contains(token(condition.status()));
    }

    /** True when a LEDI code is in the list, "5" and "05" being the same code. */
    static boolean ledi(String code, List<String> list) {
        String value = withoutLeadingZeros(code);
        return !value.isEmpty()
                && list.stream().anyMatch(c -> withoutLeadingZeros(c).equals(value));
    }

    /** True when any of the LEDI codes is in the list. */
    static boolean anyLedi(List<String> codes, List<String> list) {
        return codes.stream().anyMatch(c -> ledi(c, list));
    }

    private static String withoutLeadingZeros(String code) {
        return code == null ? "" : code.strip().replaceFirst("^0+(?=.)", "");
    }

    /** A CID-10 or CIAP-2 code as compared here: no dot, no spaces, upper case. */
    static String normalized(String code) {
        return code == null ? "" : code.replaceAll("[.\\s]", "").toUpperCase(Locale.ROOT);
    }

    private static List<String> cid(String... codes) {
        return Arrays.stream(codes).map(C3Codes::normalized).toList();
    }

    @SafeVarargs
    private static List<String> distinct(List<String>... lists) {
        Set<String> all = new LinkedHashSet<>();
        for (List<String> list : lists) {
            all.addAll(list);
        }
        return List.copyOf(all);
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> all = new ArrayList<>();
        for (List<String> list : lists) {
            all.addAll(list);
        }
        return List.copyOf(all);
    }
}
