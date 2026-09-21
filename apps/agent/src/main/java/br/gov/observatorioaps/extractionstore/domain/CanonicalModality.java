package br.gov.observatorioaps.extractionstore.domain;

/**
 * Extraction-store's own copy of the C1 arm enum — deliberately not a reference to
 * {@code pec-adapter.EncounterModality}. The canonical model must be usable without pec-adapter
 * (or any JDBC/SQL type) on the classpath, per §1.5's rule that indicator-engine "não depende de
 * JDBC... nem leitura direta do PEC" and receives canonical events, not source-shaped ones. The
 * small duplication here is the price of that boundary being real rather than incidental.
 */
public enum CanonicalModality {
    PROGRAMADO,
    ESPONTANEO,
    UNMAPPED
}
