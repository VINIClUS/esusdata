package esusdata.indicator.pack.c3;

/**
 * The stable reason codes of C3's evidence rows (contract of the rule; ENG-36). An ambiguity of
 * the ficha is written {@code AMBIGUIDADE_AMB_C3_xx} on a subject or practice and {@code
 * EVIDENCIA_AMBIGUA_AMB_C3_xx} on the event behind it.
 */
final class C3Reasons {

    // ---- cohort ----

    static final String EXCLUIDO_SEM_VINCULO = "EXCLUIDO_SEM_VINCULO";
    static final String EXCLUIDO_EQUIPE_FORA_DO_ESCOPO = "EXCLUIDO_EQUIPE_FORA_DO_ESCOPO";
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

    // ---- ambiguities: built from Ambiguity by ambiguity(...) and ambiguousEvidence(...) ----

    static final String AMBIGUITY_PREFIX = "AMBIGUIDADE_";
    static final String AMBIGUOUS_EVIDENCE_PREFIX = "EVIDENCIA_AMBIGUA_";

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
