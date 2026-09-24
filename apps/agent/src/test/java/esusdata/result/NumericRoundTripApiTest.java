package esusdata.result;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.web.ApiFixtureSupport;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ENG-26: decimals and integers travel as canonical strings over HTTP, never JSON numbers, and
 * {@code value: null} is distinct from {@code value: "0"} (§1.10 L395).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class NumericRoundTripApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Test
    void numeratorDenominatorAndValueTravelAsQuotedStrings() throws Exception {
        String manager = createUser("mgr-numeric-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED,
                "60.0000",
                BigInteger.valueOf(3),
                BigInteger.valueOf(5),
                "PROGRAMADOS_MAIS_ESPONTANEOS",
                Classification.BOM,
                "2026-06",
                "c1-mais-acesso@0.1.0",
                "2026-06-30",
                MUNICIPALITY,
                List.of(),
                "c1-exact-ratio@1");
        publishResult(manager, MUNICIPALITY, "2026-06", result, List.of());

        String body = getResults(manager, "2026-06").body();

        assertThat(body).contains("\"value\":\"60.0000\"");
        assertThat(body).contains("\"numerator\":\"3\"");
        assertThat(body).contains("\"denominator\":\"5\"");
        // The requirement is "never a JSON number" — the positive assertions above would still
        // pass if a sibling field silently became numeric; these negatives are what actually
        // pins that.
        assertThat(body).doesNotContain("\"value\":60").doesNotContain("\"value\":60.0000");
        assertThat(body).doesNotContain("\"numerator\":3");
        assertThat(body).doesNotContain("\"denominator\":5");
    }

    @Test
    void aNoDenominatorResultCarriesValueNullNotTheStringZero() throws Exception {
        String manager = createUser("mgr-nodenom-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);
        IndicatorResult result = new IndicatorResult(
                IndicatorResult.IndicatorStatus.NO_DENOMINATOR,
                null,
                BigInteger.ZERO,
                BigInteger.ZERO,
                "PROGRAMADOS_MAIS_ESPONTANEOS",
                null,
                "2026-07",
                "c1-mais-acesso@0.1.0",
                "2026-07-31",
                MUNICIPALITY,
                List.of(),
                "c1-exact-ratio@1");
        publishResult(manager, MUNICIPALITY, "2026-07", result, List.of());

        String body = getResults(manager, "2026-07").body();

        assertThat(body).contains("\"value\":null");
        assertThat(body).doesNotContain("\"value\":\"0\"");
    }

    private HttpResponse<String> getResults(String userId, String referencePeriod) throws Exception {
        URI uri = URI.create(BASE_URL + "/api/v1/results?municipalityIbge=" + MUNICIPALITY
                + "&indicatorPack=c1-mais-acesso&referencePeriod=" + referencePeriod);
        HttpClient client = HttpClient.newHttpClient();
        return client.send(
                HttpRequest.newBuilder(uri)
                        .header("Cookie", sessionCookie(userId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
