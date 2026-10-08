package esusdata.indicator.reconciliation;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * What a capture knows about itself that the downloaded bytes and their normalized content do not
 * say: the id the revision is registered under, when it was captured, what the official file said
 * about when it was generated, and where it came from. The source file is named by its file name
 * only (never a path of the capturing machine); nothing here is inferred from that name.
 *
 * @param referenceId e.g. {@code sp-3541307-2026q1-c1-team-r1}, see {@link
 *     SiapsReferenceManifest#referenceId}
 * @param capturedAt when the file was captured, to the second
 * @param officialGeneratedAt the instant the SIAPS says it generated the file (its "Dado gerado em")
 * @param sourceDescription which SIAPS screen or report the file is
 * @param sourceFilename the downloaded file's name, without its directory
 */
record CaptureMetadata(
        String referenceId,
        OffsetDateTime capturedAt,
        LocalDateTime officialGeneratedAt,
        String sourceDescription,
        String sourceFilename) {

    CaptureMetadata {
        Objects.requireNonNull(referenceId, "referenceId");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt").truncatedTo(ChronoUnit.SECONDS);
        Objects.requireNonNull(officialGeneratedAt, "officialGeneratedAt");
        requireText(sourceDescription, "sourceDescription");
        requireText(sourceFilename, "sourceFilename");
        if (sourceFilename.indexOf('/') >= 0 || sourceFilename.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("the source file is named by its file name, not by a path");
        }
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }
}
