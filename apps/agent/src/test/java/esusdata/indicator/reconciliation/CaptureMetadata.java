package esusdata.indicator.reconciliation;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * What a capture knows about itself that the downloaded bytes and their normalized content do not
 * say: the id the revision is registered under, when it was captured, what the official file said
 * about when it was generated, and where it came from. The file is not named here: the name it was
 * saved under is a person's choice and may carry an INE or a team, so its manifest calls the
 * download by its raw hash ({@link SiapsReferenceManifest#sourceFilenameOf}).
 *
 * @param referenceId e.g. {@code sp-3541307-2026q1-c1-team-r1}, see {@link
 *     SiapsReferenceManifest#referenceId}
 * @param capturedAt when the file was captured, to the second
 * @param officialGeneratedAt the instant the SIAPS says it generated the file (its "Dado gerado em")
 * @param sourceDescription which SIAPS screen or report the file is
 * @param siblingReferenceIds for the Nota Final, the revisions of C1 to C7 the same download
 *     contained ({@link SiapsReferenceManifest#siblingReferenceIds}); empty for a pack
 */
record CaptureMetadata(
        String referenceId,
        OffsetDateTime capturedAt,
        LocalDateTime officialGeneratedAt,
        String sourceDescription,
        List<String> siblingReferenceIds) {

    CaptureMetadata {
        Objects.requireNonNull(referenceId, "referenceId");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt").truncatedTo(ChronoUnit.SECONDS);
        Objects.requireNonNull(officialGeneratedAt, "officialGeneratedAt");
        if (sourceDescription == null || sourceDescription.isBlank()) {
            throw new IllegalArgumentException("sourceDescription must not be blank");
        }
        siblingReferenceIds = List.copyOf(siblingReferenceIds);
    }

    /** The metadata of a revision that names no sibling: a pack's. */
    CaptureMetadata(
            String referenceId,
            OffsetDateTime capturedAt,
            LocalDateTime officialGeneratedAt,
            String sourceDescription) {
        this(referenceId, capturedAt, officialGeneratedAt, sourceDescription, List.of());
    }
}
