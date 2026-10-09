package esusdata.indicator.reconciliation;

/**
 * Tells a re-download of an official reference from a changed one (spec §16.3). Two captures are
 * the same revision when their normalized content hashes alike, whatever their raw bytes: the
 * download carries a generation timestamp that changes every time. When the same reference (same
 * municipality, quadrimestre, source and pack) has a different normalized hash, the SIAPS changed
 * the figures, the status of the data or the layout: that is a new revision, never a replacement
 * of the registered one. A Nota Final is also a new revision when its download holds other revisions
 * of its packs ({@link SiapsReferenceManifest#siblingReferenceIds}): the same final classes read
 * beside other pack figures are another reference to reconcile.
 */
final class ReferenceDrift {

    enum DriftStatus {
        /** Same reference, same normalized content (the raw hash may differ). */
        SAME_REVISION,
        /** Same reference, different normalized content or, for the Nota Final, other siblings: a new revision. */
        REFERENCE_DRIFT
    }

    private ReferenceDrift() {}

    /**
     * Compares a registered revision with a freshly captured one.
     *
     * @throws IllegalArgumentException when they are not the same reference: there is nothing to
     *     call drift between a reference and another one
     */
    static DriftStatus compare(SiapsReferenceManifest registered, SiapsReferenceManifest captured) {
        if (!sameReference(registered, captured)) {
            throw new IllegalArgumentException(
                    "not the same reference: " + registered.referenceId() + " and " + captured.referenceId());
        }
        return registered.normalizedSha256().equals(captured.normalizedSha256())
                        && registered.siblingReferenceIds().equals(captured.siblingReferenceIds())
                ? DriftStatus.SAME_REVISION
                : DriftStatus.REFERENCE_DRIFT;
    }

    /** Same municipality, quadrimestre, source kind and indicator codes; the revision is what differs. */
    private static boolean sameReference(SiapsReferenceManifest registered, SiapsReferenceManifest captured) {
        return registered.municipalityIbge().equals(captured.municipalityIbge())
                && registered.quadrimestre().equals(captured.quadrimestre())
                && registered.sourceKind() == captured.sourceKind()
                && registered.indicatorCodes().equals(captured.indicatorCodes());
    }
}
