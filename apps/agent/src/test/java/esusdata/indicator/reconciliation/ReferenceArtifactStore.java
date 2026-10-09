package esusdata.indicator.reconciliation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The local, content-addressed store of the official reference revisions (spec §13). Each revision
 * lives in {@code <artifact-dir>/<municipality>/<quadrimestre>/<normalized-sha>/} with the
 * downloaded file ({@value #RAW}), its normalized content ({@value #NORMALIZED}) and its manifest
 * ({@value #MANIFEST}). The raw file and the normalized content never go to Git; the manifest is
 * what gets versioned.
 *
 * <p>A revision is written once and never changed. It appears in one atomic step (the directory is
 * built beside it and moved into place), so a reader sees all of it or none of it. Storing what is
 * already stored returns the revision that is there; storing different content under a hash that
 * is taken is a refusal, never an overwrite. Reading ({@link #load}) re-verifies both hashes and
 * that the content says the same municipality and period as the manifest.
 */
final class ReferenceArtifactStore {

    static final String RAW = "raw.csv";
    static final String NORMALIZED = "normalized.json";
    static final String MANIFEST = "manifest.json";

    private static final String STAGING = ".staging-";

    private final Path artifactDir;

    ReferenceArtifactStore(Path artifactDir) {
        this.artifactDir = Objects.requireNonNull(artifactDir, "artifactDir");
    }

    /**
     * What a capture of these bytes is, stored or not: the manifest of the downloaded {@code raw}
     * file and its {@code normalized} content. Comparing it with the registered manifest ({@link
     * ReferenceDrift#compare}) is how a re-capture is classified before anything is written.
     */
    static SiapsReferenceManifest manifestOf(byte[] raw, NormalizedReference normalized, CaptureMetadata metadata) {
        return new SiapsReferenceManifest(
                metadata.referenceId(),
                normalized.sourceKind(),
                normalized.municipalityIbge(),
                normalized.quadrimestre(),
                metadata.capturedAt(),
                metadata.officialGeneratedAt(),
                normalized.officialStatus(),
                metadata.sourceDescription(),
                metadata.sourceFilename(),
                SummaryWriter.sha256(raw),
                normalized.sha256(),
                normalized.parserVersion(),
                normalized.rows().size(),
                List.of(normalized.indicatorCode()),
                normalized.teamTypes(),
                false,
                metadata.siblingReferenceIds());
    }

    /** The directory of the revision a manifest describes. */
    Path directoryOf(SiapsReferenceManifest manifest) {
        return artifactDir
                .resolve(manifest.municipalityIbge())
                .resolve(manifest.quadrimestre())
                .resolve(manifest.normalizedSha256());
    }

    /**
     * Stores a revision and returns its manifest. When the same normalized content is already
     * stored (a re-download whose raw bytes differ only in the generation timestamp, say) nothing is
     * written and the manifest of the revision that is there is returned: the first capture stays
     * the record of it.
     *
     * @throws IllegalStateException when the directory of this hash exists but does not hold exactly
     *     this content: it is never overwritten
     */
    SiapsReferenceManifest store(byte[] raw, NormalizedReference normalized, CaptureMetadata metadata)
            throws IOException {
        SiapsReferenceManifest manifest = manifestOf(raw, normalized, metadata);
        Path revision = directoryOf(manifest);
        byte[] normalizedBytes = normalized.canonicalBytes();
        if (Files.exists(revision)) {
            return alreadyStored(revision, normalizedBytes, manifest);
        }
        publish(revision, raw, normalizedBytes, manifest);
        return manifest;
    }

    private SiapsReferenceManifest alreadyStored(Path revision, byte[] normalizedBytes, SiapsReferenceManifest captured)
            throws IOException {
        Path content = revision.resolve(NORMALIZED);
        Path card = revision.resolve(MANIFEST);
        if (!Files.isRegularFile(content)
                || !Files.isRegularFile(card)
                || !Arrays.equals(Files.readAllBytes(content), normalizedBytes)) {
            throw new IllegalStateException("the store holds something else under " + captured.normalizedSha256()
                    + "; it is never overwritten");
        }
        SiapsReferenceManifest registered =
                SiapsReferenceManifest.fromJson(Files.readString(card, StandardCharsets.UTF_8));
        if (ReferenceDrift.compare(registered, captured) != ReferenceDrift.DriftStatus.SAME_REVISION) {
            throw new IllegalStateException("the stored manifest is not that of the revision it is stored as");
        }
        load(registered);
        return registered;
    }

    private static void publish(Path revision, byte[] raw, byte[] normalizedBytes, SiapsReferenceManifest manifest)
            throws IOException {
        Path parent = revision.getParent();
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, STAGING);
        try {
            Files.write(staging.resolve(RAW), raw);
            Files.write(staging.resolve(NORMALIZED), normalizedBytes);
            Files.writeString(staging.resolve(MANIFEST), manifest.toJson(), StandardCharsets.UTF_8);
            Files.move(staging, revision, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            discardQuietly(staging);
        }
    }

    /** Removes what is left of a staging directory whose move did not happen. */
    private static void discardQuietly(Path staging) {
        if (!Files.exists(staging)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(staging)) {
            for (Path file : files) {
                Files.delete(file);
            }
            Files.delete(staging);
        } catch (IOException leftover) {
            // best effort: nothing reads a ".staging-" directory, so what stays behind is harmless
        }
    }

    /**
     * The normalized content of a stored revision, after re-verifying it: the hash of the raw file
     * and the hash of the normalized content must be the manifest's, and the content must say the
     * same municipality, quadrimestre, source, layout, status, indicator, team types and row count.
     *
     * @throws IllegalStateException on any mismatch: a revision that is not what its manifest says
     *     is not evidence
     */
    NormalizedReference load(SiapsReferenceManifest expected) throws IOException {
        Path revision = directoryOf(expected);
        byte[] raw = Files.readAllBytes(revision.resolve(RAW));
        byte[] normalizedBytes = Files.readAllBytes(revision.resolve(NORMALIZED));
        requireHash(expected.rawSha256(), SummaryWriter.sha256(raw), "raw");
        requireHash(expected.normalizedSha256(), SummaryWriter.sha256(normalizedBytes), "normalized");
        NormalizedReference normalized = NormalizedReference.fromJson(normalizedBytes);
        if (!describes(expected, normalized)) {
            throw new IllegalStateException(
                    "the stored content of " + expected.referenceId() + " is not what its manifest says");
        }
        return normalized;
    }

    private static void requireHash(String expected, String actual, String what) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("the " + what + " file of the stored reference does not match its hash");
        }
    }

    /** True when {@code normalized} is the content {@code manifest} describes, byte for byte. */
    private static boolean describes(SiapsReferenceManifest manifest, NormalizedReference normalized) {
        return manifest.municipalityIbge().equals(normalized.municipalityIbge())
                && manifest.quadrimestre().equals(normalized.quadrimestre())
                && manifest.sourceKind() == normalized.sourceKind()
                && manifest.parserVersion().equals(normalized.parserVersion())
                && manifest.officialStatus() == normalized.officialStatus()
                && manifest.indicatorCodes().equals(List.of(normalized.indicatorCode()))
                && manifest.rowCount() == normalized.rows().size()
                && manifest.teamTypes().equals(normalized.teamTypes())
                && manifest.normalizedSha256().equals(normalized.sha256());
    }
}
