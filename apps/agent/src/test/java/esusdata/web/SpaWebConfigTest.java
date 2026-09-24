package esusdata.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * {@link SpaWebConfig} against src/test/resources/static (a stand-in for the {@code -Pweb} bundle):
 * client routes fall back to {@code index.html}, missing files and unknown API paths do not, and
 * the pages carry the web-client chain's headers without disturbing the API chain.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SpaWebConfigTest extends SecuritySliceTestSupport {

    private static final String INDEX_MARKER = "SpaWebConfigTest fixture";

    @Test
    void rootServesIndexRevalidatedAndWithTheWebClientHeaders() throws Exception {
        HttpResponse<String> root = get("/");

        assertThat(root.statusCode()).isEqualTo(200);
        assertThat(root.body()).contains(INDEX_MARKER);
        assertThat(root.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
        assertThat(root.headers().firstValue("Cache-Control")).hasValue("no-cache");
        assertThat(root.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(
                        csp -> assertThat(csp).contains("default-src 'self'").contains("frame-ancestors 'none'"));
        assertThat(root.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(root.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    }

    @Test
    void clientRoutesFallBackToIndex() throws Exception {
        HttpResponse<String> deepLink = get("/indicadores/c1-mais-acesso");

        assertThat(deepLink.statusCode()).isEqualTo(200);
        assertThat(deepLink.body()).contains(INDEX_MARKER);
    }

    @Test
    void hashedAssetsAreCachedForeverAndMissingFilesAre404() throws Exception {
        HttpResponse<String> asset = get("/assets/app-abc123.js");

        assertThat(asset.statusCode()).isEqualTo(200);
        assertThat(asset.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(
                        cache -> assertThat(cache).contains("max-age=31536000").contains("immutable"));
        assertThat(get("/assets/missing-abc123.js").statusCode()).isEqualTo(404);
        assertThat(get("/favicon-missing.svg").statusCode()).isEqualTo(404);
    }

    @Test
    void pathTraversalOutOfTheBundleIsNotServed() throws Exception {
        HttpResponse<String> traversal = get("/assets/%2e%2e/%2e%2e/application.yml");

        assertThat(traversal.statusCode()).isIn(400, 404);
        assertThat(traversal.body()).doesNotContain("observatorio");
    }

    @Test
    void apiPathsStayOnTheApiChainAndNeverFallBackToIndex() throws Exception {
        HttpResponse<String> ready = get("/api/v1/ready");
        HttpResponse<String> unknown = get("/api/v1/nao-existe");

        assertThat(ready.statusCode()).isEqualTo(200);
        assertThat(ready.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(cache -> assertThat(cache).contains("no-store"));
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(unknown.body()).doesNotContain(INDEX_MARKER);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(BASE_URL + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
