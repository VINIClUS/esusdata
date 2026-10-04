package esusdata.indicator.model;

/**
 * Classification bands shared by C1–C7 and the Nota Final (§2.4, NT 8/2026). The band edges differ
 * per indicator: C1 is deliberately non-monotonic (a value above 70 is Regular, not better than
 * Ótimo) while C2–C7 share {@code >75 Ótimo · >50 Bom · >25 Suficiente · ≤25 Regular} ({@link
 * Bands}). Never render this as a generic "higher is better" bar for C1 (§2.4 implementation note).
 */
public enum Classification {
    OTIMO,
    BOM,
    SUFICIENTE,
    REGULAR
}
