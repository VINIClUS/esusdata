package br.gov.observatorioaps.sourceconnector.infrastructure.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvFileSecretResolverTest {

    @TempDir
    Path dir;

    @Test
    void preservesHashCharactersInsideSecretValues() throws IOException {
        Path file = writeSecretFile("DB_PASSWORD=P@ss#word1\n# comment\n");

        assertThat(new EnvFileSecretResolver(file).resolve("DB_PASSWORD"))
                .containsExactly("P@ss#word1".toCharArray());
    }

    @Test
    void rejectsGroupOrWorldReadableSecretFiles() throws IOException {
        Path file = writeSecretFile("DB_PASSWORD=secret\n");
        Files.setPosixFilePermissions(file, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ));

        assertThatThrownBy(() -> new EnvFileSecretResolver(file).resolve("DB_PASSWORD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permissions");
    }

    @Test
    void rejectsSymlinkedSecretFiles() throws IOException {
        Path target = writeSecretFile("DB_PASSWORD=secret\n");
        Path link = dir.resolve("linked.env");
        Files.createSymbolicLink(link, target.getFileName());

        assertThatThrownBy(() -> new EnvFileSecretResolver(link).resolve("DB_PASSWORD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("symbolic link");
    }

    @Test
    void rejectsNonRegularSecretFiles() {
        assertThatThrownBy(() -> new EnvFileSecretResolver(dir).resolve("DB_PASSWORD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("regular file");
    }

    private Path writeSecretFile(String content) throws IOException {
        Path file = dir.resolve("pec.env");
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
        return file;
    }
}
