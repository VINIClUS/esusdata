package esusdata.source.pec;

import java.util.Set;

/**
 * Frozen {@code tb_dim_tipo_atendimento} leaf-id → C1 arm mapping, verified against PEC 5.4.37 /
 * PostgreSQL 9.6.13 on CT 133 (docs/discovery/2026-09-19-pec-ct133.md) and again against PEC 5.5.28
 * (docs/discovery/2026-09-24-pec-5528.md). This is a leaf-id list, on
 * purpose, never a {@code co_dim_tipo_atendimento_pai} filter: ids 8 ("Atendimento programado")
 * and 9 ("Atendimento não programado") hang off parent id 1 ("Consultas"), not parent id 4
 * ("Demanda espontânea") — a parent filter would silently misclassify or drop them.
 *
 * <p>Ids 8, 9, 10, 11 never occurred across the full observed history on this instance
 * (2023-07-03 to 2026-04-30, 294 114 rows) and are therefore {@code NOT_TESTED} in
 * {@code contracts/compatibility/pec-adapters.json} — {@link #classify} returns
 * {@link EncounterModality#UNMAPPED} for them rather than guessing an arm.
 */
public final class EncounterTypeMapping {

    public static final Set<Integer> PROGRAMADO_IDS = Set.of(2, 3);
    public static final Set<Integer> ESPONTANEO_IDS = Set.of(5, 6, 7);

    private EncounterTypeMapping() {
    }

    public static EncounterModality classify(int tipoAtendimentoId) {
        if (PROGRAMADO_IDS.contains(tipoAtendimentoId)) return EncounterModality.PROGRAMADO;
        if (ESPONTANEO_IDS.contains(tipoAtendimentoId)) return EncounterModality.ESPONTANEO;
        return EncounterModality.UNMAPPED;
    }
}
