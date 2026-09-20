package br.gov.observatorioaps.api;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.CookieManager;
import java.net.http.HttpClient;
import java.nio.file.Path;

/**
 * Fixed test port (not random): {@code observatorio.web.allowed-hosts}/{@code allowed-origins}
 * must be known before the context starts ({@code @DynamicPropertySource} runs before Tomcat
 * binds), so a real {@code RANDOM_PORT} can't be pre-declared into the allowlist it is then
 * checked against.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
abstract class SecuritySliceTestSupport {

    static final int PORT = 18443;
    static final String BASE_URL = "http://127.0.0.1:" + PORT;

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("observatorio.data.directory", dataDir::toString);
        registry.add("observatorio.web.allowed-hosts", () -> "127.0.0.1:" + PORT);
        registry.add("observatorio.web.allowed-origins", () -> BASE_URL);
        registry.add("observatorio.security.argon2-memory-kib", () -> "8");
        registry.add("observatorio.security.argon2-iterations", () -> "1");
    }

    HttpClient newClientWithCookies() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }
}
