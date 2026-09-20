package br.gov.observatorioaps.extractionstore;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Reconciles extract publication pairs after a process interruption. A finalized data file with a
 * staged manifest is completed only after the staged manifest and data contents pass the same
 * validation used for normal reads; any other incomplete publication is removed so its extraction
 * id can be retried safely.
 */
public final class ExtractRecovery {

    private static final String DATA_SUFFIX = ".jsonl.gz";
    private static final String DATA_TEMP_SUFFIX = ".jsonl.gz.tmp";
    private static final String MANIFEST_SUFFIX = ".manifest.json";
    private static final String MANIFEST_TEMP_SUFFIX = ".manifest.json.tmp";

    private ExtractRecovery() {
    }

    /**
     * Reconciles all recognizable extract ids in {@code baseDir}. Missing directories are treated
     * as empty; a symbolic-link base directory is rejected by the shared extraction boundary.
     */
    public static void reconcile(Path baseDir) throws IOException {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir is required");
        }
        if (!Files.exists(baseDir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(baseDir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("extract base directory is not a directory: " + baseDir);
        }

        Set<String> extractionIds = new HashSet<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(baseDir)) {
            for (Path entry : entries) {
                String extractionId = extractionIdFromFilename(entry.getFileName().toString());
                if (extractionId != null) {
                    ExtractValidation.validateExtractionId(baseDir, extractionId);
                    extractionIds.add(extractionId);
                }
            }
        } catch (AccessDeniedException ignored) {
            // A deployment may allow file creation/fsync without directory reads. In that case
            // reconciliation is deferred until a later startup with directory-read permission.
            return;
        }

        for (String extractionId : extractionIds) {
            reconcileOne(baseDir, extractionId);
        }
    }

    private static void reconcileOne(Path baseDir, String extractionId) throws IOException {
        Path dataFile = baseDir.resolve(extractionId + DATA_SUFFIX);
        Path dataTemp = baseDir.resolve(extractionId + DATA_TEMP_SUFFIX);
        Path manifestFile = baseDir.resolve(extractionId + MANIFEST_SUFFIX);
        Path manifestTemp = baseDir.resolve(extractionId + MANIFEST_TEMP_SUFFIX);

        rejectLinks(dataFile, dataTemp, manifestFile, manifestTemp);
        boolean dataExists = Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS);
        boolean manifestExists = Files.exists(manifestFile, LinkOption.NOFOLLOW_LINKS);
        boolean manifestTempExists = Files.exists(manifestTemp, LinkOption.NOFOLLOW_LINKS);

        if (manifestExists && dataExists) {
            if (manifestTempExists) delete(manifestTemp);
            return;
        }

        if (manifestExists) {
            delete(manifestFile);
            if (manifestTempExists) delete(manifestTemp);
            forceDirectory(baseDir);
            return;
        }

        if (dataExists && manifestTempExists) {
            try {
                ExtractionManifest manifest = readStagedManifest(manifestTemp, extractionId);
                ExtractValidation.validateManifest(manifest);
                if (!Files.isRegularFile(dataFile, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("Interrupted extract data file is not regular: " + dataFile);
                }
                new ExtractReader().readDataFile(dataFile, manifest);
                publishNewFile(manifestTemp, manifestFile);
                forceDirectory(baseDir);
            } catch (IOException | RuntimeException failure) {
                removePartialPublication(dataFile, manifestTemp, baseDir, failure);
            }
            return;
        }

        if (dataExists) delete(dataFile);
        if (manifestTempExists) delete(manifestTemp);
        // A data temp file is an active/incomplete writer artifact, not a finalized publication.
        // It is deliberately left for the existing orphan cleanup policy rather than deleting a
        // writer that may still be open in this process.
        if (dataExists || manifestTempExists) forceDirectory(baseDir);
    }

    private static ExtractionManifest readStagedManifest(Path manifestTemp, String extractionId)
            throws IOException {
        if (!Files.isRegularFile(manifestTemp, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Staged manifest is not a regular file: " + manifestTemp);
        }
        ExtractionManifest manifest;
        try (InputStream input = Files.newInputStream(manifestTemp, LinkOption.NOFOLLOW_LINKS)) {
            manifest = new ObjectMapper().readValue(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8), ExtractionManifest.class);
        }
        if (!extractionId.equals(manifest.extractionId())) {
            throw new IllegalStateException("Staged manifest extractionId does not match: " + extractionId);
        }
        return manifest;
    }

    private static void removePartialPublication(
            Path dataFile, Path manifestTemp, Path baseDir, Throwable failure) throws IOException {
        try {
            delete(dataFile);
            delete(manifestTemp);
            forceDirectory(baseDir);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            throw cleanupFailure;
        }
    }

    private static String extractionIdFromFilename(String filename) {
        for (String suffix : new String[]{
                DATA_SUFFIX, DATA_TEMP_SUFFIX, MANIFEST_SUFFIX, MANIFEST_TEMP_SUFFIX}) {
            if (filename.endsWith(suffix)) {
                String extractionId = filename.substring(0, filename.length() - suffix.length());
                return extractionId.isEmpty() ? null : extractionId;
            }
        }
        return null;
    }

    private static void rejectLinks(Path... paths) {
        for (Path path : paths) ExtractValidation.rejectSymbolicLink(path, "extract recovery path");
    }

    private static void delete(Path path) throws IOException {
        ExtractValidation.rejectSymbolicLink(path, "extract recovery path");
        Files.deleteIfExists(path);
    }

    private static void publishNewFile(Path temporaryFile, Path finalFile) throws IOException {
        ExtractValidation.rejectSymbolicLink(temporaryFile, "extract recovery temporary file");
        ExtractValidation.rejectSymbolicLink(finalFile, "extract recovery publication target");
        if (Files.exists(finalFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(finalFile.toString());
        }
        Files.createLink(finalFile, temporaryFile);
        Files.delete(temporaryFile);
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                directory, java.nio.file.StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException | java.nio.file.AccessDeniedException ignored) {
            // Directory fsync is unavailable on some platforms; file contents are still forced.
        }
    }
}
