package br.gov.observatorioaps.extractionstore;

/**
 * Source-namespaced identity for one origin record — Tech Spec §1.4.3: "cada referência de origem
 * usa ao menos (source_id, source_entity_type, source_record_id)". This is what lets two sources
 * (or a DW/OLTP pair) be compared or deduplicated without their surrogate keys colliding by
 * coincidence.
 */
public record SourceRef(String sourceId, String entityType, String recordId) {
}
