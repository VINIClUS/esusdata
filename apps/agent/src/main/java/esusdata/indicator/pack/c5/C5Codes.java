package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CboGroups;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The code tables of the C5 ficha («NOTA METODOLÓGICA C5 - CUIDADO DA PESSOA COM HIPERTENSÃO»,
 * transcribed in {@code docs/metodologia/c5-cuidado-hipertensao.md}), each with the page it comes
 * from. Nothing here is inferred by analogy: a code the ficha does not list does not enter
 * (AMB-C5-04). SIGTAP codes are digits only, as the {@code procedure_codes} bind takes them; CBO
 * groups are written as the ficha writes them and matched by {@link CboGroups} (AMB-C5-08).
 */
public final class C5Codes {

    /** Item 24 f (p. 3): «CIAP-2: K86; K87; e/ou». */
    public static final List<String> CIAP_HIPERTENSAO = List.of("K86", "K87");

    /**
     * Item 24 f (p. 3), in the ficha's order and spelling: «CID-10: I10; I11; I11.0; I11.9; I12;
     * I12.0; I12.9; I13; I13.0; I13.1; I13.2; I13.9; I15; I15.0; I15.1; I15.2; I15.8; I15.9; O10;
     * O10.0; O10.1; O10.2; O10.3; O10.4; O10.9; O11.» Bound as {@code cid_codes} exactly so: the
     * query matches by category (a listed code matches every DW code that starts with it, without
     * the dot), and the rule then keeps only the literal codes (AMB-C5-04).
     */
    public static final List<String> CID_HIPERTENSAO = List.of(
            "I10", "I11", "I11.0", "I11.9", "I12", "I12.0", "I12.9", "I13", "I13.0", "I13.1", "I13.2", "I13.9", "I15",
            "I15.0", "I15.1", "I15.2", "I15.8", "I15.9", "O10", "O10.0", "O10.1", "O10.2", "O10.3", "O10.4", "O10.9",
            "O11");

    /** Quadro 03 (p. 5): «03.01.10.003-9» — «Aferição da pressão arterial.» */
    public static final String SIGTAP_AFERICAO_PA = "0301100039";

    /** Quadro 04 (p. 5): «01.01.04.002-4» — «Avaliação antropométrica» (weight and height at once). */
    public static final String SIGTAP_AVALIACAO_ANTROPOMETRICA = "0101040024";

    /** Quadro 04 (p. 5): «01.01.04.008-3» — «Medição de peso». */
    public static final String SIGTAP_MEDICAO_PESO = "0101040083";

    /** Quadro 04 (p. 5): «01.01.04.007-5» — «Medição de altura». */
    public static final String SIGTAP_MEDICAO_ALTURA = "0101040075";

    /** Every SIGTAP code of Quadros 03 and 04 (p. 5), bound as {@code procedure_codes}. */
    public static final List<String> PROCEDURE_CODES =
            List.of(SIGTAP_AFERICAO_PA, SIGTAP_AVALIACAO_ANTROPOMETRICA, SIGTAP_MEDICAO_PESO, SIGTAP_MEDICAO_ALTURA);

    /** Quadro 02 (p. 4), practice A: «2251, 2252, 2253, 2231» médicos and «2235» enfermeiros. */
    public static final CboGroups CBO_CONSULTA = CboGroups.of("2251", "2252", "2253", "2231", "2235");

    /**
     * Quadro 03 (p. 4–5), practice B. Without «5151-05», which nota de rodapé 4 (p. 6) withdrew; the
     * TACS ({@code 3222-55}) enters through the {@code 3222} family.
     */
    public static final CboGroups CBO_AFERICAO_PA = CboGroups.of(
            "2251", "2252", "2253", "2231", "2235", "3222", "2232", "2234", "2236", "2238", "2237", "2241", "2239",
            "3224");

    /** Quadro 04 (p. 5), practice C: with «5151-05», without «3224». */
    public static final CboGroups CBO_ANTROPOMETRIA = CboGroups.of(
            "2251", "2252", "2253", "2231", "2235", "3222", "5151-05", "2232", "2234", "2236", "2238", "2237", "2241",
            "2239");

    /** Quadro 05 (p. 5), practice D: «3222-55» TACS and «5151-05» ACS. */
    public static final CboGroups CBO_VISITA = CboGroups.of("3222-55", "5151-05");

    private static final String CIAP2 = "CIAP2";
    private static final String CID10 = "CID10";
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern DOT = Pattern.compile("\\.");
    private static final String SEPARATOR = ":";
    private static final int CID_CATEGORY_LENGTH = 3;

    /** Every listed code as {@link #conditionKey} writes it. */
    private static final Set<String> ELIGIBLE_KEYS = Stream.concat(
                    CIAP_HIPERTENSAO.stream().map(code -> CIAP2 + SEPARATOR + code),
                    CID_HIPERTENSAO.stream().map(code -> CID10 + SEPARATOR + undotted(code)))
            .collect(Collectors.toUnmodifiableSet());

    /** The listed CID-10 categories ({@code I10} … {@code O11}) and CIAP-2 codes, as key prefixes. */
    private static final Set<String> NEIGHBOR_PREFIXES = Stream.concat(
                    CIAP_HIPERTENSAO.stream().map(code -> CIAP2 + SEPARATOR + code),
                    CID_HIPERTENSAO.stream().map(code -> CID10 + SEPARATOR + code.substring(0, CID_CATEGORY_LENGTH)))
            .collect(Collectors.toUnmodifiableSet());

    private C5Codes() {}

    /**
     * Whether a condition code is one of item 24 f (p. 3). CIAP-2 matches exactly (upper case, no
     * spaces); CID-10 is compared without the dot, which is only how the PEC writes it, not an
     * analogy: {@code I110} is {@code I11.0}, while {@code I11.8} and {@code I10.0} stay out.
     *
     * @param codeSystem {@code CIAP2} or {@code CID10} (hyphens, spaces and case ignored)
     */
    public static boolean isEligibleCondition(String codeSystem, String code) {
        String key = conditionKey(codeSystem, code);
        return key != null && ELIGIBLE_KEYS.contains(key);
    }

    /**
     * One code as the rule compares it: the system without case, hyphens or spaces ({@code CID-10}
     * is {@code CID10}), a colon, and the code in upper case without spaces — and, for CID-10,
     * without the dot. {@code null} when either part is missing.
     */
    public static String conditionKey(String codeSystem, String code) {
        if (codeSystem == null || code == null) {
            return null;
        }
        String system =
                NOT_ALPHANUMERIC.matcher(codeSystem.toUpperCase(Locale.ROOT)).replaceAll("");
        String normalized = WHITESPACE.matcher(code).replaceAll("").toUpperCase(Locale.ROOT);
        return system + SEPARATOR + (CID10.equals(system) ? undotted(normalized) : normalized);
    }

    /**
     * An unlisted code next to the list — a CID-10 of category {@code I10}, {@code I11}, {@code
     * I12}, {@code I13}, {@code I15}, {@code O10} or {@code O11}, or {@code K86}/{@code K87} with a
     * suffix — that the literal reading leaves out and the AMB-C5-04 diagnostic counts. Other
     * conditions ({@code E11}) are not diagnosed.
     */
    static boolean isUnlistedNeighbor(String codeSystem, String code) {
        String key = conditionKey(codeSystem, code);
        if (key == null || ELIGIBLE_KEYS.contains(key)) {
            return false;
        }
        return NEIGHBOR_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private static String undotted(String code) {
        return DOT.matcher(code).replaceAll("");
    }
}
