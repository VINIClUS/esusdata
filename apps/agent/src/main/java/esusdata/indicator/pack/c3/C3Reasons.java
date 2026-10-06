package esusdata.indicator.pack.c3;

/** The stable reason codes of C3's evidence rows (contract of the rule; ENG-36). */
final class C3Reasons {

    // ---- cohort ----

    static final String EXCLUIDO_SEM_VINCULO = "EXCLUIDO_SEM_VINCULO";
    static final String EXCLUIDO_EQUIPE_FORA_DO_ESCOPO = "EXCLUIDO_EQUIPE_FORA_DO_ESCOPO";
    static final String INTERROMPIDO_MUDANCA_TERRITORIO = "INTERROMPIDO_MUDANCA_TERRITORIO";
    static final String EXCLUIDO_OBITO = "EXCLUIDO_OBITO";
    static final String EXCLUIDO_ABORTO = "EXCLUIDO_ABORTO";

    /** A DUM or IG without a 24 f pregnancy code in {@code [DUM, D]} (AMB-C3-03 (iii)). */
    static final String EXCLUIDO_SEM_CODIGO_GESTACAO = "EXCLUIDO_SEM_CODIGO_GESTACAO";

    /** A 24 f pregnancy code without a DUM or IG to date it (AMB-C3-03 (iii)). */
    static final String EXCLUIDO_SEM_DUM_NEM_IG = "EXCLUIDO_SEM_DUM_NEM_IG";

    static final String ELEGIVEL_DESFECHO_REGISTRADO = "ELEGIVEL_DESFECHO_REGISTRADO";
    static final String ELEGIVEL_DATA_SUBSTITUTIVA_294D = "ELEGIVEL_DATA_SUBSTITUTIVA_294D";
    static final String ELEGIVEL_DESFECHO_RESOLUCAO_LPC = "ELEGIVEL_DESFECHO_RESOLUCAO_LPC";
    static final String MARCO_DUM = "MARCO_DUM";

    // ---- practices ----

    static final String CUMPRIDA = "CUMPRIDA";
    static final String NAO_CUMPRIDA = "NAO_CUMPRIDA";
    static final String EVIDENCIA = "EVIDENCIA";

    private C3Reasons() {}
}
