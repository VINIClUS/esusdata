package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The reference policy (spec 2026-10-08 §8.1): the seeded contract, the rules of the loader, the
 * selector, the hash of the gate set, and the guarantee that the JSON Schema and the loader — which
 * does not run the schema at runtime — refuse the same bad documents, so they cannot drift apart.
 */
class ReferencePolicyTest {

    private static final Path CONTRACTS = Path.of("..", "..", "contracts", "indicators");
    private static final Path POLICY = CONTRACTS.resolve("siaps-reference-policy.json");
    private static final Path SCHEMA = CONTRACTS.resolve("siaps-reference-policy.schema.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<PackDescriptor> PACKS = ReleaseGateRegistry.registeredPacks();
    private static final PackDescriptor C1 = new C1Pack().descriptor();
    private static final PackDescriptor C2 = new C2Pack().descriptor();

    /** Made-up hashes: nothing here reads the files they would pin. */
    private static final String MANIFEST_SHA = "1".repeat(64);

    private static final String DOSSIER_SHA = "2".repeat(64);
    private static final String OTHER_SHA = "3".repeat(64);

    /** The reference the baseline decides D with, and the one it only compares. */
    private static final String GATE_ID = "sp-3541307-2026q1-c1-team-r1";

    private static final String DIAGNOSTIC_ID = "sp-3541307-2025q3-c1-agg-r1";

    /** The other references the selection tests declare, older and newer than the baseline's. */
    private static final String OLDER_GATE_ID = "sp-3541307-2025q3-c1-team-r1";

    private static final String OLDEST_GATE_ID = "sp-3541307-2025q1-c1-team-r1";
    private static final String LATER_DIAGNOSTIC_ID = "sp-3541307-2026q2-c1-agg-r1";
    private static final String OLDEST_DIAGNOSTIC_ID = "sp-3541307-2025q1-c1-agg-r1";

    /** A document the loader refuses, and the sentence it refuses it with. */
    record Bad(String name, Consumer<ObjectNode> breaking, String loaderSays) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** An edit of the baseline, for the tests of the gate set hash. */
    record Change(String name, Consumer<ObjectNode> edit) {
        @Override
        public String toString() {
            return name;
        }
    }

    // ---- the document builders

    /**
     * A policy with an empty set for every compiled rule, built from the rules and not read from the
     * contract: the tests below stay valid when the contract holds real references.
     */
    private static ObjectNode emptyPolicy() {
        ObjectNode root = MAPPER.createObjectNode().put("schema_version", "1");
        ArrayNode sets = root.putArray("reference_sets");
        for (PackDescriptor pack : PACKS) {
            ObjectNode set = sets.addObject()
                    .put("pack", pack.id())
                    .put("rule_version", pack.ruleVersion())
                    .put("check", ReferencePolicy.checkFor(pack.id()))
                    .put("selection_policy", "ALL_REQUIRED");
            set.putArray("references");
        }
        return root;
    }

    private static ArrayNode sets(ObjectNode root) {
        return (ArrayNode) root.get("reference_sets");
    }

    private static ObjectNode set(ObjectNode root, int index) {
        return (ObjectNode) sets(root).get(index);
    }

    private static ArrayNode references(ObjectNode root, int index) {
        return (ArrayNode) set(root, index).get("references");
    }

    /** The empty policy with these declarations in the set of C1, in this order. */
    private static ObjectNode policyOf(ObjectNode... c1Declarations) {
        ObjectNode root = emptyPolicy();
        for (ObjectNode declaration : c1Declarations) {
            references(root, 0).add(declaration);
        }
        return root;
    }

    /** C1 with a GATE reference, then a DIAGNOSTIC one. */
    private static ObjectNode baseline() {
        return policyOf(gate(GATE_ID), diagnostic(DIAGNOSTIC_ID));
    }

    private static ObjectNode gateOf(ObjectNode root) {
        return (ObjectNode) references(root, 0).get(0);
    }

    private static ObjectNode diagnosticOf(ObjectNode root) {
        return (ObjectNode) references(root, 0).get(1);
    }

    private static ObjectNode declaration(String id, String sourceKind, String purpose) {
        Matcher shape = ReferenceDeclaration.ID.matcher(id);
        assertThat(shape.matches()).as("shape of %s", id).isTrue();
        return MAPPER.createObjectNode()
                .put("reference_id", id)
                .put("quadrimestre", shape.group("year") + "Q" + shape.group("index"))
                .put("municipality_ibge", shape.group("ibge"))
                .put("source_kind", sourceKind)
                .put("purpose", purpose)
                .put("reference_manifest_sha256", MANIFEST_SHA);
    }

    /** A sound GATE declaration of C1: an official team export, EXACT, its dossier cited. */
    private static ObjectNode gate(String id) {
        return cite(declaration(id, "OFFICIAL_TEAM_EXPORT_CSV", "GATE")
                .put("required", true)
                .put("status", "ACTIVE")
                .put("compatibility", "EXACT"));
    }

    /** A sound DIAGNOSTIC declaration of C1: a public aggregate nobody has judged yet. */
    private static ObjectNode diagnostic(String id) {
        return uncited(declaration(id, "PUBLIC_AGGREGATE", "DIAGNOSTIC")
                .put("required", false)
                .put("status", "ACTIVE"));
    }

    /** Points the declaration at its dossier, whose verdict it keeps. */
    private static ObjectNode cite(ObjectNode node) {
        return node.put("compatibility_evidence_ref", dossierOf(node))
                .put("compatibility_evidence_sha256", DOSSIER_SHA);
    }

    /** UNKNOWN, with no dossier yet. */
    private static ObjectNode uncited(ObjectNode node) {
        return node.put("compatibility", "UNKNOWN")
                .putNull("compatibility_evidence_ref")
                .putNull("compatibility_evidence_sha256");
    }

    /** The dossier of the declaration's reference, as pack C1 expects it. */
    private static String dossierOf(ObjectNode node) {
        return ReferencePolicy.dossierPath(node.get("reference_id").asString(), C1.id());
    }

    /** The declaration, judged by a dossier whose verdict is {@code verdict}. */
    private static ObjectNode judged(ObjectNode node, String verdict) {
        return cite(node.put("compatibility", verdict));
    }

    /** The reference as a GATE declaration: what the derived gate set makes of an eligible one. */
    private static ObjectNode promote(ObjectNode node) {
        return judged(node.put("purpose", "GATE").put("required", true), "EXACT");
    }

    /** A new revision of the gate reference: another id, so another dossier path too. */
    private static ObjectNode revise(ObjectNode node) {
        return cite(node.put("reference_id", "sp-3541307-2026q1-c1-team-r2"));
    }

    private static ReferenceDeclaration declared(String id, boolean required, ReferenceCompatibility verdict) {
        Matcher shape = ReferenceDeclaration.ID.matcher(id);
        assertThat(shape.matches()).as("shape of %s", id).isTrue();
        boolean decided = verdict.isDecided();
        return new ReferenceDeclaration(
                id,
                shape.group("year") + "Q" + shape.group("index"),
                shape.group("ibge"),
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                ReferencePurpose.GATE,
                required,
                ReferenceStatus.ACTIVE,
                verdict,
                MANIFEST_SHA,
                decided ? ReferencePolicy.dossierPath(id, C1.id()) : null,
                decided ? DOSSIER_SHA : null,
                null);
    }

    private static ReferenceSet referenceSetOf(ReferenceDeclaration... declarations) {
        return new ReferenceSet(
                C1.id(),
                C1.ruleVersion(),
                ReferencePolicy.CHECK_DISTRIBUTION,
                SelectionPolicy.ALL_REQUIRED,
                List.of(declarations));
    }

    private static ReferenceSet c1SetOf(ObjectNode document) {
        return ReferencePolicy.fromJson(document.toString(), PACKS).referenceSet(C1.id(), C1.ruleVersion());
    }

    private static String hashOfC1(ObjectNode document) {
        return c1SetOf(document).gateSetSha256();
    }

    private static List<String> ids(List<ReferenceDeclaration> declarations) {
        return declarations.stream().map(ReferenceDeclaration::referenceId).toList();
    }

    private static Schema schema() throws IOException {
        try (InputStream stream = Files.newInputStream(SCHEMA)) {
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(stream);
        }
    }

    // ---- the seeded contract

    /**
     * The seed of the policy: one set per compiled rule, each empty. It holds no reference until the
     * evidence campaign pre-registers them (spec §16.2, plan Task 9): that change replaces this test
     * with the fixture-based ones below, and nothing before it may promote a declaration to GATE.
     */
    @Test
    void loadsEmptyDiagnosticSetsForEveryCompiledRule() throws Exception {
        ReferencePolicy policy = ReferencePolicy.load(POLICY, PACKS);

        assertThat(policy.referenceSets())
                .extracting(ReferenceSet::ruleVersion)
                .containsExactlyElementsOf(
                        PACKS.stream().map(PackDescriptor::ruleVersion).toList());
        for (PackDescriptor descriptor : PACKS) {
            ReferenceSet set = policy.referenceSet(descriptor.id(), descriptor.ruleVersion());
            assertThat(set.references()).as("%s references", descriptor.id()).isEmpty();
            assertThat(set.check())
                    .as("%s check", descriptor.id())
                    .isEqualTo(ReferencePolicy.checkFor(descriptor.id()));
            assertThat(set.selectionPolicy()).isEqualTo(SelectionPolicy.ALL_REQUIRED);
            assertThat(ReferenceSelector.diagnostics(set)).isEmpty();
            assertThat(ReferenceSelector.requiredGateReferences(set)).isEmpty();
        }
    }

    @Test
    void theChecksAreTheAtTwoIdsAndOnlyTheNotaFinalHasItsOwn() {
        assertThat(ReferencePolicy.CHECK_DISTRIBUTION).isEqualTo("siaps-distribuicao-por-classe@2");
        assertThat(ReferencePolicy.CHECK_NOTA_FINAL).isEqualTo("siaps-nota-final-por-classe@2");
        assertThat(PACKS.stream()
                        .filter(pack -> ReferencePolicy.CHECK_NOTA_FINAL.equals(ReferencePolicy.checkFor(pack.id())))
                        .map(PackDescriptor::id))
                .containsExactly(ComponentIII.ID);
        assertThat(PACKS.stream()
                        .filter(pack -> ReferencePolicy.CHECK_DISTRIBUTION.equals(ReferencePolicy.checkFor(pack.id())))
                        .count())
                .isEqualTo(PACKS.size() - 1L);
    }

    @Test
    void theIdCarriesTheCodeOfItsPackAndPathsFollowTheSpec() {
        assertThat(PACKS.stream().map(ReferencePolicy::packCode).toList())
                .containsExactly("c1", "c2", "c3", "c4", "c5", "c6", "c7", "ciii");
        assertThat(ReferencePolicy.manifestPath(GATE_ID))
                .isEqualTo("docs/indicadores/portoes/references/sp-3541307-2026q1-c1-team-r1.json");
        assertThat(ReferencePolicy.dossierPath(GATE_ID, C1.id()))
                .isEqualTo("docs/indicadores/portoes/compatibilidade/sp-3541307-2026q1-c1-team-r1-c1-mais-acesso.json");
    }

    @Test
    void theSetOfARuleIsFoundByPackAndExactVersionOnly() throws Exception {
        ReferencePolicy policy = ReferencePolicy.load(POLICY, PACKS);
        String stale = "c1-mais-acesso@0.4.0";
        String c1 = C1.id();
        String otherPack = C2.id();
        String ruleVersion = C1.ruleVersion();

        assertThat(policy.referenceSet(c1, ruleVersion).pack()).isEqualTo(c1);
        assertThatThrownBy(() -> policy.referenceSet(c1, stale)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.referenceSet(otherPack, ruleVersion))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingFileIsAnIoErrorNotAnInvalidPolicy(@TempDir Path directory) {
        Path missing = directory.resolve("nope.json");

        assertThatThrownBy(() -> ReferencePolicy.load(missing, PACKS)).isInstanceOf(IOException.class);
    }

    // ---- the rules of the loader

    @ParameterizedTest(name = "{0}")
    @MethodSource("gateRules")
    void rejectsGateReferenceWithoutRequiredExactOrEquivalentDossier(Bad bad) throws Exception {
        ObjectNode document = baseline();
        bad.breaking().accept(document);
        String json = document.toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(json, PACKS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bad.loaderSays());
    }

    @ParameterizedTest
    @EnumSource(ReferenceStatus.class)
    void rejectsDiagnosticReferenceMarkedRequired(ReferenceStatus status) throws Exception {
        ObjectNode document =
                policyOf(diagnostic(DIAGNOSTIC_ID).put("required", true).put("status", status.name()));
        String json = document.toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(json, PACKS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a DIAGNOSTIC reference is never required");
    }

    @Test
    void rejectsDuplicateReferenceIdAcrossSets() throws Exception {
        ObjectNode document = policyOf(gate(GATE_ID));
        references(document, 1).add(gate(GATE_ID));
        String json = document.toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(json, PACKS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate reference_id " + GATE_ID);
    }

    @Test
    void rejectsUnknownPackOrRuleVersion() throws Exception {
        ObjectNode unknownPack = emptyPolicy();
        set(unknownPack, 0).put("pack", "c9-inexistente");
        ObjectNode olderVersion = emptyPolicy();
        set(olderVersion, 0).put("rule_version", "c1-mais-acesso@0.4.0");
        ObjectNode anotherPacksVersion = emptyPolicy();
        set(anotherPacksVersion, 0).put("rule_version", C2.ruleVersion());
        ObjectNode withoutC4 = emptyPolicy();
        sets(withoutC4).remove(3);
        String unknownPackJson = unknownPack.toString();
        String olderVersionJson = olderVersion.toString();
        String anotherPacksVersionJson = anotherPacksVersion.toString();
        String withoutC4Json = withoutC4.toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(unknownPackJson, PACKS))
                .hasMessageContaining("names unregistered pack c9-inexistente");
        assertThatThrownBy(() -> ReferencePolicy.fromJson(olderVersionJson, PACKS))
                .hasMessageContaining("is not the compiled " + C1.ruleVersion());
        assertThatThrownBy(() -> ReferencePolicy.fromJson(anotherPacksVersionJson, PACKS))
                .hasMessageContaining("is not the compiled " + C1.ruleVersion());
        assertThatThrownBy(() -> ReferencePolicy.fromJson(withoutC4Json, PACKS))
                .hasMessageContaining("has no reference set for compiled rule c4-cuidado-diabetes@0.3.0");
    }

    @ParameterizedTest
    @EnumSource(
            value = ReferenceCompatibility.class,
            names = {"EXACT", "EQUIVALENT_FOR_REFERENCE"})
    void compatibleActiveReferenceLeftAsDiagnosticIsRejected(ReferenceCompatibility verdict) throws Exception {
        ObjectNode document = policyOf(judged(diagnostic(DIAGNOSTIC_ID), verdict.name()));
        String json = document.toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(json, PACKS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("belongs to the gate set (spec 8.1 rule 7)");
    }

    @ParameterizedTest
    @CsvSource({"SUPERSEDED, EXACT", "RETRACTED, EXACT", "SUPERSEDED, EQUIVALENT_FOR_REFERENCE"})
    void aReferenceThatIsNoLongerActiveMayStayDiagnosticWhateverItsDossierSaid(String status, String verdict)
            throws Exception {
        ObjectNode document = policyOf(judged(diagnostic(DIAGNOSTIC_ID).put("status", status), verdict));

        assertThat(ReferenceSelector.diagnostics(c1SetOf(document)))
                .extracting(ReferenceDeclaration::status)
                .containsExactly(ReferenceStatus.valueOf(status));
    }

    @Test
    void rejectsJsonThatIsNotOneUnambiguousObject() throws Exception {
        String json = baseline().toString();
        String repeatedKey = json.replace("\"required\":true", "\"required\":true,\"required\":false");
        String trailingContent = json + " {}";
        String truncated = json.substring(0, json.length() / 2);

        assertThat(repeatedKey).isNotEqualTo(json);
        assertThatThrownBy(() -> ReferencePolicy.fromJson(repeatedKey, PACKS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not valid JSON");
        assertThatThrownBy(() -> ReferencePolicy.fromJson(trailingContent, PACKS))
                .hasMessageContaining("is not valid JSON");
        assertThatThrownBy(() -> ReferencePolicy.fromJson(truncated, PACKS)).hasMessageContaining("is not valid JSON");
    }

    @Test
    void theHintAgainstDatesIsGivenForDateLikeFieldsOnly() throws Exception {
        String dated = policyOf(gate(GATE_ID).put("signed_at", "2026-06-24")).toString();
        String plain = policyOf(gate(GATE_ID).put("color", "blue")).toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(dated, PACKS))
                .hasMessageContaining("unknown field signed_at (a reference is chosen by its id, never by a date");
        assertThatThrownBy(() -> ReferencePolicy.fromJson(plain, PACKS))
                .hasMessageContaining("unknown field color")
                .hasMessageNotContaining("never by a date");
    }

    // ---- selection

    @Test
    void doesNotSortOrSelectByQuadrimestreDate() throws Exception {
        ObjectNode document = policyOf(
                gate(GATE_ID), diagnostic(LATER_DIAGNOSTIC_ID), gate(OLDER_GATE_ID), diagnostic(OLDEST_DIAGNOSTIC_ID));
        ObjectNode backwards = policyOf(
                diagnostic(OLDEST_DIAGNOSTIC_ID), gate(OLDER_GATE_ID), diagnostic(LATER_DIAGNOSTIC_ID), gate(GATE_ID));

        ReferenceSet set = c1SetOf(document);
        ReferenceSet reversed = c1SetOf(backwards);

        assertThat(ids(ReferenceSelector.requiredGateReferences(set))).containsExactly(GATE_ID, OLDER_GATE_ID);
        assertThat(ids(ReferenceSelector.diagnostics(set))).containsExactly(LATER_DIAGNOSTIC_ID, OLDEST_DIAGNOSTIC_ID);
        assertThat(ids(ReferenceSelector.requiredGateReferences(reversed))).containsExactly(OLDER_GATE_ID, GATE_ID);
        assertThat(ids(ReferenceSelector.diagnostics(reversed)))
                .containsExactly(OLDEST_DIAGNOSTIC_ID, LATER_DIAGNOSTIC_ID);
    }

    @Test
    void requiredGateReferencesAreAllTheGateDeclarationsNotJustOne() throws Exception {
        ObjectNode document = policyOf(gate(GATE_ID), gate(OLDER_GATE_ID), gate(OLDEST_GATE_ID));

        List<ReferenceDeclaration> required = ReferenceSelector.requiredGateReferences(c1SetOf(document));

        assertThat(required).hasSize(3).allSatisfy(declaration -> {
            assertThat(declaration.required()).isTrue();
            assertThat(declaration.status()).isEqualTo(ReferenceStatus.ACTIVE);
            assertThat(declaration.purpose()).isEqualTo(ReferencePurpose.GATE);
        });
    }

    @Test
    void theSelectorNeverDropsAGateDeclarationThatCannotDecideD() {
        ReferenceDeclaration fit = declared(GATE_ID, true, ReferenceCompatibility.EXACT);
        ReferenceDeclaration inconclusive = declared(OLDER_GATE_ID, true, ReferenceCompatibility.UNKNOWN);
        ReferenceDeclaration notRequired = declared(OLDEST_GATE_ID, false, ReferenceCompatibility.EXACT);
        ReferenceSet sound = referenceSetOf(fit);
        ReferenceSet withoutDossier = referenceSetOf(fit, inconclusive);
        ReferenceSet withOptional = referenceSetOf(fit, notRequired);

        assertThat(ReferenceSelector.requiredGateReferences(sound)).containsExactly(fit);
        assertThatThrownBy(() -> ReferenceSelector.requiredGateReferences(withoutDossier))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OLDER_GATE_ID)
                .hasMessageContaining("not UNKNOWN");
        assertThatThrownBy(() -> ReferenceSelector.requiredGateReferences(withOptional))
                .hasMessageContaining("a GATE reference must be required");
    }

    // ---- the hash of the gate set

    @Test
    void addingOrChangingDiagnosticDeclarationKeepsGateSetSha256() throws Exception {
        String before = hashOfC1(baseline());

        ObjectNode added = baseline();
        references(added, 0).add(diagnostic(LATER_DIAGNOSTIC_ID));
        ObjectNode judgedLater = baseline();
        judged(diagnosticOf(judgedLater), "INCONCLUSIVE").put("note", "sem universo histórico");
        ObjectNode superseded = baseline();
        diagnosticOf(superseded).put("status", "SUPERSEDED");
        ObjectNode replaced = baseline();
        references(replaced, 0)
                .set(
                        1,
                        diagnostic("sp-3541307-2025q1-c1-aggu-r2")
                                .put("source_kind", "PUBLIC_AGGREGATE_WITH_PERIOD_UNIVERSE"));
        ObjectNode removed = baseline();
        references(removed, 0).remove(1);

        assertThat(Stream.of(added, judgedLater, superseded, replaced, removed).map(ReferencePolicyTest::hashOfC1))
                .containsOnly(before);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gateChanges")
    void changingOrRemovingGateDeclarationChangesGateSetSha256(Change change) throws Exception {
        String before = hashOfC1(baseline());
        ObjectNode edited = baseline();

        change.edit().accept(edited);

        assertThat(hashOfC1(edited)).isNotEqualTo(before);
    }

    static Stream<Change> gateChanges() {
        return Stream.of(
                new Change(
                        "the gate reference is removed",
                        root -> references(root, 0).remove(0)),
                new Change(
                        "a second gate reference is declared",
                        root -> references(root, 0).add(gate(OLDER_GATE_ID))),
                new Change("the diagnostic reference is promoted to gate", root -> promote(diagnosticOf(root))),
                new Change(
                        "the verdict of the dossier changes",
                        root -> gateOf(root).put("compatibility", "EQUIVALENT_FOR_REFERENCE")),
                new Change(
                        "the manifest hash changes", root -> gateOf(root).put("reference_manifest_sha256", OTHER_SHA)),
                new Change(
                        "the dossier hash changes",
                        root -> gateOf(root).put("compatibility_evidence_sha256", OTHER_SHA)),
                new Change("the reference is a new revision", root -> revise(gateOf(root))));
    }

    @Test
    void theGateSetOfOneRuleDoesNotDependOnAnotherRulesSet() throws Exception {
        String before = hashOfC1(baseline());
        String foreign = "sp-3541307-2026q1-c2-team-r1";
        ObjectNode withGateInC2 = baseline();
        references(withGateInC2, 1)
                .add(gate(foreign).put("compatibility_evidence_ref", ReferencePolicy.dossierPath(foreign, C2.id())));

        ReferencePolicy policy = ReferencePolicy.fromJson(withGateInC2.toString(), PACKS);

        assertThat(policy.referenceSet(C1.id(), C1.ruleVersion()).gateSetSha256())
                .isEqualTo(before);
        assertThat(policy.referenceSet(C2.id(), C2.ruleVersion()).gateSetSha256())
                .isNotEqualTo(before);
    }

    @Test
    void gateSetSha256IsIndependentOfDeclarationOrderAndWhitespace() throws Exception {
        ObjectNode document = policyOf(gate(GATE_ID), diagnostic(DIAGNOSTIC_ID), gate(OLDER_GATE_ID));
        String compact = document.toString();
        String reshaped = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(reversed(document));

        ReferencePolicy original = ReferencePolicy.fromJson(compact, PACKS);
        ReferencePolicy shuffled = ReferencePolicy.fromJson(reshaped, PACKS);

        assertThat(reshaped).isNotEqualTo(compact).contains("\n  ");
        assertThat(shuffled.referenceSet(C1.id(), C1.ruleVersion()).references())
                .extracting(ReferenceDeclaration::referenceId)
                .containsExactly(OLDER_GATE_ID, DIAGNOSTIC_ID, GATE_ID);
        for (PackDescriptor descriptor : PACKS) {
            ReferenceSet a = original.referenceSet(descriptor.id(), descriptor.ruleVersion());
            ReferenceSet b = shuffled.referenceSet(descriptor.id(), descriptor.ruleVersion());
            assertThat(b.canonicalGateJson()).as(descriptor.id()).isEqualTo(a.canonicalGateJson());
            assertThat(b.gateSetSha256()).as(descriptor.id()).isEqualTo(a.gateSetSha256());
        }
    }

    /** The same document with every object's keys and every array's elements in reverse order. */
    private static JsonNode reversed(JsonNode node) {
        if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(node.properties());
            Collections.reverse(entries);
            ObjectNode copy = MAPPER.createObjectNode();
            entries.forEach(entry -> copy.set(entry.getKey(), reversed(entry.getValue())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            for (int i = node.size() - 1; i >= 0; i--) {
                copy.add(reversed(node.get(i)));
            }
            return copy;
        }
        return node;
    }

    /**
     * The format is the contract: D cites this digest, so a rewrite of the canonical form that
     * agreed only with itself would silently void every approval. The strings and digests below
     * were written by hand and hashed with {@code sha256sum}, not produced by the code.
     */
    @Test
    void theCanonicalFormOfTheGateSetAndItsDigestAreFixed() throws Exception {
        ObjectNode document = policyOf(gate(GATE_ID), diagnostic(DIAGNOSTIC_ID));
        String expectedC1 = """
                {"check":"siaps-distribuicao-por-classe@2","gate_references":[{"compatibility":"EXACT",\
                "compatibility_evidence_ref":"docs/indicadores/portoes/compatibilidade/\
                sp-3541307-2026q1-c1-team-r1-c1-mais-acesso.json",\
                "compatibility_evidence_sha256":"2222222222222222222222222222222222222222222222222222222222222222",\
                "reference_id":"sp-3541307-2026q1-c1-team-r1",\
                "reference_manifest_sha256":"1111111111111111111111111111111111111111111111111111111111111111",\
                "required":true,"status":"ACTIVE"}],"pack":"c1-mais-acesso","rule_version":"c1-mais-acesso@0.5.0",\
                "selection_policy":"ALL_REQUIRED"}""";
        String expectedNotaFinal = """
                {"check":"siaps-nota-final-por-classe@2","gate_references":[],"pack":"componente-iii-nota-final",\
                "rule_version":"componente-iii-nota-final@0.3.0","selection_policy":"ALL_REQUIRED"}""";

        ReferencePolicy policy = ReferencePolicy.fromJson(document.toString(), PACKS);
        ReferenceSet c1 = policy.referenceSet(C1.id(), C1.ruleVersion());
        ReferenceSet notaFinal = policy.referenceSet(ComponentIII.ID, ComponentIII.RULE_VERSION);

        assertThat(c1.canonicalGateJson()).isEqualTo(expectedC1);
        assertThat(c1.gateSetSha256()).isEqualTo("0c809097e11d8d7dbcc15800a05633e25cc821bd0c80762b313f0bec8e8216af");
        assertThat(notaFinal.canonicalGateJson()).isEqualTo(expectedNotaFinal);
        assertThat(notaFinal.gateSetSha256())
                .isEqualTo("61316c3b6cf673f04f86a5909b6cb8d75dd130f68cf5aa5b73164ea3a20045db");
    }

    // ---- the schema and the loader refuse the same documents

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedByBoth")
    void theSchemaRefusesWhatTheLoaderRefusesWhereverJsonSchemaCanStateTheRule(Bad bad) throws Exception {
        ObjectNode document = baseline();
        bad.breaking().accept(document);
        String json = document.toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(json, PACKS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bad.loaderSays());
        assertThat(schema().validate(document)).as(bad.name()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedByTheLoaderOnly")
    void theLoaderAloneRefusesWhatNeedsMoreThanTheShapeOfOneDocument(Bad bad) throws Exception {
        ObjectNode document = baseline();
        bad.breaking().accept(document);
        String json = document.toString();

        assertThatThrownBy(() -> ReferencePolicy.fromJson(json, PACKS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bad.loaderSays());
        assertThat(schema().validate(document)).as(bad.name()).isEmpty();
    }

    @Test
    void theBaselineSatisfiesTheSchemaAndTheLoader() throws Exception {
        ObjectNode document = baseline();

        assertThat(schema().validate(document)).isEmpty();
        assertThat(ids(c1SetOf(document).references())).containsExactly(GATE_ID, DIAGNOSTIC_ID);
    }

    @Test
    void theContractFileSatisfiesTheSchema() throws Exception {
        assertThat(schema().validate(MAPPER.readTree(POLICY))).isEmpty();
    }

    @Test
    void theSchemaEnumsAreTheJavaEnumsAndTheSchemaNamesTheTwoChecks() throws Exception {
        JsonNode defs = MAPPER.readTree(SCHEMA).get("$defs");
        JsonNode declaration = defs.get("declaration").get("properties");
        JsonNode set = defs.get("referenceSet").get("properties");

        assertThat(enumOf(declaration, "source_kind")).containsExactlyInAnyOrderElementsOf(names(SourceKind.values()));
        assertThat(enumOf(declaration, "purpose"))
                .containsExactlyInAnyOrderElementsOf(names(ReferencePurpose.values()));
        assertThat(enumOf(declaration, "status")).containsExactlyInAnyOrderElementsOf(names(ReferenceStatus.values()));
        assertThat(enumOf(declaration, "compatibility"))
                .containsExactlyInAnyOrderElementsOf(names(ReferenceCompatibility.values()));
        assertThat(enumOf(set, "selection_policy"))
                .containsExactlyInAnyOrderElementsOf(names(SelectionPolicy.values()));
        assertThat(enumOf(set, "check"))
                .containsExactlyInAnyOrder(ReferencePolicy.CHECK_DISTRIBUTION, ReferencePolicy.CHECK_NOTA_FINAL);
    }

    private static List<String> enumOf(JsonNode properties, String field) {
        List<String> values = new ArrayList<>();
        properties.get(field).get("enum").forEach(value -> values.add(value.asString()));
        return values;
    }

    private static List<String> names(Enum<?>... constants) {
        return Stream.of(constants).map(Enum::name).toList();
    }

    @Test
    void theSchemaKnowsEveryCompiledPackAndNoSetCanBorrowAnothersCheckOrIds() throws Exception {
        Schema schema = schema();
        for (int index = 0; index < PACKS.size(); index++) {
            ObjectNode withOtherCheck = emptyPolicy();
            boolean isNotaFinal = ReferencePolicy.CHECK_NOTA_FINAL.equals(
                    set(withOtherCheck, index).get("check").asString());
            set(withOtherCheck, index)
                    .put("check", isNotaFinal ? ReferencePolicy.CHECK_DISTRIBUTION : ReferencePolicy.CHECK_NOTA_FINAL);
            ObjectNode withOtherPacksId = emptyPolicy();
            String foreign = ReferencePolicy.packCode(PACKS.get((index + 1) % PACKS.size()));
            references(withOtherPacksId, index).add(diagnostic("sp-3541307-2026q1-" + foreign + "-agg-r1"));

            assertThat(schema.validate(withOtherCheck))
                    .as("check of %s", PACKS.get(index).id())
                    .isNotEmpty();
            assertThat(schema.validate(withOtherPacksId))
                    .as("ids of %s", PACKS.get(index).id())
                    .isNotEmpty();
        }
    }

    // ---- the documents, grouped by what they break

    /** Rule 3 of spec §8.1: a GATE reference is required, ACTIVE, EXACT or equivalent, and has its dossier. */
    static Stream<Bad> gateRules() {
        return Stream.of(
                new Bad(
                        "a GATE reference that is not required",
                        root -> gateOf(root).put("required", false),
                        "a GATE reference must be required"),
                new Bad(
                        "a SUPERSEDED GATE reference",
                        root -> gateOf(root).put("status", "SUPERSEDED"),
                        "a GATE reference must be ACTIVE, not SUPERSEDED"),
                new Bad(
                        "a RETRACTED GATE reference",
                        root -> gateOf(root).put("status", "RETRACTED"),
                        "a GATE reference must be ACTIVE, not RETRACTED"),
                new Bad(
                        "a GATE reference whose dossier is INCOMPATIBLE",
                        root -> gateOf(root).put("compatibility", "INCOMPATIBLE"),
                        "or EQUIVALENT_FOR_REFERENCE, not INCOMPATIBLE"),
                new Bad(
                        "a GATE reference whose dossier is INCONCLUSIVE",
                        root -> gateOf(root).put("compatibility", "INCONCLUSIVE"),
                        "or EQUIVALENT_FOR_REFERENCE, not INCONCLUSIVE"),
                new Bad(
                        "a GATE reference whose compatibility is still UNKNOWN",
                        root -> uncited(gateOf(root)),
                        "or EQUIVALENT_FOR_REFERENCE, not UNKNOWN"),
                new Bad(
                        "a GATE reference without the path of its dossier",
                        root -> gateOf(root).putNull("compatibility_evidence_ref"),
                        "a GATE reference needs compatibility_evidence_ref"),
                new Bad(
                        "a GATE reference without the hash of its dossier",
                        root -> gateOf(root).putNull("compatibility_evidence_sha256"),
                        "a GATE reference needs compatibility_evidence_sha256"));
    }

    /** The other rules a JSON Schema can state: the schema refuses these documents as well. */
    static Stream<Bad> statableRules() {
        return Stream.of(declarationRules(), shapeRules(), dateFields(), referenceSetRules())
                .flatMap(group -> group);
    }

    private static Stream<Bad> declarationRules() {
        return Stream.of(
                new Bad(
                        "a DIAGNOSTIC reference marked required",
                        root -> diagnosticOf(root).put("required", true),
                        "a DIAGNOSTIC reference is never required"),
                new Bad(
                        "an ACTIVE EXACT reference left DIAGNOSTIC",
                        root -> judged(diagnosticOf(root), "EXACT"),
                        "belongs to the gate set (spec 8.1 rule 7)"),
                new Bad(
                        "an ACTIVE EQUIVALENT_FOR_REFERENCE reference left DIAGNOSTIC",
                        root -> judged(diagnosticOf(root), "EQUIVALENT_FOR_REFERENCE"),
                        "belongs to the gate set (spec 8.1 rule 7)"),
                new Bad(
                        "an UNKNOWN compatibility that cites a dossier",
                        root -> cite(diagnosticOf(root)),
                        "an UNKNOWN compatibility cites no dossier"),
                new Bad(
                        "a decided compatibility that cites no dossier",
                        root -> uncited(diagnosticOf(root)).put("compatibility", "INCONCLUSIVE"),
                        "compatibility INCONCLUSIVE must cite the dossier that decided it"),
                new Bad(
                        "a dossier path without the hash of the dossier",
                        root -> diagnosticOf(root)
                                .put("compatibility", "INCONCLUSIVE")
                                .put("compatibility_evidence_ref", dossierOf(diagnosticOf(root))),
                        "compatibility_evidence_ref and compatibility_evidence_sha256 must be set together"),
                new Bad(
                        "a dossier outside the dossier directory",
                        root -> judged(diagnosticOf(root), "INCONCLUSIVE")
                                .put("compatibility_evidence_ref", "docs/outro/dossie.json"),
                        "does not match"),
                new Bad(
                        "an id of another pack",
                        root -> gateOf(root).put("reference_id", "sp-3541307-2026q1-c2-team-r1"),
                        "reference_id names pack c2 but the set is c1-mais-acesso (c1)"),
                new Bad(
                        "an id that disagrees with the source kind",
                        root -> gateOf(root).put("source_kind", "PUBLIC_AGGREGATE"),
                        "reference_id names source team but source_kind is PUBLIC_AGGREGATE"),
                new Bad(
                        "a reference_id repeated across sets",
                        root -> references(root, 1).add(gateOf(root).deepCopy()),
                        "duplicate reference_id " + GATE_ID));
    }

    private static Stream<Bad> shapeRules() {
        return Stream.of(
                new Bad(
                        "an id with capitals",
                        root -> gateOf(root).put("reference_id", "SP-3541307-2026Q1-C1-TEAM-R1"),
                        "does not match"),
                new Bad(
                        "an id with a segment between the pack and the source",
                        root -> gateOf(root).put("reference_id", "sp-3541307-2026q1-c1-extra-team-r1"),
                        "does not match"),
                new Bad(
                        "a hash in capitals",
                        root -> gateOf(root).put("reference_manifest_sha256", "A".repeat(64)),
                        "does not match"),
                new Bad(
                        "a hash that is too short",
                        root -> gateOf(root).put("compatibility_evidence_sha256", "ab12"),
                        "does not match"),
                new Bad(
                        "a quadrimestre that does not exist",
                        root -> gateOf(root).put("quadrimestre", "2026Q4"),
                        "does not match"),
                new Bad(
                        "an IBGE code of six digits",
                        root -> gateOf(root).put("municipality_ibge", "354130"),
                        "does not match"),
                new Bad(
                        "a flag written as a string",
                        root -> gateOf(root).put("required", "true"),
                        "required must be true or false"),
                new Bad(
                        "an IBGE code written as a number",
                        root -> gateOf(root).put("municipality_ibge", 3_541_307),
                        "municipality_ibge must be a string"),
                new Bad(
                        "a purpose nobody defined",
                        root -> gateOf(root).put("purpose", "OBSERVER"),
                        "purpose OBSERVER is not one of"),
                new Bad(
                        "a declaration without its purpose",
                        root -> gateOf(root).remove("purpose"),
                        "needs purpose"),
                new Bad("a note that is null", root -> gateOf(root).putNull("note"), "note must be a string"),
                new Bad("a field nobody defined", root -> gateOf(root).put("color", "blue"), "unknown field color"));
    }

    /** The fields that would select a reference by date: the contract has none, and says so. */
    private static Stream<Bad> dateFields() {
        return Stream.of(
                        "selected_after",
                        "valid_from",
                        "latest",
                        "published_at",
                        "captured_at",
                        "signature_date",
                        "floor")
                .map(field -> new Bad(
                        "the date field " + field,
                        root -> gateOf(root).put(field, "2026-06-24"),
                        "unknown field " + field));
    }

    private static Stream<Bad> referenceSetRules() {
        return Stream.of(
                new Bad(
                        "C1 with the check of the Nota Final",
                        root -> set(root, 0).put("check", ReferencePolicy.CHECK_NOTA_FINAL),
                        "check must be " + ReferencePolicy.CHECK_DISTRIBUTION),
                new Bad(
                        "the Nota Final with the check of C1 to C7",
                        root -> set(root, 7).put("check", ReferencePolicy.CHECK_DISTRIBUTION),
                        "check must be " + ReferencePolicy.CHECK_NOTA_FINAL),
                new Bad(
                        "a selection policy nobody defined",
                        root -> set(root, 0).put("selection_policy", "ANY_PASSES"),
                        "selection_policy ANY_PASSES is not one of"),
                new Bad(
                        "the rule_version of another pack",
                        root -> set(root, 0).put("rule_version", C2.ruleVersion()),
                        "is not the compiled " + C1.ruleVersion()),
                new Bad(
                        "a field the set does not know",
                        root -> set(root, 0).put("floor_date", "2026-06-24"),
                        "unknown field floor_date"),
                new Bad(
                        "another schema_version",
                        root -> root.put("schema_version", "2"),
                        "unsupported schema_version"),
                new Bad(
                        "a policy without sets",
                        root -> root.set("reference_sets", MAPPER.createArrayNode()),
                        "reference_sets must be a non-empty array"),
                new Bad(
                        "a field the policy does not know",
                        root -> root.put("latest_only", true),
                        "unknown field latest_only"));
    }

    static Stream<Bad> refusedByBoth() {
        return Stream.concat(gateRules(), statableRules());
    }

    /**
     * What a JSON Schema cannot say: it sees one document, and knows neither the compiled rules nor
     * the other declarations.
     */
    static Stream<Bad> refusedByTheLoaderOnly() {
        return Stream.of(
                new Bad(
                        "a quadrimestre that disagrees with the id",
                        root -> gateOf(root).put("quadrimestre", "2026Q2"),
                        "reference_id names quadrimestre 2026Q1 but quadrimestre is 2026Q2"),
                new Bad(
                        "a municipality that disagrees with the id",
                        root -> gateOf(root).put("municipality_ibge", "3550308"),
                        "reference_id names municipality 3541307 but municipality_ibge is 3550308"),
                new Bad(
                        "the dossier of another reference",
                        root -> judged(diagnosticOf(root), "INCONCLUSIVE")
                                .put("compatibility_evidence_ref", dossierOf(gateOf(root))),
                        "compatibility_evidence_ref must be " + ReferencePolicy.dossierPath(DIAGNOSTIC_ID, C1.id())),
                new Bad(
                        "a reference_id repeated within a set",
                        root -> references(root, 0).add(gateOf(root).deepCopy()),
                        "duplicate reference_id " + GATE_ID),
                new Bad(
                        "an older rule_version of the same pack",
                        root -> set(root, 0).put("rule_version", "c1-mais-acesso@0.4.0"),
                        "is not the compiled " + C1.ruleVersion()),
                new Bad(
                        "a pack that is not compiled",
                        root -> set(root, 0).put("pack", "c9-inexistente"),
                        "names unregistered pack c9-inexistente"),
                new Bad(
                        "a second set for the same rule",
                        root -> sets(root).add(set(root, 2).deepCopy()),
                        "duplicate reference set for c3-gestacao-puerperio@0.3.0"),
                new Bad(
                        "a compiled rule without a set",
                        root -> sets(root).remove(3),
                        "has no reference set for compiled rule c4-cuidado-diabetes@0.3.0"));
    }
}
