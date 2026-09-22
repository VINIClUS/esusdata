package esusdata.source.pec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Development-only {@link PecSecretResolver}: reads {@code KEY=value} pairs from a single
 * {@code 0600} file outside the repository (Tech Spec §1.12.7: "armazenamento protegido pelo SO
 * ou segredo cifrado com chave separada"). This is the dev-environment equivalent named in
 * ADR 0002/0003 — {@code ~/.config/observatorio-aps/pec.env}. A production deployment replaces
 * this resolver with an OS-keystore or secret-manager-backed implementation; nothing else in
 * {@code source-connector} depends on how the secret is stored.
 */
public final class EnvFileSecretResolver implements PecSecretResolver {

    private final Path envFile;

    public EnvFileSecretResolver(Path envFile) {
        this.envFile = envFile;
    }

    @Override
    public char[] resolve(String secretRef) {
        validateSecretFile();
        Map<String, String> values = new HashMap<>();
        try {
            try (var input = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(envFile, LinkOption.NOFOLLOW_LINKS), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int i = line.indexOf('=');
                if (i > 0) {
                    String key = line.substring(0, i).trim();
                    String value = line.substring(i + 1);
                    values.put(key, value);
                }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read secret file " + envFile, e);
        }
        String value = values.get(secretRef);
        if (value == null) {
            throw new IllegalStateException("No value for secretRef=" + secretRef + " in " + envFile);
        }
        return value.toCharArray();
    }

    private void validateSecretFile() {
        if (envFile == null) {
            throw new IllegalStateException("Secret file path is required");
        }
        if (Files.isSymbolicLink(envFile)) {
            throw new IllegalStateException("Secret file must not be a symbolic link: " + envFile);
        }
        if (!Files.isRegularFile(envFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Secret file must be a regular file: " + envFile);
        }
        try {
            PosixFileAttributes attributes = Files.readAttributes(
                    envFile, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Set<PosixFilePermission> permissions = attributes.permissions();
            if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                    || permission.name().startsWith("OTHERS_"))) {
                throw new IllegalStateException(
                        "Secret file permissions must be owner-only (0600 or stricter): " + envFile);
            }
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException(
                    "Cannot verify secret file permissions on this filesystem: " + envFile, e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect secret file permissions: " + envFile, e);
        }
    }
}
