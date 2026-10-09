package esusdata.indicator.reconciliation;

/**
 * Whether the SIAPS flagged an official figure as preliminary ("Dado Preliminar") when it was
 * generated. A preliminary and a final download of the same figures are different revisions: the
 * status is part of the normalized content (see {@link NormalizedReference}).
 */
enum OfficialStatus {
    PRELIMINARY,
    FINAL
}
