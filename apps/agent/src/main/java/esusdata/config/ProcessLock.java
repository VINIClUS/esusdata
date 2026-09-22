package esusdata.config;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exclusive OS-level lock over the data directory, per Tech Spec §1.9.4: "Antes de migrations ou
 * recuperação, obter lock exclusivo do SO sobre o diretório de dados canônico e mantê-lo até
 * encerrar o serviço." A PID file is explicitly not proof of exclusion (§1.9.4) — this uses the
 * actual {@link FileLock} primitive, tested by acquiring twice.
 *
 * <p>This is a single-process-per-data-directory lock, not a distributed lease. It does not
 * coordinate across machines and does not claim to (§1.9.4: "O lock local não coordena outros
 * produtos/instalações").
 */
public final class ProcessLock implements AutoCloseable {

    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final FileLock lock;

    private ProcessLock(RandomAccessFile raf, FileChannel channel, FileLock lock) {
        this.raf = raf;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Acquires the lock or fails fast. Never blocks waiting for another holder — a second
     * instance against the same data directory must be refused immediately with a diagnostic,
     * not queued (§1.9.4).
     */
    public static ProcessLock acquireOrFail(Path lockFile) {
        RandomAccessFile raf = null;
        FileChannel channel = null;
        try {
            Files.createDirectories(lockFile.getParent());
            raf = new RandomAccessFile(lockFile.toFile(), "rw");
            channel = raf.getChannel();
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                raf.close();
                throw new ProcessLockUnavailableException(
                        "Data directory already locked by another running instance: " + lockFile
                                + ". Refusing to start a second process against the same data directory.");
            }
            return new ProcessLock(raf, channel, lock);
        } catch (OverlappingFileLockException e) {
            closeQuietly(channel, raf);
            throw new ProcessLockUnavailableException(
                    "Data directory already locked within this JVM: " + lockFile, e);
        } catch (IOException e) {
            closeQuietly(channel, raf);
            throw new ProcessLockUnavailableException(
                    "Could not acquire process lock on " + lockFile, e);
        }
    }

    private static void closeQuietly(FileChannel channel, RandomAccessFile raf) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() {
        try {
            lock.release();
        } catch (IOException ignored) {
            // Best effort on shutdown; the OS releases the lock when the process exits regardless.
        }
        try {
            channel.close();
            raf.close();
        } catch (IOException ignored) {
            // Same — shutdown path.
        }
    }

    public static final class ProcessLockUnavailableException extends RuntimeException {
        public ProcessLockUnavailableException(String message) {
            super(message);
        }

        public ProcessLockUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
