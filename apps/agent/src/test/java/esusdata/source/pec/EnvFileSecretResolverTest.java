package esusdata.source.pec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;
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
    @DisabledOnOs(OS.WINDOWS)
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

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void acceptsSecretFilesReadableBySystemOnNtfs() throws IOException {
        Path file = writeSecretFile("DB_PASSWORD=secret\n");
        grantRead(file, principal("NT AUTHORITY\\SYSTEM"));

        assertThat(new EnvFileSecretResolver(file).resolve("DB_PASSWORD"))
                .containsExactly("secret".toCharArray());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsSecretFilesReadableByOtherPrincipalsOnNtfs() throws IOException {
        Path file = writeSecretFile("DB_PASSWORD=secret\n");
        // A principal whose English name resolves on any Windows display language.
        grantRead(file, principal("NT AUTHORITY\\LocalService"));

        assertThatThrownBy(() -> new EnvFileSecretResolver(file).resolve("DB_PASSWORD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can read it");
    }

    private Path writeSecretFile(String content) throws IOException {
        Path file = dir.resolve("pec.env");
        Files.writeString(file, content);
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } else {
            Files.getFileAttributeView(file, AclFileAttributeView.class).setAcl(List.of(
                    allow(Files.getOwner(file), Set.of(AclEntryPermission.values()))));
        }
        return file;
    }

    private static void grantRead(Path file, UserPrincipal principal) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        List<AclEntry> acl = new ArrayList<>(view.getAcl());
        acl.add(allow(principal, Set.of(AclEntryPermission.READ_DATA)));
        view.setAcl(acl);
    }

    private static AclEntry allow(UserPrincipal principal, Set<AclEntryPermission> permissions) {
        return AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(principal).setPermissions(permissions).build();
    }

    private UserPrincipal principal(String name) throws IOException {
        return dir.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(name);
    }
}
