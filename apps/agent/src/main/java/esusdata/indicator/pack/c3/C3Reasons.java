package esusdata.indicator.pack.c3;

/**
 * The stable reason codes of C3's evidence rows (contract of the rule; ENG-36). An ambiguity of
 * the ficha is written {@code AMBIGUIDADE_AMB_C3_xx} on a subject or practice and {@code
 * EVIDENCIA_AMBIGUA_AMB_C3_xx} on the event behind it.
 */
final class C3Reasons {

    // ---- cohort ----

    static final String SEM_VINCULO = "SEM_VINCULO";
    static final String INTERROMPIDO_MUDANCA_TERRITORIO = "INTERROMPIDO_MUDANCA_TERRITORIO";
    static final String EXCLUIDO_OBITO = "EXCLUIDO_OBITO";
    static final String EXCLUIDO_ABORTO = "EXCLUIDO_ABORTO";
    static final String ELEGIVEL_DESFECHO_REGISTRADO = "ELEGIVEL_DESFECHO_REGISTRADO";
    static final String ELEGIVEL_DATA_SUBSTITUTIVA_294D = "ELEGIVEL_DATA_SUBSTITUTIVA_294D";
    static final String ELEGIVEL_DESFECHO_RESOLUCAO_LPC = "ELEGIVEL_DESFECHO_RESOLUCAO_LPC";
    static final String MARCO_DUM = "MARCO_DUM";

    // ---- practices ----

    static final String CUMPRIDA = "CUMPRIDA";
    static final String NAO_CUMPRIDA = "NAO_CUMPRIDA";
    static final String EAP_TIPO_76_PONTUACAO_INTEGRAL = "EAP_TIPO_76_PONTUACAO_INTEGRAL";
    static final String EVIDENCIA = "EVIDENCIA";

    // ---- ambiguities (prefix + Ambiguity name) ----

    static final String AMBIGUITY_PREFIX = "AMBIGUIDADE_";
    static final String AMBIGUOUS_EVIDENCE_PREFIX = "EVIDENCIA_AMBIGUA_";

    static final String AMBIGUIDADE_AMB_C3_01 = "AMBIGUIDADE_AMB_C3_01";
    static final String AMBIGUIDADE_AMB_C3_02 = "AMBIGUIDADE_AMB_C3_02";
    static final String AMBIGUIDADE_AMB_C3_03 = "AMBIGUIDADE_AMB_C3_03";
    static final String AMBIGUIDADE_AMB_C3_04 = "AMBIGUIDADE_AMB_C3_04";
    static final String AMBIGUIDADE_AMB_C3_05 = "AMBIGUIDADE_AMB_C3_05";
    static final String AMBIGUIDADE_AMB_C3_07 = "AMBIGUIDADE_AMB_C3_07";
    static final String AMBIGUIDADE_AMB_C3_08 = "AMBIGUIDADE_AMB_C3_08";
    static final String AMBIGUIDADE_AMB_C3_11 = "AMBIGUIDADE_AMB_C3_11";
    static final String AMBIGUIDADE_AMB_C3_12 = "AMBIGUIDADE_AMB_C3_12";
    static final String AMBIGUIDADE_AMB_C3_13 = "AMBIGUIDADE_AMB_C3_13";
    static final String AMBIGUIDADE_AMB_C3_14 = "AMBIGUIDADE_AMB_C3_14";
    static final String AMBIGUIDADE_AMB_C3_15 = "AMBIGUIDADE_AMB_C3_15";
    static final String AMBIGUIDADE_AMB_C3_16 = "AMBIGUIDADE_AMB_C3_16";
    static final String AMBIGUIDADE_AMB_C3_17 = "AMBIGUIDADE_AMB_C3_17";
    static final String AMBIGUIDADE_AMB_C3_18 = "AMBIGUIDADE_AMB_C3_18";
    static final String AMBIGUIDADE_AMB_C3_19 = "AMBIGUIDADE_AMB_C3_19";

    static final String EVIDENCIA_AMBIGUA_AMB_C3_01 = "EVIDENCIA_AMBIGUA_AMB_C3_01";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_02 = "EVIDENCIA_AMBIGUA_AMB_C3_02";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_03 = "EVIDENCIA_AMBIGUA_AMB_C3_03";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_04 = "EVIDENCIA_AMBIGUA_AMB_C3_04";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_08 = "EVIDENCIA_AMBIGUA_AMB_C3_08";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_11 = "EVIDENCIA_AMBIGUA_AMB_C3_11";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_12 = "EVIDENCIA_AMBIGUA_AMB_C3_12";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_13 = "EVIDENCIA_AMBIGUA_AMB_C3_13";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_14 = "EVIDENCIA_AMBIGUA_AMB_C3_14";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_15 = "EVIDENCIA_AMBIGUA_AMB_C3_15";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_16 = "EVIDENCIA_AMBIGUA_AMB_C3_16";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_17 = "EVIDENCIA_AMBIGUA_AMB_C3_17";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_18 = "EVIDENCIA_AMBIGUA_AMB_C3_18";
    static final String EVIDENCIA_AMBIGUA_AMB_C3_19 = "EVIDENCIA_AMBIGUA_AMB_C3_19";

    private C3Reasons() {}

    /** {@code AMBIGUIDADE_AMB_C3_xx}: a subject or practice the ficha leaves undecided. */
    static String ambiguity(Ambiguity ambiguity) {
        return AMBIGUITY_PREFIX + ambiguity.name();
    }

    /** {@code EVIDENCIA_AMBIGUA_AMB_C3_xx}: an event that counts only under one reading. */
    static String ambiguousEvidence(Ambiguity ambiguity) {
        return AMBIGUOUS_EVIDENCE_PREFIX + ambiguity.name();
    }
}
