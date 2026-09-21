package br.gov.observatorioaps.api.results;

import br.gov.observatorioaps.extractionstore.domain.CanonicalEncounter;
import br.gov.observatorioaps.extractionstore.domain.CanonicalModality;
import br.gov.observatorioaps.extractionstore.domain.SourceRef;
import br.gov.observatorioaps.identityaccess.domain.Role;
import br.gov.observatorioaps.indicatorengine.domain.IndicatorResult;
import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.observatorioaps.api.ApiFixtureSupport;

/**
 * §4.4 L1802: C1's portões A/B/D/E remain BLOCKED — the API must show that honestly (numerator/
 * denominator exact, value null, limitations listed) rather than collapsing it into a fake 0% or
 * dropping the result. This slice does not unblock any gate.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class BlockedResultRenderingTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";

    @Test
    void aBlockedC1ResultCarriesExactCountsAndLimitationsNeverAFakeZero() throws Exception {
        String manager = createUser("mgr-blocked-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY);

        List<CanonicalEncounter> encounters = List.of(
                encounter(0, CanonicalModality.PROGRAMADO),
                encounter(1, CanonicalModality.PROGRAMADO),
                encounter(2, CanonicalModality.ESPONTANEO));
        // Default overload uses ReleaseGates.knownIncomplete() — the real, current gate state.
        IndicatorResult blocked = C1Rule.compute(encounters, MUNICIPALITY, "2026-03", "2026-03-31");
        assertThat(blocked.status()).isEqualTo(IndicatorResult.IndicatorStatus.BLOCKED);

        publishResult(manager, MUNICIPALITY, "2026-03", blocked, List.of());

        String body = getResults(manager).body();

        assertThat(body).contains("\"status\":\"BLOCKED\"");
        assertThat(body).contains("\"value\":null");
        assertThat(body).contains("\"numerator\":\"2\"");
        assertThat(body).contains("\"denominator\":\"3\"");
        assertThat(body).contains("\"resultNature\":\"LOCAL_ESTIMATE\"");
        assertThat(body).contains("\"validationStatus\":\"NOT_VALIDATED\"");
        assertThat(body).contains("\"jobId\":\"job-");
        assertThat(body).contains("\"runId\":\"run-");
        assertThat(body).contains("\"extractionId\":\"ext-");
        assertThat(body).contains("\"adapterVersion\":\"test-adapter@1\"");
        assertThat(body).contains("\"calculationPolicyVersion\":\"c1-exact-ratio@1\"");
        assertThat(body).contains("\"inputFingerprint\":\"sha256:");
        // Never a disguised 0% — "value":null above is the honest state; a JSON number 0 here
        // would be exactly the silent-zero failure §4.4 L1802 forbids.
        assertThat(body).doesNotContain("\"value\":0");
        assertThat(body).contains("Portão A");
    }

    private CanonicalEncounter encounter(int seq, CanonicalModality modality) {
        return new CanonicalEncounter(
                new SourceRef("src-blocked", "tb_fat_atendimento_individual", "rec-" + seq),
                MUNICIPALITY, "2026-03-1" + seq, modality, "2750325", "0000346268", "225142");
    }

    private HttpResponse<String> getResults(String userId) throws Exception {
        URI uri = URI.create(BASE_URL + "/api/v1/results?municipalityIbge=" + MUNICIPALITY
                + "&indicatorPack=c1-mais-acesso&referencePeriod=2026-03");
        HttpClient client = HttpClient.newHttpClient();
        return client.send(
                HttpRequest.newBuilder(uri).header("Cookie", sessionCookie(userId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
