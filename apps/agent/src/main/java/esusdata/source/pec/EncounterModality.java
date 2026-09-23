package esusdata.source.pec;

/**
 * Canonical C1 arms. {@code UNMAPPED} is a distinct value, not a null — an encounter whose
 * {@code tb_dim_tipo_atendimento} id falls outside the frozen leaf set must be visible as an
 * exclusion, never silently dropped or silently bucketed (Tech Spec §2.4, mutual exclusivity
 * requirement).
 */
public enum EncounterModality {
    PROGRAMADO,
    ESPONTANEO,
    UNMAPPED
}
