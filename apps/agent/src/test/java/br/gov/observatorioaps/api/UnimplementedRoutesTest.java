package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.10.1 L409/L1788: "importações/exportações só são expostas quando implementadas" — and the
 * refusal itself is tested. An unauthenticated probe never learns whether the route exists at all
 * (ENG-49: no bypass, no information leak before authentication); an authenticated probe gets a
 * plain 404, not a 501 announcing an unimplemented feature.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnimplementedRoutesTest extends ApiFixtureSupport {

    @Test
    void unauthenticatedProbesGet401NotA404ThatWouldLeakRouteExistence() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> imports = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/imports")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> exports = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/exports")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(imports.statusCode()).isEqualTo(401);
        assertThat(exports.statusCode()).isEqualTo(401);
    }

    @Test
    void authenticatedProbesGetAPlain404NeverA501() throws Exception {
        String user = createUser("auditor-" + System.nanoTime());
        grantMunicipality(user, Role.AUDITOR, "3541307");
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> imports = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/imports"))
                        .header("Cookie", sessionCookie(user)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> exports = client.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/api/v1/exports"))
                        .header("Cookie", sessionCookie(user)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(imports.statusCode()).isEqualTo(404);
        assertThat(exports.statusCode()).isEqualTo(404);
    }
}
