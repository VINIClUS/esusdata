package esusdata.indicator.pack.c7;

import esusdata.indicator.model.CboGroups;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Code tables of the C7 ficha (nota metodológica SEI 0054641718, junho/2026), literal and with the
 * page of the PDF they come from ({@code docs/metodologia/c7-prevencao-cancer.md}). SIGTAP goes as
 * digits only; CBO as the ficha writes it; CID-10 without the dot, as the ficha lists it. A change
 * to any list is a new {@link C7Pack#RULE_VERSION}.
 */
public final class C7Codes {

    /**
     * Quadros 02, 04 e 05 (pp. 5–6): "2251, 2252, 2253, 2231" Médicos; "2235" Enfermeiros. The
     * wider lists of item 24, d and item 4.5 are AMB-C7-09 (limitation, not applied).
     */
    public static final CboGroups MEDICOS_ENFERMEIROS = CboGroups.of("2251", "2252", "2253", "2231", "2235");

    /** Quadro 02 (pp. 5–6), prática A, janela de 36 meses. */
    public static final List<String> A_SIGTAP_36_MESES = List.of(
            "0201020033", // 02.01.02.003-3 Coleta de citopatológico de colo uterino (p. 5)
            "0203010086", // 02.03.01.008-6 Exame citopatológico cérvico-vaginal/microflora-rastreamento (p. 5)
            "0203010019", // 02.03.01.001-9 Exame citopatológico cérvico-vaginal/microflora (p. 6)
            "0201020076", // 02.01.02.007-6 Coleta de material do colo do útero para exame molecular de HPV (p. 6)
            "0201020084"); // 02.01.02.008-4 Entrega de material obtido por auto coleta … HPV (p. 6)

    /**
     * Quadro 02 (p. 6), observação "Considerar registros nos últimos 60 meses": 02.02.10.025-1
     * Exame molecular de detecção de HPV — só ele tem 60 meses (MET-25).
     */
    public static final String A_SIGTAP_HPV_MOLECULAR = "0202100251";

    /**
     * Nota de rodapé 4 (p. 7): "A contabilização desse SIGTAP passou a ser realizada a partir da
     * competência janeiro de 2026". From which competência the code counts; from then on records dated before
     * 2026-01 count too, inside the 60 months (C7-D2).
     */
    public static final YearMonth HPV_MOLECULAR_DESDE = YearMonth.of(2026, 1);

    /** Quadro 02 (p. 6): "ABEX001 Citopatológico" — código AB de exame, lido como procedimento. */
    public static final List<String> A_CODIGOS_ABEX = List.of("ABEX001");

    /**
     * Quadro 02 (p. 6): "ABP022 Rastreamento de câncer do colo do útero" — código AB de problema
     * avaliado ({@code tb_dim_ciap}), lido na lista de problemas, nunca como procedimento.
     */
    public static final String A_ABP = "ABP022";

    /** Quadro 05 (p. 6), prática D: 02.04.03.003-0 Mamografia; 02.04.03.018-8 Mamografia bilateral para rastreamento. */
    public static final List<String> D_SIGTAP = List.of("0204030030", "0204030188");

    /** Quadro 05 (p. 6): "ABP023 Rastreamento de câncer de mama". */
    public static final String D_ABP = "ABP023";

    /** Quadro 03 (p. 6) e item 24, i (p. 4): "67 - Vacina HPV quadrivalente"; "93 - Vacina HPV nonavalente". */
    public static final List<String> B_VACINAS_HPV = List.of("67", "93");

    /** Item 24, alínea g (p. 3): CIAP-2 de saúde sexual e reprodutiva (28 códigos). */
    public static final List<String> C_CIAP2 = List.of(
            "B25", "W02", "W10", "W11", "W12", "W13", "W14", "W15", "W79", "W82", "X01", "X02", "X03", "X04", "X05",
            "X06", "X07", "X08", "X09", "X10", "X11", "X12", "X13", "X23", "X24", "X82", "X89", "Y14");

    /** Item 24, alínea g (pp. 3–4): CID-10 de saúde sexual e reprodutiva (105 códigos, sem ponto). */
    public static final List<String> C_CID10 = List.of(
            "N80", "N800", "N801", "N802", "N803", "N804", "N805", "N806", "N808", "N809", "N91", "N910", "N911",
            "N912", "N913", "N914", "N915", "N92", "N920", "N921", "N922", "N923", "N924", "N925", "N926", "N93",
            "N930", "N938", "N939", "N94", "N940", "N941", "N942", "N943", "N944", "N945", "N946", "N948", "N949",
            "N95", "N950", "N951", "N952", "N953", "N958", "N959", "N96", "N97", "N970", "N971", "N972", "N973", "N974",
            "N978", "N979", "O03", "O04", "R102", "T742", "Y050", "Y051", "Y052", "Y053", "Y054", "Y055", "Y056",
            "Y057", "Y058", "Y059", "Z123", "Z124", "Z205", "Z206", "Z30", "Z300", "Z301", "Z302", "Z303", "Z304",
            "Z305", "Z308", "Z309", "Z31", "Z310", "Z311", "Z312", "Z313", "Z314", "Z315", "Z316", "Z318", "Z319",
            "Z320", "Z600", "Z630", "Z640", "Z70", "Z700", "Z701", "Z702", "Z703", "Z708", "Z709", "Z717", "Z725");

    /** Item 24, alínea g (p. 4): "Código ABP: ABP003; ABP022; ABP023." */
    public static final List<String> C_CODIGOS_ABP = List.of("ABP003", "ABP022", "ABP023");

    /**
     * Sex as {@code CanonicalPerson} carries it, in words (como-adicionar.md, convenções); the
     * correspondence with the ficha's "Registro de sexo" is AMB-C7-12.
     */
    public static final String SEXO_FEMININO = "FEMININO";

    /** Sex as {@code CanonicalPerson} carries it; the source of the record is AMB-C7-12. */
    public static final String SEXO_MASCULINO = "MASCULINO";

    /** LEDI {@code identidadeGeneroCidadao} 149: "Homem transgênero" (itens 4.1.2, p. 5). */
    public static final String IDENTIDADE_HOMEM_TRANSGENERO = "149";

    /** LEDI {@code identidadeGeneroCidadao} 150: "Mulher transgênero" (item 4.2, p. 5). */
    public static final String IDENTIDADE_MULHER_TRANSGENERO = "150";

    /** LEDI {@code MotivoSaida} 136: "Mudança de território" (item 15, p. 2). */
    public static final String SAIDA_MUDANCA_TERRITORIO = "136";

    /** LEDI {@code MotivoSaida} 135: "Óbito". */
    public static final String SAIDA_OBITO = "135";

    private static final Pattern SEPARATORS = Pattern.compile("[-.\\s]");

    static final Set<String> A_36_MESES = normalizedSet(A_SIGTAP_36_MESES, A_CODIGOS_ABEX);
    static final Set<String> D_CODIGOS = normalizedSet(D_SIGTAP);
    static final Set<String> B_VACINAS = normalizedSet(B_VACINAS_HPV);

    /** CIAP-2 (and the ABP codes, stored with them) of the item 24, g — never compared with CID-10. */
    static final Set<String> C_CIAP2_ABP = normalizedSet(C_CIAP2, C_CODIGOS_ABP);

    private C7Codes() {}

    /** Codes compared as the ficha writes them: no dots, hyphens or spaces, upper case. */
    static String normalized(String code) {
        return code == null ? "" : SEPARATORS.matcher(code).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /**
     * Codes bound as {@code procedure_codes}: SIGTAP digits only and the ABEX exam code literal, as
     * {@code tb_dim_procedimento.co_proced} stores both (como-adicionar.md, convenções) — A (36 and
     * 60 months) and D. The ABP codes are evaluated problems and go to {@code condition_list}.
     */
    static List<String> procedureCodes(YearMonth competencia) {
        List<String> codes = new ArrayList<>(A_SIGTAP_36_MESES);
        if (!competencia.isBefore(HPV_MOLECULAR_DESDE)) {
            codes.add(A_SIGTAP_HPV_MOLECULAR);
        }
        codes.addAll(A_CODIGOS_ABEX);
        codes.addAll(D_SIGTAP);
        return List.copyOf(codes);
    }

    /**
     * CID-10 by category, as the capability matches it (como-adicionar.md): a listed code matches
     * every source code that starts with it once the dot is gone ({@code O03} matches {@code O03.9}).
     */
    static boolean cidListed(String sourceCode) {
        String code = normalized(sourceCode);
        if (code.isEmpty()) {
            return false;
        }
        for (String listed : C_CID10) {
            if (code.startsWith(listed)) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    static Set<String> normalizedSet(Collection<String>... lists) {
        Set<String> set = new HashSet<>();
        for (Collection<String> list : lists) {
            for (String code : list) {
                set.add(normalized(code));
            }
        }
        return Set.copyOf(set);
    }
}
