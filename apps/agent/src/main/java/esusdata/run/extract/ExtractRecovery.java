package esusdata.run.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

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
    private static final String LOCK_SUFFIX = ".extract.lock";

    private ExtractRecovery() {}

    /**
     * Reconciles all recognizable extract ids in {@code baseDir}. Missing directories are treated
     * as empty; a symbolic-link base directory is rejected by the shared extraction boundary.
     */
    public static void reconcile(Path baseDir) throws IOException {
        reconcile(baseDir, null);
    }

    /**
     * Reconciles the directory while treating {@code ownedExtractionId} as locked by the caller.
     * ExtractWriter uses this form after acquiring that id's lock, so abandoned temporary data
     * can be removed without deleting a different writer's active temporary file.
     */
    static void reconcile(Path baseDir, String ownedExtractionId) throws IOException {
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
                String extractionId =
                        extractionIdFromFilename(entry.getFileName().toString());
                if (extractionId != null) {
                    ExtractValidation.validateExtractionId(baseDir, extractionId);
                    extractionIds.add(extractionId);
                }
            }
        } catch (AccessDeniedException ignored) {
            // A deployment may allow file creation/fsync without directory reads. In that case
            // reconcile the caller's known id by direct path and defer directory-wide cleanup
            // until a later startup with directory-read permission.
            if (ownedExtractionId != null) {
                ExtractValidation.validateExtractionId(baseDir, ownedExtractionId);
                reconcileOne(baseDir, ownedExtractionId);
            }
            return;
        }

        if (ownedExtractionId != null) {
            ExtractValidation.validateExtractionId(baseDir, ownedExtractionId);
            extractionIds.add(ownedExtractionId);
        }

        for (String extractionId : extractionIds) {
            if (extractionId.equals(ownedExtractionId)) {
                reconcileOne(baseDir, extractionId);
                continue;
            }
            try (WriterLock ignored = tryAcquireLock(baseDir, extractionId)) {
                if (ignored != null) {
                    reconcileOne(baseDir, extractionId);
                }
            }
        }
    }

    static WriterLock acquireWriterLock(Path baseDir, String extractionId) throws IOException {
        ExtractValidation.validateExtractionId(baseDir, extractionId);
        WriterLock lock = tryAcquireLock(baseDir, extractionId);
        if (lock == null) {
            throw new FileAlreadyExistsException(
                    baseDir.resolve(extractionId + LOCK_SUFFIX).toString(),
                    null,
                    "extract is already being written: " + extractionId);
        }
        return lock;
    }

    private static WriterLock tryAcquireLock(Path baseDir, String extractionId) throws IOException {
        Path lockPath = baseDir.resolve(extractionId + LOCK_SUFFIX);
        ExtractValidation.rejectSymbolicLink(lockPath, "extract writer lock");
        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    closeQuietly(channel);
                    return null;
                }
                return new WriterLock(channel, lock);
            } catch (OverlappingFileLockException e) {
                closeQuietly(channel);
                return null;
            }
        } catch (IOException e) {
            closeQuietly(channel);
            throw e;
        }
    }

    private static void reconcileOne(Path baseDir, String extractionId) throws IOException {
        Path dataFile = baseDir.resolve(extractionId + DATA_SUFFIX);
        Path dataTemp = baseDir.resolve(extractionId + DATA_TEMP_SUFFIX);
        Path manifestFile = baseDir.resolve(extractionId + MANIFEST_SUFFIX);
        Path manifestTemp = baseDir.resolve(extractionId + MANIFEST_TEMP_SUFFIX);

        rejectLinks(dataFile, dataTemp, manifestFile, manifestTemp);
        boolean dataExists = Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS);
        boolean dataTempExists = Files.exists(dataTemp, LinkOption.NOFOLLOW_LINKS);
        boolean manifestExists = Files.exists(manifestFile, LinkOption.NOFOLLOW_LINKS);
        boolean manifestTempExists = Files.exists(manifestTemp, LinkOption.NOFOLLOW_LINKS);

        if (manifestExists && dataExists) {
            if (manifestTempExists) {
                delete(manifestTemp);
            }
            if (dataTempExists) {
                delete(dataTemp);
            }
            if (manifestTempExists || dataTempExists) {
                forceDirectory(baseDir);
            }
            return;
        }

        if (manifestExists) {
            delete(manifestFile);
            if (manifestTempExists) {
                delete(manifestTemp);
            }
            if (dataTempExists) {
                delete(dataTemp);
            }
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
                if (dataTempExists) {
                    delete(dataTemp);
                }
                forceDirectory(baseDir);
            } catch (IOException | RuntimeException failure) {
                removePartialPublication(dataFile, dataTemp, manifestTemp, baseDir, failure);
            }
            return;
        }

        if (dataExists) {
            delete(dataFile);
        }
        if (manifestTempExists) {
            delete(manifestTemp);
        }
        if (dataTempExists) {
            delete(dataTemp);
        }
        if (dataExists || manifestTempExists || dataTempExists) {
            forceDirectory(baseDir);
        }
    }

    private static ExtractionManifest readStagedManifest(Path manifestTemp, String extractionId) throws IOException {
        if (!Files.isRegularFile(manifestTemp, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Staged manifest is not a regular file: " + manifestTemp);
        }
        ExtractionManifest manifest;
        try (InputStream input = Files.newInputStream(manifestTemp, LinkOption.NOFOLLOW_LINKS)) {
            manifest = new ObjectMapper()
                    .readValue(new String(input.readAllBytes(), StandardCharsets.UTF_8), ExtractionManifest.class);
        }
        if (!extractionId.equals(manifest.extractionId())) {
            throw new IllegalStateException("Staged manifest extractionId does not match: " + extractionId);
        }
        return manifest;
    }

    private static void removePartialPublication(
            Path dataFile, Path dataTemp, Path manifestTemp, Path baseDir, Throwable failure) throws IOException {
        try {
            delete(dataFile);
            delete(dataTemp);
            delete(manifestTemp);
            forceDirectory(baseDir);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            throw cleanupFailure;
        }
    }

    private static String extractionIdFromFilename(String filename) {
        for (String suffix : new String[] {DATA_SUFFIX, DATA_TEMP_SUFFIX, MANIFEST_SUFFIX, MANIFEST_TEMP_SUFFIX}) {
            if (filename.endsWith(suffix)) {
                String extractionId = filename.substring(0, filename.length() - suffix.length());
                return extractionId.isEmpty() ? null : extractionId;
            }
        }
        return null;
    }

    private static void rejectLinks(Path... paths) {
        for (Path path : paths) {
            ExtractValidation.rejectSymbolicLink(path, "extract recovery path");
        }
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
        try (java.nio.channels.FileChannel channel =
                java.nio.channels.FileChannel.open(directory, java.nio.file.StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException | java.nio.file.AccessDeniedException ignored) {
            // Directory fsync is unavailable on some platforms; file contents are still forced.
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Best-effort cleanup: callers are already returning or failing on their own terms.
            }
        }
    }

    static final class WriterLock implements AutoCloseable {

        private final FileChannel channel;
        private final FileLock lock;

        private WriterLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            try {
                lock.release();
            } catch (IOException ignored) {
                // Closing the channel below releases the lock anyway.
            }
            closeQuietly(channel);
        }
    }
}
