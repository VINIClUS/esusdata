package esusdata.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.auth.model.Role;
import esusdata.auth.model.ScopeDeniedException;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.IndicatorResult;
import esusdata.result.model.EvidenceEntry;
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
 * ENG-04/ENG-38 at the HTTP level. Three isolation axes, each proven against the real
 * {@code ScopeResolver}/{@code GrantRevalidator} the running app uses — nothing stubbed:
 * município A × B, INE × INE within the same município (evidence narrowing), and a team-scoped
 * grant refused on the municipal aggregate.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ScopeIsolationApiTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY_A = "3541307";
    private static final String MUNICIPALITY_B = "3550308";

    @Test
    void aManagerInMunicipalityACannotReadMunicipalityBsResults() throws Exception {
        String manager = createUser("manager-a-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY_A);
        publishResult(manager, MUNICIPALITY_A, "2026-03", computedResult(MUNICIPALITY_A), List.of());

        HttpResponse<String> ownMunicipality = getResults(manager, MUNICIPALITY_A);
        assertThat(ownMunicipality.statusCode()).isEqualTo(200);
        assertThat(ownMunicipality.body()).contains(MUNICIPALITY_A);

        HttpResponse<String> otherMunicipality = getResults(manager, MUNICIPALITY_B);
        assertThat(otherMunicipality.statusCode()).isEqualTo(404);
        assertThat(otherMunicipality.body()).contains("NOT_FOUND");
    }

    @Test
    void evidenceIsNarrowedToTheCallersOwnTeamWithinTheSameMunicipality() throws Exception {
        String manager = createUser("publisher-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY_A);
        String resultId = publishResult(
                manager,
                MUNICIPALITY_A,
                "2026-04",
                computedResult(MUNICIPALITY_A),
                List.of(
                        entry("rec-team-1", "0000346268"),
                        entry("rec-team-2", "0000346268"),
                        entry("rec-team-9", "0000999999")));

        String teamUser = createUser("team-1-" + System.nanoTime());
        grantTeam(teamUser, Role.TEAM_SCOPED_PROFESSIONAL, MUNICIPALITY_A, "2750325", "0000346268");

        HttpResponse<String> evidence = getEvidence(teamUser, resultId, MUNICIPALITY_A);

        assertThat(evidence.statusCode()).isEqualTo(200);
        assertThat(evidence.body()).contains("rec-team-1").contains("rec-team-2");
        assertThat(evidence.body()).doesNotContain("rec-team-9");
    }

    @Test
    void aTeamScopedGrantIsRefusedOnTheMunicipalAggregate() throws Exception {
        String manager = createUser("publisher-b-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY_A);
        // Same referencePeriod the query below uses ("2026-03") — proves the 404 comes from the
        // authorization gate itself, not from an unrelated "authorized but no match" empty-list
        // 200 that would happen to look similar with a mismatched period.
        publishResult(manager, MUNICIPALITY_A, "2026-03", computedResult(MUNICIPALITY_A), List.of());

        String teamUser = createUser("team-2-" + System.nanoTime());
        grantTeam(teamUser, Role.TEAM_SCOPED_PROFESSIONAL, MUNICIPALITY_A, "2750325", "0000346268");

        HttpResponse<String> aggregate = getResults(teamUser, MUNICIPALITY_A);

        assertThat(aggregate.statusCode()).isEqualTo(404);
        assertThat(aggregate.body()).contains("NOT_FOUND");
    }

    @Test
    void anInstallationOnlyAuditGrantCannotReadEvidence() throws Exception {
        // AUDITOR carries both `audit` and `read_clinical` (ADR 0007) — but `read_clinical` is
        // NOT installation-eligible, so an INSTALLATION-scoped AUDITOR grant must not leak into
        // evidence access. This denies at requireAnyMunicipalScope, before
        // resolveEvidenceTeamFilter is ever reached — see the next test for that method's own
        // fail-closed behavior.
        String manager = createUser("publisher-c-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY_A);
        String resultId = publishResult(
                manager,
                MUNICIPALITY_A,
                "2026-03",
                computedResult(MUNICIPALITY_A),
                List.of(entry("rec-audit-1", "0000346268")));

        String auditor = createUser("auditor-installation-" + System.nanoTime());
        grantInstallation(auditor, Role.AUDITOR);

        HttpResponse<String> evidence = getEvidence(auditor, resultId, MUNICIPALITY_A);

        assertThat(evidence.statusCode()).isEqualTo(404);
        assertThat(evidence.body()).contains("NOT_FOUND");
    }

    /**
     * Codex P1 on PR #4: {@code requireAnyMunicipalScope} and {@code resolveEvidenceTeamFilter}
     * are two SEPARATE database reads. If the caller's only matching grant is revoked in the
     * (however small) window between them, the old code fell through to
     * {@code TeamScopeFilter.unrestricted()} — turning a just-lost authorization into "show every
     * team's evidence" instead of denying it. Simulates the race deterministically by revoking the
     * grant between the two real calls, rather than by chance under real concurrency.
     */
    @Test
    void resolveEvidenceTeamFilterFailsClosedWhenTheMatchingGrantIsGoneByTheSecondRead() throws Exception {
        String manager = createUser("publisher-d-" + System.nanoTime());
        grantMunicipality(manager, Role.MANAGER, MUNICIPALITY_A);
        publishResult(manager, MUNICIPALITY_A, "2026-03", computedResult(MUNICIPALITY_A), List.of());

        String teamUser = createUser("team-race-" + System.nanoTime());
        grantTeam(teamUser, Role.TEAM_SCOPED_PROFESSIONAL, MUNICIPALITY_A, "2750325", "0000346268");
        var session = new esusdata.auth.model.AuthenticatedSession(
                "unused-session-id",
                teamUser,
                clock.instant(),
                clock.instant(),
                clock.instant().plusSeconds(3600),
                1,
                null);

        authorization.requireAnyMunicipalScope(session, esusdata.auth.model.Permission.READ_CLINICAL, MUNICIPALITY_A);

        String grantId = grantRepository.activeGrantsForUser(teamUser).get(0).grantId();
        grantRepository.revoke(grantId, clock.instant(), "simulated-race");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authorization.resolveEvidenceTeamFilter(
                        session, esusdata.auth.model.Permission.READ_CLINICAL, MUNICIPALITY_A))
                .isInstanceOf(ScopeDeniedException.class);
    }

    private IndicatorResult computedResult(String municipalityIbge) {
        return new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED,
                "60.0000",
                BigInteger.valueOf(3),
                BigInteger.valueOf(5),
                "PROGRAMADOS_MAIS_ESPONTANEOS",
                Classification.BOM,
                "2026-03",
                "c1-mais-acesso@0.1.0",
                "2026-03-31",
                municipalityIbge,
                List.of(),
                "c1-exact-ratio@1");
    }

    private EvidenceEntry entry(String recordId, String ine) {
        return new EvidenceEntry(
                "tb_fat_atendimento_individual",
                recordId,
                "2026-04-05",
                "PROGRAMADO",
                "2750325",
                ine,
                "225142",
                "IN_NUMERATOR",
                "c1@1");
    }

    private HttpResponse<String> getResults(String userId, String municipalityIbge) throws Exception {
        URI uri = URI.create(BASE_URL + "/api/v1/results?municipalityIbge=" + municipalityIbge
                + "&indicatorPack=c1-mais-acesso&referencePeriod=2026-03");
        return get(userId, uri);
    }

    private HttpResponse<String> getEvidence(String userId, String resultId, String municipalityIbge) throws Exception {
        URI uri =
                URI.create(BASE_URL + "/api/v1/results/" + resultId + "/evidence?municipalityIbge=" + municipalityIbge);
        return get(userId, uri);
    }

    private HttpResponse<String> get(String userId, URI uri) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        return client.send(
                HttpRequest.newBuilder(uri)
                        .header("Cookie", sessionCookie(userId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
