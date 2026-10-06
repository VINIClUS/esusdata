package esusdata.indicator.pack.c4;

import esusdata.indicator.model.TeamScope;

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
     * Linked to a team whose type, valid on the last day of the competência, is known and is neither
     * eSF 70 nor eAP 76 (item 24 b, p. 2: «Serão consideradas equipes de Saúde da Família (eSF), e
     * equipes de Atenção Primária (eAP), tipo 70 e 76»).
     */
    public static final String TEAM_TYPE_OUT_OF_SCOPE = TeamScope.REASON_OUT_OF_SCOPE;

    /** Linked to a team (INE) with no type in the source (C4-D2): not a considered team. */
    public static final String TEAM_WITHOUT_TYPE = TeamScope.REASON_WITHOUT_TYPE;

    /** Linked to a team (INE) with two different types on the same day (C4-D2). */
    public static final String TEAM_TYPE_CONFLICT = TeamScope.REASON_CONFLICT;

    /** «todas as condições ou problemas marcados como "resolvidos" no PEC» (item 15, p. 2). */
    public static final String CONDITIONS_RESOLVED = "INTERROMPIDO_CONDICOES_RESOLVIDAS";

    public static final String PRACTICE_MET = "PRATICA_CUMPRIDA";
    public static final String PRACTICE_NOT_MET = "PRATICA_NAO_CUMPRIDA";

    /**
     * Practice D of a person of an eAP 76 team who had no two valid visits: the ficha does not make D
     * a condition of the eAP's score (item 24 b), so it is credited in full (C4-D1).
     */
    public static final String PRACTICE_CREDITED_EAP = TeamScope.REASON_CREDITED_EAP76;

    private C4Reasons() {}
}
