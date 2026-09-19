package br.gov.observatorioaps.indicatorengine;

/** C1 classification bands (§2.4) — deliberately non-monotonic: a value above 70 is Regular, not
 * better than Ótimo. Never render this as a generic "higher is better" bar (§2.4 implementation
 * note). */
public enum Classification {
    OTIMO,
    BOM,
    SUFICIENTE,
    REGULAR
}
