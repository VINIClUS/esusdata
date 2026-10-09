package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c1.C1Pack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The build-time half of the reference policy, as {@code ReleaseGatesConsistencyTest} is for the
 * release-gate registry (ADR 0032): the loader checks the shape of the policy, and here it is
 * checked against the repository. Every declaration has its manifest under {@code
 * docs/indicadores/portoes/references/} with the SHA-256 it pins, naming the reference, municipality,
 * quadrimestre and source kind the declaration does, and a dossier under {@code
 * docs/indicadores/portoes/compatibilidade/} that agrees with it (spec §8.1 rules 3, 7 and 9).
 * Missing directories mean no evidence yet, which the seeded policy relies on.
 *
 * <p>The check itself is {@link #violations}: it runs on the repository below, and on synthetic
 * repositories in the tests after it, so that each of its rules is shown to fail.
 */
class ReferencePolicyConsistencyTest {

    private static final Path REPO = Path.of("..", "..");
    private static final Path POLICY = REPO.resolve("contracts/indicators/siaps-reference-policy.json");
    private static final Path SCHEMA = REPO.resolve("contracts/indicators/siaps-reference-policy.schema.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<PackDescriptor> PACKS = ReleaseGateRegistry.registeredPacks();
    private static final PackDescriptor C1 = new C1Pack().descriptor();

    private static final String GATE_ID = "sp-3541307-2026q1-c1-team-r1";
    private static final String DIAGNOSTIC_ID = "sp-3541307-2025q3-c1-agg-r1";

    private static final String REFERENCE_ID = "reference_id";
    private static final String MUNICIPALITY = "municipality_ibge";
    private static final String QUADRIMESTRE = "quadrimestre";
    private static final String SOURCE_KIND = "source_kind";
    private static final String TEAM_EXPORT = "OFFICIAL_TEAM_EXPORT_CSV";
    private static final String AGGREGATE = "PUBLIC_AGGREGATE";
    private static final Map<String, String> SOURCE_KIND_OF = Map.of(GATE_ID, TEAM_EXPORT, DIAGNOSTIC_ID, AGGREGATE);

    // ---- the check

    /**
     * What is wrong between the policy and the evidence under {@code repo}; empty when they agree.
     *
     * <ul>
     *   <li>the manifest of every declaration exists and has the pinned SHA-256 over its bytes;</li>
     *   <li>that manifest repeats the declaration's {@code reference_id}, {@code municipality_ibge},
     *       {@code quadrimestre} and {@code source_kind}: the policy is read by the id and the
     *       manifest holds the facts, so a pin to the wrong file would otherwise go unnoticed;</li>
     *   <li>a cited dossier exists and has the pinned SHA-256;</li>
     *   <li>any dossier that exists, cited or not, names its reference and pack, carries a decided
     *       verdict, and that verdict is the declared {@code compatibility} (rule 9);</li>
     *   <li>an {@code ACTIVE} reference whose dossier is {@code EXACT} or {@code
     *       EQUIVALENT_FOR_REFERENCE} is a {@code GATE} one (rule 7: the gate set is derived);</li>
     *   <li>no dossier is left without a declaration, or a compatible reference that was never
     *       declared would escape the gate set.</li>
     * </ul>
     */
    static List<String> violations(Path repo, ReferencePolicy policy) throws IOException {
        List<String> found = new ArrayList<>();
        Set<String> dossiers = new HashSet<>();
        for (ReferenceSet set : policy.referenceSets()) {
            for (ReferenceDeclaration declaration : set.references()) {
                dossiers.add(ReferencePolicy.dossierPath(declaration.referenceId(), set.pack()));
                found.addAll(manifestViolations(repo, declaration));
                found.addAll(dossierViolations(repo, set, declaration));
            }
        }
        found.addAll(orphanDossiers(repo, dossiers));
        return found;
    }

    private static List<String> manifestViolations(Path repo, ReferenceDeclaration declaration) throws IOException {
        String path = ReferencePolicy.manifestPath(declaration.referenceId());
        Path manifest = repo.resolve(path);
        if (!Files.isRegularFile(manifest)) {
            return List.of(declaration.referenceId() + ": the manifest " + path + " does not exist");
        }
        if (!sha256(manifest).equals(declaration.referenceManifestSha256())) {
            return List.of(
                    declaration.referenceId() + ": the manifest " + path + " changed since the policy pinned it");
        }
        return manifestFieldViolations(manifest, path, declaration);
    }

    private static List<String> manifestFieldViolations(Path manifest, String path, ReferenceDeclaration declaration) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(manifest);
        } catch (JacksonException e) {
            return List.of(declaration.referenceId() + ": the manifest " + path + " is not valid JSON ("
                    + e.getOriginalMessage() + ")");
        }
        String who = declaration.referenceId() + ": the manifest " + path;
        List<String> found = new ArrayList<>();
        disagreement(who, tree, REFERENCE_ID, declaration.referenceId()).ifPresent(found::add);
        disagreement(who, tree, MUNICIPALITY, declaration.municipalityIbge()).ifPresent(found::add);
        disagreement(who, tree, QUADRIMESTRE, declaration.quadrimestre()).ifPresent(found::add);
        disagreement(who, tree, SOURCE_KIND, declaration.sourceKind().name()).ifPresent(found::add);
        return found;
    }

    private static Optional<String> disagreement(String who, JsonNode manifest, String field, String declared) {
        String written = text(manifest, field);
        return declared.equals(written)
                ? Optional.empty()
                : Optional.of(
                        who + " says " + field + " \"" + written + "\" but the declaration says \"" + declared + "\"");
    }

    private static List<String> dossierViolations(Path repo, ReferenceSet set, ReferenceDeclaration declaration)
            throws IOException {
        String path = ReferencePolicy.dossierPath(declaration.referenceId(), set.pack());
        Path dossier = repo.resolve(path);
        String who = declaration.referenceId() + " of " + set.pack();
        List<String> found = new ArrayList<>();
        if (declaration.compatibilityEvidenceRef() != null && !Files.isRegularFile(dossier)) {
            found.add(who + ": the dossier " + path + " it cites does not exist");
        } else if (declaration.compatibilityEvidenceRef() != null
                && !sha256(dossier).equals(declaration.compatibilityEvidenceSha256())) {
            found.add(who + ": the dossier " + path + " changed since the policy pinned it");
        }
        if (Files.isRegularFile(dossier)) {
            found.addAll(verdictViolations(dossier, set, declaration, who));
        }
        return found;
    }

    private static List<String> verdictViolations(
            Path dossier, ReferenceSet set, ReferenceDeclaration declaration, String who) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(dossier);
        } catch (JacksonException e) {
            return List.of(who + ": the dossier is not valid JSON (" + e.getOriginalMessage() + ")");
        }
        List<String> found = new ArrayList<>();
        if (!declaration.referenceId().equals(text(tree, REFERENCE_ID))
                || !set.pack().equals(text(tree, "pack"))) {
            found.add(who + ": the dossier names reference_id " + text(tree, REFERENCE_ID) + " and pack "
                    + text(tree, "pack"));
        }
        Optional<ReferenceCompatibility> verdict = decidedVerdict(tree);
        if (verdict.isEmpty()) {
            found.add(who + ": the dossier carries no decided verdict (EXACT, EQUIVALENT_FOR_REFERENCE,"
                    + " INCOMPATIBLE or INCONCLUSIVE)");
        } else {
            found.addAll(agreement(who, declaration, verdict.get()));
        }
        return found;
    }

    private static List<String> agreement(
            String who, ReferenceDeclaration declaration, ReferenceCompatibility verdict) {
        List<String> found = new ArrayList<>();
        if (declaration.compatibility() != verdict) {
            found.add(who + ": the declaration says " + declaration.compatibility() + " but the dossier decided "
                    + verdict);
        }
        if (declaration.status() == ReferenceStatus.ACTIVE
                && verdict.authorizesGate()
                && declaration.purpose() != ReferencePurpose.GATE) {
            found.add(
                    who + ": the dossier decided " + verdict + " and the reference is ACTIVE, so it belongs to the gate"
                            + " set, yet it is still DIAGNOSTIC (spec 8.1 rule 7)");
        }
        return found;
    }

    private static List<String> orphanDossiers(Path repo, Set<String> declared) throws IOException {
        Path directory = repo.resolve(ReferencePolicy.DOSSIER_DIR);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> ReferencePolicy.DOSSIER_DIR + file.getFileName())
                    .filter(path -> path.endsWith(".json") && !declared.contains(path))
                    .sorted()
                    .map(path -> "the dossier " + path + " has no declaration in the policy: a compatible reference"
                            + " that is never declared would escape the gate set")
                    .toList();
        }
    }

    private static String text(JsonNode tree, String field) {
        JsonNode value = tree.path(field);
        return value.isString() ? value.stringValue() : "";
    }

    private static Optional<ReferenceCompatibility> decidedVerdict(JsonNode tree) {
        String name = text(tree, "verdict");
        return Stream.of(ReferenceCompatibility.values())
                .filter(verdict -> verdict.isDecided() && verdict.name().equals(name))
                .findFirst();
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK provides SHA-256", e);
        }
    }

    // ---- the repository

    @Test
    void thePolicyValidatesAgainstItsSchema() throws Exception {
        try (InputStream stream = Files.newInputStream(SCHEMA)) {
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(stream);

            assertThat(schema.validate(MAPPER.readTree(POLICY))).isEmpty();
        }
    }

    @Test
    void everyCompiledRuleHasItsSetAndEveryDeclarationIsBackedByEvidenceInTheRepository() throws Exception {
        ReferencePolicy policy = ReferencePolicy.load(POLICY, PACKS);

        assertThat(policy.referenceSets())
                .extracting(ReferenceSet::ruleVersion)
                .containsExactlyInAnyOrderElementsOf(
                        PACKS.stream().map(PackDescriptor::ruleVersion).toList());
        assertThat(violations(REPO, policy)).isEmpty();
    }

    /**
     * The contracts are dev tooling of the Portão D (apps/agent/pom.xml): the jar carries the
     * release-gate registry beside them, and not these. The control line shows the resource
     * directory is the one under test.
     */
    @Test
    void theSiapsContractsAreDevToolingAndDoNotShipInTheJar() {
        assertThat(getClass().getResource("/indicators/release-gates.json")).isNotNull();
        assertThat(getClass().getResource("/indicators/siaps-reference-policy.json"))
                .isNull();
        assertThat(getClass().getResource("/indicators/siaps-reference-policy.schema.json"))
                .isNull();
    }

    // ---- the check, on synthetic repositories

    private static ObjectNode base(String id, String sourceKind, String manifestSha) {
        Matcher shape = ReferenceDeclaration.ID.matcher(id);
        assertThat(shape.matches()).as("shape of %s", id).isTrue();
        return MAPPER.createObjectNode()
                .put(REFERENCE_ID, id)
                .put(QUADRIMESTRE, shape.group("year") + "Q" + shape.group("index"))
                .put(MUNICIPALITY, shape.group("ibge"))
                .put(SOURCE_KIND, sourceKind)
                .put("reference_manifest_sha256", manifestSha);
    }

    /** A GATE declaration of C1 whose dossier says EXACT. */
    private static ObjectNode gate(String id, String manifestSha, String dossierSha) {
        return decided(
                base(id, TEAM_EXPORT, manifestSha)
                        .put("purpose", "GATE")
                        .put("required", true)
                        .put("status", "ACTIVE"),
                "EXACT",
                dossierSha);
    }

    /** A DIAGNOSTIC declaration of C1 that no dossier has judged. */
    private static ObjectNode diagnostic(String id, String manifestSha) {
        return base(id, AGGREGATE, manifestSha)
                .put("purpose", "DIAGNOSTIC")
                .put("required", false)
                .put("status", "ACTIVE")
                .put("compatibility", "UNKNOWN")
                .putNull("compatibility_evidence_ref")
                .putNull("compatibility_evidence_sha256");
    }

    private static ObjectNode decided(ObjectNode node, String verdict, String dossierSha) {
        return node.put("compatibility", verdict)
                .put(
                        "compatibility_evidence_ref",
                        ReferencePolicy.dossierPath(node.get(REFERENCE_ID).asString(), C1.id()))
                .put("compatibility_evidence_sha256", dossierSha);
    }

    private static ReferencePolicy policyOf(ObjectNode... declarations) {
        ObjectNode root = MAPPER.createObjectNode().put("schema_version", "1");
        ObjectNode set = root.putArray("reference_sets")
                .addObject()
                .put("pack", C1.id())
                .put("rule_version", C1.ruleVersion())
                .put("check", ReferencePolicy.CHECK_DISTRIBUTION)
                .put("selection_policy", "ALL_REQUIRED");
        ArrayNode references = set.putArray("references");
        for (ObjectNode declaration : declarations) {
            references.add(declaration);
        }
        return ReferencePolicy.fromJson(root.toString(), List.of(C1));
    }

    private static String write(Path repo, String relative, String content) throws IOException {
        Path file = repo.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return sha256(file);
    }

    private static String dossier(String id, String pack, String verdict) {
        return MAPPER.createObjectNode()
                .put(REFERENCE_ID, id)
                .put("pack", pack)
                .put("verdict", verdict)
                .toString();
    }

    /** A manifest with the four facts the declaration of {@code id} repeats; the rest is not read here. */
    private static ObjectNode manifestOf(String id) {
        ObjectNode manifest = base(id, SOURCE_KIND_OF.get(id), "0".repeat(64));
        manifest.remove("reference_manifest_sha256");
        return manifest;
    }

    private static String manifestSha(Path repo, String id) throws IOException {
        return write(repo, ReferencePolicy.manifestPath(id), manifestOf(id).toString());
    }

    private static String dossierSha(Path repo, String id, String verdict) throws IOException {
        return write(repo, ReferencePolicy.dossierPath(id, C1.id()), dossier(id, C1.id(), verdict));
    }

    @Test
    void aRepositoryThatHoldsEveryPinnedFileIsConsistent(@TempDir Path repo) throws Exception {
        ReferencePolicy policy = policyOf(
                gate(GATE_ID, manifestSha(repo, GATE_ID), dossierSha(repo, GATE_ID, "EXACT")),
                diagnostic(DIAGNOSTIC_ID, manifestSha(repo, DIAGNOSTIC_ID)));

        assertThat(violations(repo, policy)).isEmpty();
    }

    @Test
    void missingDirectoriesMeanNoEvidenceYet(@TempDir Path repo) throws Exception {
        ReferencePolicy withoutReferences = policyOf();

        assertThat(repo).isEmptyDirectory();
        assertThat(violations(repo, withoutReferences)).isEmpty();
    }

    @Test
    void aMissingManifestIsReported(@TempDir Path repo) throws Exception {
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, "4".repeat(64)));

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(DIAGNOSTIC_ID, "the manifest", "does not exist");
    }

    @Test
    void aManifestWhoseBytesChangedIsReported(@TempDir Path repo) throws Exception {
        String pinned = manifestSha(repo, DIAGNOSTIC_ID);
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, pinned));
        write(repo, ReferencePolicy.manifestPath(DIAGNOSTIC_ID), "{\"reference_id\":\"edited\"}");

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(DIAGNOSTIC_ID, "the manifest", "changed since the policy pinned it");
    }

    @ParameterizedTest(name = "a manifest that says {0} is {1} is reported")
    @MethodSource("factsAManifestMayGetWrong")
    void aManifestThatDisagreesWithItsDeclarationIsReported(String field, String written, @TempDir Path repo)
            throws Exception {
        ObjectNode manifest = manifestOf(DIAGNOSTIC_ID).put(field, written);
        String pinned = write(repo, ReferencePolicy.manifestPath(DIAGNOSTIC_ID), manifest.toString());
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, pinned));

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(DIAGNOSTIC_ID, "the manifest", "says " + field + " \"" + written + "\"");
    }

    static Stream<Arguments> factsAManifestMayGetWrong() {
        return Stream.of(
                Arguments.of(REFERENCE_ID, "sp-3541307-2025q3-c1-agg-r2"),
                Arguments.of(MUNICIPALITY, "3550308"),
                Arguments.of(QUADRIMESTRE, "2025Q2"),
                Arguments.of(SOURCE_KIND, TEAM_EXPORT));
    }

    @ParameterizedTest(name = "a manifest without {0} is reported")
    @ValueSource(strings = {REFERENCE_ID, MUNICIPALITY, QUADRIMESTRE, SOURCE_KIND})
    void aManifestThatLeavesOutOneOfTheFourFactsIsReported(String field, @TempDir Path repo) throws Exception {
        ObjectNode manifest = manifestOf(DIAGNOSTIC_ID);
        manifest.remove(field);
        String pinned = write(repo, ReferencePolicy.manifestPath(DIAGNOSTIC_ID), manifest.toString());
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, pinned));

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(DIAGNOSTIC_ID, "says " + field + " \"\"");
    }

    @Test
    void aManifestThatIsNotJsonIsReported(@TempDir Path repo) throws Exception {
        String pinned = write(repo, ReferencePolicy.manifestPath(DIAGNOSTIC_ID), "not json at all");
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, pinned));

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(DIAGNOSTIC_ID, "the manifest", "is not valid JSON");
    }

    @Test
    void aGateDeclarationMustPointAtItsDossierAndMatchItsBytes(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, GATE_ID);
        ReferencePolicy pinnedWrongly = policyOf(gate(GATE_ID, manifest, "5".repeat(64)));
        String path = ReferencePolicy.dossierPath(GATE_ID, C1.id());

        assertThat(violations(repo, pinnedWrongly))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(path, "it cites does not exist");

        dossierSha(repo, GATE_ID, "EXACT");

        assertThat(violations(repo, pinnedWrongly))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(path, "changed since the policy pinned it");
    }

    @Test
    void aDeclarationThatDisagreesWithTheVerdictOfItsDossierIsReported(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        String dossierSha = dossierSha(repo, DIAGNOSTIC_ID, "INCOMPATIBLE");
        ReferencePolicy policy = policyOf(decided(diagnostic(DIAGNOSTIC_ID, manifest), "INCONCLUSIVE", dossierSha));

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains("the declaration says INCONCLUSIVE but the dossier decided INCOMPATIBLE");
    }

    @Test
    void anActiveReferenceWhoseDossierMakesItEligibleCannotStayDiagnostic(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        String dossierSha = dossierSha(repo, DIAGNOSTIC_ID, "EXACT");
        ReferencePolicy policy = policyOf(decided(diagnostic(DIAGNOSTIC_ID, manifest), "INCONCLUSIVE", dossierSha));

        assertThat(violations(repo, policy))
                .hasSize(2)
                .anySatisfy(violation -> assertThat(violation)
                        .contains("the declaration says INCONCLUSIVE but the dossier decided EXACT"))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("it belongs to the gate set, yet it is still DIAGNOSTIC (spec 8.1 rule 7)"));
    }

    @Test
    void aDossierThatTheDeclarationDoesNotCiteIsStillHeldToTheDeclaration(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        dossierSha(repo, DIAGNOSTIC_ID, "EQUIVALENT_FOR_REFERENCE");
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, manifest));

        assertThat(violations(repo, policy))
                .hasSize(2)
                .anySatisfy(violation -> assertThat(violation)
                        .contains("the declaration says UNKNOWN but the dossier decided EQUIVALENT_FOR_REFERENCE"))
                .anySatisfy(violation -> assertThat(violation).contains("(spec 8.1 rule 7)"));
    }

    @Test
    void aCompatibleReferenceThatIsNoLongerActiveMayStayDiagnostic(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        String dossierSha = dossierSha(repo, DIAGNOSTIC_ID, "EXACT");
        ReferencePolicy policy =
                policyOf(decided(diagnostic(DIAGNOSTIC_ID, manifest).put("status", "SUPERSEDED"), "EXACT", dossierSha));

        assertThat(violations(repo, policy)).isEmpty();
    }

    @Test
    void aDossierWithoutADeclarationIsReported(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        dossierSha(repo, GATE_ID, "EXACT");
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, manifest));

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains(ReferencePolicy.dossierPath(GATE_ID, C1.id()), "has no declaration in the policy");
    }

    @Test
    void onlyJsonFilesAreDossiers(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        write(repo, ReferencePolicy.DOSSIER_DIR + "sp-3541307-2025q3-c1-agg-r1-c1-mais-acesso.md", "# resumo");
        ReferencePolicy policy = policyOf(diagnostic(DIAGNOSTIC_ID, manifest));

        assertThat(violations(repo, policy)).isEmpty();
    }

    @Test
    void aDossierThatNamesAnotherReferenceOrPackIsReported(@TempDir Path repo) throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        String sha = write(
                repo,
                ReferencePolicy.dossierPath(DIAGNOSTIC_ID, C1.id()),
                dossier(GATE_ID, "c2-desenvolvimento-infantil", "INCONCLUSIVE"));
        ReferencePolicy policy = policyOf(decided(diagnostic(DIAGNOSTIC_ID, manifest), "INCONCLUSIVE", sha));

        assertThat(violations(repo, policy))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains("the dossier names reference_id " + GATE_ID + " and pack c2-desenvolvimento-infantil");
    }

    @ParameterizedTest
    @MethodSource("dossiersWithoutADecidedVerdict")
    void aDossierWithoutADecidedVerdictIsReported(String content, String complaint, @TempDir Path repo)
            throws Exception {
        String manifest = manifestSha(repo, DIAGNOSTIC_ID);
        String sha = write(repo, ReferencePolicy.dossierPath(DIAGNOSTIC_ID, C1.id()), content);
        ReferencePolicy policy = policyOf(decided(diagnostic(DIAGNOSTIC_ID, manifest), "INCONCLUSIVE", sha));

        assertThat(violations(repo, policy))
                .anySatisfy(violation -> assertThat(violation).contains(complaint));
    }

    static Stream<Arguments> dossiersWithoutADecidedVerdict() {
        return Stream.of(
                Arguments.of(dossier(DIAGNOSTIC_ID, C1.id(), "UNKNOWN"), "carries no decided verdict"),
                Arguments.of(dossier(DIAGNOSTIC_ID, C1.id(), "MAYBE"), "carries no decided verdict"),
                Arguments.of(
                        "{\"reference_id\":\"" + DIAGNOSTIC_ID + "\",\"pack\":\"" + C1.id() + "\"}",
                        "carries no decided verdict"),
                Arguments.of("not json at all", "is not valid JSON"));
    }
}
