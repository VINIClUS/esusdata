package br.gov.observatorioaps.pecadapter.domain;

import java.time.LocalDate;

/**
 * One row of {@code tb_fat_atendimento_individual}, exactly as read — before any canonicalization.
 * {@code _1} attribution fields only (unidade/equipe/cbo): discovery confirmed {@code _1} is the
 * primary participant and {@code _2} is a second participant on a shared encounter (~1.3% of
 * rows), not an alternate attribution — using {@code _1} causes no double counting.
 */
public record RawEncounterRecord(
        long pk,
        int tipoAtendimentoId,
        LocalDate careDate,
        String cnes,
        String ine,
        String cbo,
        String uuidFicha,
        int nuAtendimento
) {
    public EncounterModality modality() {
        return EncounterTypeMapping.classify(tipoAtendimentoId);
    }
}
