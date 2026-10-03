package esusdata.indicator.pack.c4;

/** Stable reason codes of the C4 evidence rows (ENG-36): every exclusion says why. */
@SuppressWarnings("PMD.DataClass") // a table of frozen identifiers
public final class C4Reasons {

    /** In the denominator: diabetes evaluated, linked, not interrupted. */
    public static final String ELIGIBLE = "ELEGIVEL";

    /**
     * A diabetes signal that does not qualify: self-reported only, evaluated before 2013 or after the
     * cutoff, or evaluated by a CBO that is not médico/enfermeiro (item 5, p. 1).
     */
    public static final String NO_PROFESSIONAL_EVALUATION = "SEM_CONDICAO_AVALIADA";

    /** «Óbito no CadSUS» (item 15, p. 2), approximated by the death the local PEC records. */
    public static final String DEATH = "INTERROMPIDO_OBITO";

    /** «Saída do cidadão do cadastro» with «Mudança de território» (item 15, p. 2). */
    public static final String TERRITORY_CHANGE = "INTERROMPIDO_MUDANCA_TERRITORIO";

    /** No usable individual registration linking the person to a team on the cutoff. */
    public static final String NO_LINK = "SEM_VINCULO";

    /**
     * Linked to a team whose CNES type is known and is neither eSF 70 nor eAP 76 (item 24 b, p. 2:
     * «Serão consideradas equipes de Saúde da Família (eSF), e equipes de Atenção Primária (eAP), tipo
     * 70 e 76»).
     */
    public static final String TEAM_TYPE_OUT_OF_SCOPE = "EQUIPE_FORA_DO_ESCOPO";

    /** «todas as condições ou problemas marcados como "resolvidos" no PEC» (item 15, p. 2). */
    public static final String CONDITIONS_RESOLVED = "INTERROMPIDO_CONDICOES_RESOLVIDAS";

    public static final String PRACTICE_MET = "PRATICA_CUMPRIDA";
    public static final String PRACTICE_NOT_MET = "PRATICA_NAO_CUMPRIDA";

    /**
     * Practice D of a person linked to an eAP tipo 76 team: the ficha does not decide it (AMB-C4-01),
     * so it is {@code PRACTICE_AMBIGUOUS}, without points.
     */
    public static final String PRACTICE_INFORMATIVE_EAP = "AMB-C4-01_PRATICA_D_EAP76";

    private C4Reasons() {}
}
