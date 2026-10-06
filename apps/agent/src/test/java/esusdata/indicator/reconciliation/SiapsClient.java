package esusdata.indicator.reconciliation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Developer tooling only, never reachable from the product: the anonymous, read-only calls the
 * public SIAPS page itself makes, bundled as one snapshot ({@link SiapsParser#snapshot}). One
 * snapshot is {@code 1 + 1 + 7} requests: the competências, the quality result of the municipality
 * and quadrimestre, and the team list of each of C1–C7. There is no loop over municipalities.
 */
public final class SiapsClient {

    static final String BASE = "https://apisiaps.saude.gov.br/";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    /** The raw answer of {@code filtros/competencias}. */
    public String competencias() throws IOException {
        return get("api/public/filtros/competencias");
    }

    /**
     * The snapshot of one municipality and quadrimestre.
     *
     * @param uf the state's two-letter code, e.g. {@code SP}
     * @param ibge6 the SIAPS municipality code (6 digits)
     * @param quadrimestre the SIAPS spelling, e.g. {@code 2026Q2}
     */
    public String snapshot(String uf, String ibge6, String quadrimestre) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("competencias", MAPPER.readTree(competencias()));
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("uf").add(uf);
        body.putArray("nuQuadrimestre").add(quadrimestre);
        body.putArray("coMunicipioIbge").add(ibge6);
        root.set(
                "filtro",
                MAPPER.readTree(post("api/public/componente/indicador-quadrimestre/filtro", body.toString())));
        ObjectNode teams = root.putObject("equipes");
        for (GatePack pack : GatePack.all()) {
            teams.set(
                    Integer.toString(pack.siapsCode()),
                    MAPPER.readTree(get(
                            "api/public/filtros/equipes?indicadores=" + pack.siapsCode() + "&municipioIbge=" + ibge6)));
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private String get(String path) throws IOException {
        return send(
                HttpRequest.newBuilder(URI.create(BASE + path)).timeout(TIMEOUT).GET());
    }

    private String post(String path, String json) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    private String send(HttpRequest.Builder request) throws IOException {
        try {
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("SIAPS answered " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading the SIAPS", e);
        }
    }
}
