package esusdata.run.extract;

import esusdata.source.pec.SourceBudgetExceededException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Static file-publication mechanics shared by {@link ExtractWriter} (the JDBC path, which writes
 * its own data file byte-by-byte) and {@link DelegatedExtractPublication} (the execution-plane
 * path, fatia 3 / ADR 0011, whose data file is written by the Rust child) — owner-only file
 * creation, atomic hard-link publication, directory fsync, free-space reservation, and the
 * manifest-argument invariants are the same mechanics regardless of who produced the data file's
 * bytes. There is one implementation of each, not two.
 */
final class ExtractPublication {

    private ExtractPublication() {}

    static void validateManifestArguments(
            String sourceId,
            String municipalityIbge,
            String periodStart,
            String periodEndExclusive,
            Instant startedAt,
            String sourceZoneId,
            String queryChecksum,
            String adapterVersion,
            String completenessStatus,
            String consistencyLevel) {
        requireText(sourceId, "sourceId");
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException("municipalityIbge must be a 7-digit IBGE code");
        }
        validatePeriod(periodStart, periodEndExclusive);
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt is required");
        }
        requireText(sourceZoneId, "sourceZoneId");
        if (!ExtractValidation.isSha256Digest(queryChecksum)) {
            throw new IllegalArgumentException("queryChecksum must be a SHA-256 digest");
        }
        requireText(adapterVersion, "adapterVersion");
        if (!"COMPLETE".equals(completenessStatus)) {
            throw new IllegalArgumentException("only COMPLETE extracts may be published");
        }
        if (!"SNAPSHOT".equals(consistencyLevel)) {
            throw new IllegalArgumentException("only SNAPSHOT extracts may be published");
        }
        try {
            java.time.ZoneId.of(sourceZoneId);
        } catch (RuntimeException e) { // NOPMD - unknown zone or null, converted with its cause
            throw new IllegalArgumentException("sourceZoneId is invalid", e);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void validatePeriod(String periodStart, String periodEndExclusive) {
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(periodStart);
            end = LocalDate.parse(periodEndExclusive);
        } catch (RuntimeException e) { // NOPMD - parse failure or null, converted with its cause
            throw new IllegalArgumentException("period must use ISO local dates", e);
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("period end must be after period start");
        }
    }

    static void writeAndForce(Path path, byte[] bytes) throws IOException {
        createOwnerOnlyFile(path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    static void createOwnerOnlyFile(Path path) throws IOException {
        PosixFileAttributeView posixView = Files.getFileAttributeView(path.getParent(), PosixFileAttributeView.class);
        if (posixView != null) {
            Files.createFile(
                    path,
                    PosixFilePermissions.asFileAttribute(
                            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } else {
            Files.createFile(path);
        }
    }

    /**
     * Publishes a new file without replacement semantics. {@link Files#move(Path, Path,
     * java.nio.file.StandardCopyOption...)} with {@code ATOMIC_MOVE} is allowed to replace an
     * existing target on common Unix providers even when {@code REPLACE_EXISTING} is absent. A
     * hard link creates the destination directory entry with create-new semantics; the source is
     * removed only after publication, so a retry can never mutate an already finalized extract.
     */
    static void publishNewFile(Path temporaryFile, Path finalFile) throws IOException {
        requirePublicationTargetAbsent(finalFile, "publication target");
        Files.createLink(finalFile, temporaryFile);
        Files.delete(temporaryFile);
    }

    static void requirePublicationTargetAbsent(Path path, String description) throws IOException {
        ExtractValidation.rejectSymbolicLink(path, description);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(path.toString());
        }
    }

    static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException | AccessDeniedException ignored) {
            // Directory fsync is unavailable on some platforms (notably Windows); file contents
            // were still forced, and real file-write failures have already propagated.
        }
    }

    static void ensureTempSpace(Path directory, long maxTempFileBytes) throws IOException {
        if (maxTempFileBytes < 0) {
            throw new SourceBudgetExceededException(
                    SourceBudgetExceededException.CODE + ": temporary extract byte ceiling exceeded");
        }
        long reserveBytes =
                maxTempFileBytes > Long.MAX_VALUE - 1_048_576L ? Long.MAX_VALUE : maxTempFileBytes + 1_048_576L;
        long usableBytes = Files.getFileStore(directory).getUsableSpace();
        if (usableBytes < reserveBytes) {
            throw new SourceBudgetExceededException(
                    SourceBudgetExceededException.CODE + ": insufficient free space for the temporary extract: "
                            + usableBytes + " < " + reserveBytes + " bytes reserved");
        }
    }
}
