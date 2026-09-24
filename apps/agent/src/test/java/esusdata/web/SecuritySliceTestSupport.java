package esusdata.web;

import java.net.CookieManager;
import java.net.http.HttpClient;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Fixed test port (not random): {@code observatorio.web.allowed-hosts}/{@code allowed-origins}
 * must be known before the context starts ({@code @DynamicPropertySource} runs before Tomcat
 * binds), so a real {@code RANDOM_PORT} can't be pre-declared into the allowlist it is then
 * checked against.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public abstract class SecuritySliceTestSupport {

    public static final int PORT = 18443;
    public static final String BASE_URL = "http://127.0.0.1:" + PORT;

    @TempDir
    public static Path dataDir;

    @DynamicPropertySource
    public static void props(DynamicPropertyRegistry registry) {
        // Any executable satisfies RunConfig's startup check; this context never acquires.
        registry.add(
                "observatorio.execution-plane.binary",
                () -> ProcessHandle.current().info().command().orElseThrow());
        registry.add("server.port", () -> PORT);
        registry.add("observatorio.data.directory", dataDir::toString);
        registry.add("observatorio.web.allowed-hosts", () -> "127.0.0.1:" + PORT);
        registry.add(
                "observatorio.web.allowed-origins", () -> BASE_URL + ",http://127.0.0.1:5173,http://localhost:5173");
        registry.add("observatorio.security.argon2-memory-kib", () -> "8");
        registry.add("observatorio.security.argon2-iterations", () -> "1");
    }

    public HttpClient newClientWithCookies() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }
}
