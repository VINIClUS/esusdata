package br.gov.observatorioaps.sourceconnector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                int i = line.indexOf('=');
                if (i > 0) {
                    values.put(line.substring(0, i), line.substring(i + 1));
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
}
