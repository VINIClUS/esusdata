package esusdata.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * §1.10.1 "documentar contratos em OpenAPI na implementação". Compares
 * {@code contracts/openapi/observatorio-v1.yaml} against what Spring actually registered, in BOTH
 * directions, on two levels: the route (path+method) set, and — per route — the set of path/query
 * parameter names. Deliberately stops there: request/response SCHEMAS and status codes are not
 * checked, since validating those against the DTOs would make this test as brittle as the DTOs
 * themselves change shape. A route with no YAML entry, a YAML entry with no registered route, or a
 * query/path parameter present on only one side, are equally a broken contract; a drifted response
 * schema is not caught here and remains a documentation-only claim.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiContractTest extends SecuritySliceTestSupport {

    /**
     * Boot's own default error-handling route — never part of THIS application's documented
     * surface, and {@code produces}/{@code methods} conditions are empty on it (matches "any"),
     * so it would never usefully round-trip through a YAML comparison anyway.
     */
    private static final String EXCLUDED_PATH = "/error";

    private static final Path CONTRACT_PATH =
            Path.of("../../contracts/openapi/observatorio-v1.yaml").normalize();

    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    @Test
    void everyRegisteredRouteIsDocumentedAndEveryDocumentedRouteIsRegistered() {
        Map<String, HandlerMethod> registered = registeredRoutes();
        Map<String, Set<ParamRef>> documented = documentedRoutes();

        assertThat(registered.keySet())
                .as("routes Spring registered but contracts/openapi/observatorio-v1.yaml does not document")
                .isSubsetOf(documented.keySet());
        assertThat(documented.keySet())
                .as("routes contracts/openapi/observatorio-v1.yaml documents but Spring never registered")
                .isSubsetOf(registered.keySet());
    }

    @Test
    void everyRoutesQueryAndPathParametersMatchBetweenSpringAndTheContract() {
        Map<String, HandlerMethod> registered = registeredRoutes();
        Map<String, Set<ParamRef>> documented = documentedRoutes();

        for (String route : registered.keySet()) {
            if (!documented.containsKey(route)) {
                continue; // already failed by the route-set test above; do not double-report here
            }
            Set<ParamRef> actual = handlerParams(registered.get(route));
            Set<ParamRef> declared = documented.get(route);
            assertThat(actual).as("query/path parameters of %s", route).isEqualTo(declared);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void runCreationDeclaresIdempotencyKeyAsRequired() throws Exception {
        Map<String, Object> document;
        try (InputStream in = Files.newInputStream(CONTRACT_PATH)) {
            document = new Yaml().load(in);
        }
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> runs = (Map<String, Object>) paths.get("/api/v1/runs");
        Map<String, Object> post = (Map<String, Object>) runs.get("post");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) post.get("parameters");
        Map<String, Object> idempotencyKey = parameters.stream()
                .filter(parameter -> "Idempotency-Key".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Idempotency-Key parameter is missing"));

        assertThat(idempotencyKey.get("required")).isEqualTo(true);
    }

    private static Set<ParamRef> handlerParams(HandlerMethod handlerMethod) {
        Set<ParamRef> params = new TreeSet<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            parameter.initParameterNameDiscovery(PARAMETER_NAMES);
            PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
            if (pathVariable != null) {
                params.add(new ParamRef("path", resolveName(pathVariable.value(), pathVariable.name(), parameter)));
                continue;
            }
            RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            if (requestParam != null) {
                params.add(new ParamRef("query", resolveName(requestParam.value(), requestParam.name(), parameter)));
            }
        }
        return params;
    }

    private static String resolveName(String value, String name, MethodParameter parameter) {
        if (!value.isBlank()) {
            return value;
        }
        if (!name.isBlank()) {
            return name;
        }
        return parameter.getParameterName();
    }

    private Map<String, HandlerMethod> registeredRoutes() {
        Map<String, HandlerMethod> routes = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            Set<String> patterns = new LinkedHashSet<>();
            info.getPathPatternsCondition().getPatternValues().forEach(patterns::add);
            for (String pattern : patterns) {
                if (EXCLUDED_PATH.equals(pattern)) {
                    continue;
                }
                for (org.springframework.web.bind.annotation.RequestMethod method :
                        info.getMethodsCondition().getMethods()) {
                    routes.put(method.name() + " " + pattern, entry.getValue());
                }
            }
        }
        return routes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<ParamRef>> documentedRoutes() {
        assertThat(Files.exists(CONTRACT_PATH))
                .as("expected an OpenAPI contract at %s", CONTRACT_PATH.toAbsolutePath())
                .isTrue();
        Map<String, Set<ParamRef>> routes = new TreeMap<>();
        try (InputStream in = Files.newInputStream(CONTRACT_PATH)) {
            Map<String, Object> document = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) document.getOrDefault("components", Map.of());
            Map<String, Object> componentParams = (Map<String, Object>) components.getOrDefault("parameters", Map.of());
            Map<String, Object> paths = (Map<String, Object>) document.get("paths");
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                Map<String, Object> operations = (Map<String, Object>) pathEntry.getValue();
                for (Map.Entry<String, Object> operationEntry : operations.entrySet()) {
                    String route = operationEntry.getKey().toUpperCase(Locale.ROOT) + " " + pathEntry.getKey();
                    Map<String, Object> operation = (Map<String, Object>) operationEntry.getValue();
                    routes.put(route, operationParams(operation, componentParams));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + CONTRACT_PATH, e);
        }
        return routes;
    }

    @SuppressWarnings("unchecked")
    private static Set<ParamRef> operationParams(Map<String, Object> operation, Map<String, Object> componentParams) {
        Set<ParamRef> params = new TreeSet<>();
        List<Object> declared = (List<Object>) operation.getOrDefault("parameters", List.of());
        for (Object entry : declared) {
            Map<String, Object> paramObject = (Map<String, Object>) entry;
            String ref = (String) paramObject.get("$ref");
            if (ref != null) {
                String refName = ref.substring(ref.lastIndexOf('/') + 1);
                paramObject = (Map<String, Object>) componentParams.get(refName);
            }
            String in = (String) paramObject.get("in");
            if (!"path".equals(in) && !"query".equals(in)) {
                continue; // headers (e.g. Idempotency-Key) are out of scope for this comparison
            }
            params.add(new ParamRef(in, (String) paramObject.get("name")));
        }
        return params;
    }

    private record ParamRef(String in, String name) implements Comparable<ParamRef> {
        @Override
        public int compareTo(ParamRef other) {
            int byIn = in.compareTo(other.in);
            return byIn == 0 ? name.compareTo(other.name) : byIn;
        }
    }
}
