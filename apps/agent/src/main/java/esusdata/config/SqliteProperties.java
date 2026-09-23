package esusdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Canonical data directory for the Observatório's own persistence (§1.12.1). Never SMB/NFS in
 * production; this is not enforced here (the spec leaves that to deployment), but the directory
 * is always resolved to a local absolute path.
 */
@ConfigurationProperties(prefix = "observatorio.data")
public record SqliteProperties(String directory) {

    public Path resolvedDirectory() {
        String dir = (directory == null || directory.isBlank())
                ? System.getProperty("user.home") + "/.local/share/observatorio-aps"
                : directory;
        return Path.of(dir).toAbsolutePath().normalize();
    }

    public Path databaseFile() {
        return resolvedDirectory().resolve("observatorio.sqlite");
    }

    public Path lockFile() {
        return resolvedDirectory().resolve("observatorio.lock");
    }

    /**
     * Protected extract storage (§1.12.1: "Extratos locais protegidos"). Shares the data
     * directory's owner-only hardening (applied by {@link SqliteConfig}) rather than
     * introducing a separately configured location — one canonical data root per installation.
     */
    public Path extractsDirectory() {
        return resolvedDirectory().resolve("extracts");
    }
}
