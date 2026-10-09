package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.reconciliation.MethodologyProfile.DataTiming;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredConvention;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredLimitation;
import esusdata.indicator.reconciliation.MethodologyProfile.Dimension;
import esusdata.indicator.reconciliation.MethodologyProfile.OfficialEdition;
import esusdata.indicator.reconciliation.MethodologyProfile.Reading;
import esusdata.indicator.reconciliation.MethodologyProfile.Source;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The methodology profiles (spec 2026-10-08 §8.3): the shape of the contract, the rules of the
 * loader and the guarantee that the JSON Schema and the loader, which does not run the schema at
 * runtime, refuse the same bad documents, so they cannot drift apart. Everything here runs on an
 * invented profile ({@code methodology-profile-fixture.json}); the production profiles come with
 * their own consistency test.
 */
class MethodologyProfileRegistryTest {

    private static final Path SCHEMA =
            Path.of("..", "..", "contracts", "indicators").resolve("siaps-methodology-profiles.schema.json");
    private static final String FIXTURE = "/esusdata/indicator/reconciliation/methodology-profile-fixture.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The fixture: two versions of one invented pack. */
    private static final String PACK = "fixture-pack";

    private static final String V1 = "fixture-pack@1.0.0";
    private static final String V1_1 = "fixture-pack@1.1.0";

    private static final String ANCHOR = "fx.cohort.anchor-date";
    private static final String SAME_DAY = "fx.visits.same-day";
    private static final String CREDIT = "fx.team.credit";
    private static final String CUTOFF = "fx.timing.transmission-cutoff";
    private static final String LATE = "fx.timing.late-sending";
    private static final String REPROCESSING = "fx.timing.reprocessing";
    private static final String LIMITATION = "fx.limitation.person-identification";
    private static final String CONVENTION = "fx.convention.team-type-date";
    private static final String Q_2025 = "2025Q3";
    private static final String Q_2026 = "2026Q1";
    private static final String NT_2026 = "fx-nt-2026";
    private static final String FICHA_2025 = "fx-ficha-2025";

    /** A document the loader refuses, and the sentence it refuses it with. */
    record Bad(String name, Consumer<ObjectNode> breaking, String loaderSays) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** A date-like field put into one of the contract's objects. */
    record DateField(String name, UnaryOperator<ObjectNode> into, String field) {
        @Override
        public String toString() {
            return name;
        }
    }

    // ---- the documents

    private static String fixtureText() throws IOException {
        try (InputStream stream = MethodologyProfileRegistryTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(stream).as("missing classpath resource %s", FIXTURE).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ObjectNode fixture() throws IOException {
        return (ObjectNode) MAPPER.readTree(fixtureText());
    }

    private static Schema schema() throws IOException {
        try (InputStream stream = Files.newInputStream(SCHEMA)) {
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(stream);
        }
    }

    private static ArrayNode profiles(ObjectNode root) {
        return (ArrayNode) root.get("profiles");
    }

    /** The first profile of the fixture, {@value #V1}. */
    private static ObjectNode profile(ObjectNode root) {
        return (ObjectNode) profiles(root).get(0);
    }

    private static ObjectNode dimension(ObjectNode root, int index) {
        return (ObjectNode) profile(root).get("dimensions").get(index);
    }

    private static ObjectNode timing(ObjectNode root, int index) {
        return (ObjectNode) profile(root).get("data_timing").get(index);
    }

    private static ObjectNode limitation(ObjectNode root, int index) {
        return (ObjectNode) profile(root).get("declared_limitations").get(index);
    }

    private static ObjectNode convention(ObjectNode root, int index) {
        return (ObjectNode) profile(root).get("declared_conventions").get(index);
    }

    private static ObjectNode source(ObjectNode root, int index) {
        return (ObjectNode) root.get("sources").get(index);
    }

    private static ObjectNode quadrimestre(ObjectNode root, String quadrimestre) {
        return (ObjectNode) profile(root).get("official_readings").get(quadrimestre);
    }

    private static ObjectNode edition(ObjectNode root, String quadrimestre) {
        return (ObjectNode) quadrimestre(root, quadrimestre).get("edition");
    }

    private static ObjectNode readings(ObjectNode root, String quadrimestre) {
        return (ObjectNode) quadrimestre(root, quadrimestre).get("readings");
    }

    private static ObjectNode reading(ObjectNode root, String quadrimestre, String dimension) {
        return (ObjectNode) readings(root, quadrimestre).get(dimension);
    }

    private static ArrayNode array(String... values) {
        ArrayNode array = MAPPER.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static ObjectNode sameReading(String source) {
        return MAPPER.createObjectNode().put("reading", "SAME").put("source_ref", source);
    }

    private static MethodologyProfileRegistry load() throws IOException {
        return MethodologyProfileRegistry.fromJson(fixtureText());
    }

    // ---- the fixture

    @Test
    void loadsTheFixtureWithEveryFieldAndEveryReferenceResolved() throws Exception {
        MethodologyProfileRegistry registry = load();

        assertThat(registry.profiles())
                .extracting(MethodologyProfile::ruleVersion)
                .containsExactly(V1, V1_1);
        assertThat(registry.sources()).extracting(Source::id).containsExactly(FICHA_2025, NT_2026, "fx-release-4-2");
        Source release = registry.sources().get(2);
        assertThat(release.title()).isEqualTo("Fixture: release 4.2 do SIAPS");
        assertThat(release.url()).isEqualTo("https://example.org/fixture/release-4-2");
        assertThat(release.published()).isEqualTo(LocalDate.of(2026, 5, 20));
        assertThat(release.kind()).isEqualTo(Source.DocumentKind.SIAPS_RELEASE);

        MethodologyProfile profile = registry.profile(PACK, V1);
        assertThat(profile.dimensions()).extracting(Dimension::id).containsExactly(ANCHOR, SAME_DAY, CREDIT);
        Dimension sameDay = profile.dimensions().get(1);
        assertThat(sameDay.localReading()).isEqualTo("Two visits on the same day count once.");
        assertThat(sameDay.decisionRefs()).containsExactly("AMB-FX-02", "LACUNA-L3");
        assertThat(sameDay.probeId()).contains(SAME_DAY);
        assertThat(profile.dimensions().get(2).decisionRefs()).isEmpty();
        assertThat(profile.dataTiming()).extracting(DataTiming::id).containsExactly(CUTOFF, LATE, REPROCESSING);
        assertThat(profile.dataTiming())
                .extracting(DataTiming::kind)
                .containsExactly(
                        DataTiming.TimingKind.TRANSMISSION_CUTOFF,
                        DataTiming.TimingKind.LATE_SENDING,
                        DataTiming.TimingKind.REPROCESSING);
        assertThat(profile.dataTiming().get(0).decisionRefs()).containsExactly("FX-LIM-01");
        assertThat(profile.officialReadings()).containsOnlyKeys(Q_2025, Q_2026);
    }

    @Test
    void anEditionNamesItsSourcesAndEveryDimensionHasItsReadingWithTheSourceItRestsOn() throws Exception {
        MethodologyProfile profile = load().profile(PACK, V1);

        OfficialEdition edition2026 = profile.officialEdition(Q_2026).orElseThrow();
        assertThat(edition2026.id()).isEqualTo("fixture-2026-v1");
        assertThat(edition2026.sources()).extracting(Source::id).containsExactly(NT_2026, "fx-release-4-2");
        assertThat(edition2026.readings()).containsOnlyKeys(ANCHOR, SAME_DAY, CREDIT);
        Reading different = edition2026.readings().get(SAME_DAY);
        assertThat(different.reading()).isEqualTo(OfficialReading.DIFFERENT);
        assertThat(different.officialReadingText())
                .isEqualTo("Each visit counts, even when two happen on the same day.");
        assertThat(different.source().id()).isEqualTo("fx-release-4-2");
        Reading same = edition2026.readings().get(ANCHOR);
        assertThat(same.reading()).isEqualTo(OfficialReading.SAME);
        assertThat(same.officialReadingText()).isNull();

        OfficialEdition edition2025 = profile.officialEdition(Q_2025).orElseThrow();
        assertThat(edition2025.readings().get(CREDIT).reading()).isEqualTo(OfficialReading.UNKNOWN);
        assertThat(edition2025.readings().get(CREDIT).officialReadingText()).isNull();
    }

    @Test
    void anOfficialEditionIsFoundByTheExactQuadrimestreAndAnUndeclaredOneHasNone() throws Exception {
        MethodologyProfile profile = load().profile(PACK, V1);

        assertThat(profile.officialEdition(Q_2026)).isPresent();
        assertThat(profile.officialEdition("2026Q2")).isEmpty();
        assertThat(profile.officialEdition("2026-Q1")).isEmpty();
        assertThat(profile.officialEdition("2026q1")).isEmpty();
        assertThat(load().profile(PACK, V1_1).officialEdition(Q_2025)).isEmpty();
    }

    @Test
    void theRequiredProbesAreTheOnesOfTheDimensionsAndDataTimingAddsNone() throws Exception {
        MethodologyProfileRegistry registry = load();

        assertThat(registry.profile(PACK, V1).requiredProbeIds()).containsExactly(ANCHOR, SAME_DAY, CREDIT);
        assertThat(registry.profile(PACK, V1_1).requiredProbeIds()).containsExactly(ANCHOR);
        assertThat(registry.profile(PACK, V1).dataTiming()).hasSize(3);
    }

    @Test
    void limitationsAndConventionsRoundTripAndTheProfileFindsALimitationById() throws Exception {
        MethodologyProfile profile = load().profile(PACK, V1);

        assertThat(profile.declaredLimitations())
                .extracting(DeclaredLimitation::id)
                .containsExactly(LIMITATION);
        DeclaredLimitation limitation = profile.declaredLimitations().get(0);
        assertThat(limitation.what()).startsWith("The local rule cannot see the national registry");
        assertThat(limitation.decisionRefs()).containsExactly("FX-LIM-02");
        assertThat(profile.limitation(LIMITATION)).contains(limitation);
        assertThat(profile.limitation(CONVENTION)).isEmpty();
        assertThat(profile.declaresLimitation(LIMITATION)).isTrue();
        assertThat(profile.declaresLimitation("fx.limitation.other")).isFalse();
        assertThat(profile.declaresLimitation(ANCHOR)).isFalse();

        assertThat(profile.declaredConventions())
                .extracting(DeclaredConvention::id)
                .containsExactly(CONVENTION);
        DeclaredConvention convention = profile.declaredConventions().get(0);
        assertThat(convention.localReading())
                .isEqualTo("The team type is read at the reference date of the quadrimestre.");
        assertThat(convention.decisionRefs()).containsExactly("AMB-FX-03");
        assertThat(convention.researchRef()).isEqualTo("fixture-research.md section 1 (K3)");
        assertThat(convention.probeId()).contains(CONVENTION);
    }

    @Test
    void emptyLimitationAndConventionArraysAreFine() throws Exception {
        MethodologyProfile bare = load().profile(PACK, V1_1);

        assertThat(bare.declaredLimitations()).isEmpty();
        assertThat(bare.declaredConventions()).isEmpty();
        assertThat(bare.conventionProbeIds()).isEmpty();
        assertThat(bare.limitation(LIMITATION)).isEmpty();
    }

    @Test
    void theRequiredProbesLeaveOutTheConventionsAndTheConventionProbesListOnlyThem() throws Exception {
        MethodologyProfile profile = load().profile(PACK, V1);

        assertThat(profile.requiredProbeIds())
                .containsExactly(ANCHOR, SAME_DAY, CREDIT)
                .doesNotContain(CONVENTION);
        assertThat(profile.conventionProbeIds()).containsExactly(CONVENTION);
    }

    @Test
    void aValidationTimingItemLoadsAndTheSchemaAcceptsIt() throws Exception {
        ObjectNode root = fixture();
        timing(root, 1).put("kind", "VALIDATION");
        MethodologyProfile profile =
                MethodologyProfileRegistry.fromJson(root.toString()).profile(PACK, V1);

        assertThat(profile.dataTiming().get(1).kind()).isEqualTo(DataTiming.TimingKind.VALIDATION);
        assertThat(schema().validate(root)).isEmpty();
    }

    @Test
    void aConventionWithoutAProbeAddsNoConventionProbe() throws Exception {
        ObjectNode root = fixture();
        convention(root, 0).remove("probe_id");
        MethodologyProfile profile =
                MethodologyProfileRegistry.fromJson(root.toString()).profile(PACK, V1);

        assertThat(profile.declaredConventions().get(0).probeId()).isEmpty();
        assertThat(profile.conventionProbeIds()).isEmpty();
        assertThat(schema().validate(root)).isEmpty();
    }

    @Test
    void aDimensionWithoutAProbeMustReadSameEverywhereAndThenItLoadsAndIsNotRequired() throws Exception {
        ObjectNode root = fixture();
        dimension(root, 0).remove("probe_id");
        MethodologyProfile profile =
                MethodologyProfileRegistry.fromJson(root.toString()).profile(PACK, V1);

        assertThat(profile.dimensions().get(0).probeId()).isEmpty();
        assertThat(profile.dimensions().get(1).probeId()).contains(SAME_DAY);
        assertThat(profile.requiredProbeIds()).containsExactly(SAME_DAY, CREDIT);
        assertThat(schema().validate(root)).isEmpty();
    }

    @Test
    void aDimensionWithoutAProbeThatReadsDifferentSomewhereIsRefusedNamingTheDimension() throws Exception {
        ObjectNode root = fixture();
        dimension(root, 1).remove("probe_id");
        String json = root.toString();

        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimension " + SAME_DAY + " has no probe_id")
                .hasMessageContaining("2026Q1 must read it SAME, not DIFFERENT");
    }

    @Test
    void aDimensionWithoutAProbeThatReadsUnknownSomewhereIsRefusedNamingTheDimension() throws Exception {
        ObjectNode root = fixture();
        dimension(root, 2).remove("probe_id");
        String json = root.toString();

        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimension " + CREDIT + " has no probe_id")
                .hasMessageContaining("2025Q3 must read it SAME, not UNKNOWN");
    }

    @Test
    void theModelRefusesWhatTheLoaderRefusesWithoutTheJson() {
        assertThatThrownBy(() -> new DeclaredLimitation("fx.limitation.x", "what", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs at least one decision_ref");
        assertThatThrownBy(() -> new DeclaredConvention("fx.convention.x", "reads", List.of(), "ref", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs at least one decision_ref");
        assertThatThrownBy(() -> new Dimension("fx.dimension.x", "reads", List.of(), Optional.of("probe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probe_id of fx.dimension.x must look like");
        assertThat(new Dimension("fx.dimension.x", "reads", List.of(), "fx.dimension.x").probeId())
                .contains("fx.dimension.x");
    }

    @Test
    void aDateLikeWordInAnIdIsAnIdAndNotADateField() throws Exception {
        ObjectNode root = fixture();

        assertThat(ANCHOR).contains("date");
        assertThat(MethodologyProfileRegistry.fromJson(root.toString())
                        .profile(PACK, V1)
                        .requiredProbeIds())
                .contains(ANCHOR);
        assertThat(schema().validate(root)).isEmpty();
    }

    @Test
    void profileLookupByPackAndRuleVersionIsExact() throws Exception {
        MethodologyProfileRegistry registry = load();

        assertThat(registry.profile(PACK, V1).ruleVersion()).isEqualTo(V1);
        assertThat(registry.profile(PACK, V1_1).ruleVersion()).isEqualTo(V1_1);
        assertThat(registry.profile(PACK, V1)).isNotEqualTo(registry.profile(PACK, V1_1));
        for (String[] wrong : new String[][] {
            {PACK, "fixture-pack@1.0"},
            {PACK, "fixture-pack@1.0.1"},
            {PACK, "fixture-pack@2.0.0"},
            {PACK, "FIXTURE-PACK@1.0.0"},
            {PACK, " fixture-pack@1.0.0"},
            {PACK, "fixture-pack@1.0.0 "},
            {PACK, PACK},
            {PACK, "1.0.0"},
            {"fixture", V1},
            {"fixture-pack-2", V1},
            {"other-pack", V1},
            {"other-pack", "other-pack@1.0.0"},
            {"", ""}
        }) {
            assertThatThrownBy(() -> registry.profile(wrong[0], wrong[1]))
                    .as(wrong[0] + " " + wrong[1])
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no methodology profile for");
        }
    }

    @Test
    void aFileIsReadAsUtf8AndAMissingOneIsAnIoErrorNotAnInvalidDocument(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("profiles.json");
        Files.writeString(file, fixtureText(), StandardCharsets.UTF_8);
        Path missing = directory.resolve("nope.json");

        assertThat(MethodologyProfileRegistry.load(file).profile(PACK, V1).ruleVersion())
                .isEqualTo(V1);
        assertThatThrownBy(() -> MethodologyProfileRegistry.load(missing)).isInstanceOf(IOException.class);
    }

    @Test
    void theProfilesAreDevToolingAndTheirSchemaDoesNotShipInTheJar() {
        assertThat(getClass().getResource("/indicators/release-gates.json")).isNotNull();
        assertThat(getClass().getResource("/indicators/siaps-methodology-profiles.schema.json"))
                .isNull();
        assertThat(getClass().getResource("/indicators/siaps-methodology-profiles.json"))
                .isNull();
    }

    // ---- strict reading

    @Test
    void aRepeatedKeyTrailingContentAndSomethingThatIsNotAnObjectAreRefused() throws Exception {
        String text = fixtureText();
        String repeated =
                text.replace("\"schema_version\": \"1\",", "\"schema_version\": \"1\", \"schema_version\": \"1\",");
        String trailing = text + " {}";

        assertThat(repeated).isNotEqualTo(text);
        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(repeated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(trailing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson("nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson("[]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("document must be an object");
    }

    @Test
    void aValueOfTheWrongJsonTypeIsNeverCoercedToAString() throws Exception {
        ObjectNode root = fixture();
        dimension(root, 0).put("local_reading", 7);
        String json = root.toString();

        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profiles[0].dimensions[0]")
                .hasMessageContaining("local_reading must be a string");
        assertThat(schema().validate(root)).isNotEmpty();
    }

    @Test
    void aMessageNamesThePathOfTheObjectThatBreaksTheContract() throws Exception {
        ObjectNode root = fixture();
        reading(root, Q_2026, CREDIT).put("reading", "MAYBE");
        String json = root.toString();

        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profiles[0].official_readings.2026Q1.readings.fx.team.credit.reading MAYBE");
    }

    // ---- the schema and the loader refuse the same documents

    @Test
    void theFixtureSatisfiesTheSchemaAndTheLoader() throws Exception {
        assertThat(schema().validate(fixture())).isEmpty();
        assertThat(load().profiles()).hasSize(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedByBoth")
    void theSchemaRefusesWhatTheLoaderRefusesWhereverJsonSchemaCanStateTheRule(Bad bad) throws Exception {
        ObjectNode document = fixture();
        bad.breaking().accept(document);
        String json = document.toString();

        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bad.loaderSays());
        assertThat(schema().validate(document)).as(bad.name()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedByTheLoaderOnly")
    void theLoaderAloneRefusesWhatNeedsMoreThanTheShapeOfOneDocument(Bad bad) throws Exception {
        ObjectNode document = fixture();
        bad.breaking().accept(document);
        String json = document.toString();

        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bad.loaderSays());
        assertThat(schema().validate(document)).as(bad.name()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dateLikeFields")
    void aDateLikeFieldIsRefusedByBothWhereverItIsPutBecauseNothingIsSelectedByADate(DateField date) throws Exception {
        ObjectNode document = fixture();
        date.into().apply(document).put(date.field(), "2026-01-01");
        String json = document.toString();

        assertThatThrownBy(() -> MethodologyProfileRegistry.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has unknown field " + date.field())
                .hasMessageContaining("dates are metadata");
        assertThat(schema().validate(document)).as(date.name()).isNotEmpty();
    }

    @Test
    void theSchemaEnumsAreTheJavaEnumsAndTheSchemaNamesTheContractFields() throws Exception {
        JsonNode defs = MAPPER.readTree(SCHEMA).get("$defs");

        assertThat(enumOf(defs.get("source").get("properties").get("kind")))
                .containsExactlyInAnyOrderElementsOf(names(Source.DocumentKind.values()));
        assertThat(enumOf(defs.get("dataTiming").get("properties").get("kind")))
                .containsExactlyInAnyOrderElementsOf(names(DataTiming.TimingKind.values()));
        assertThat(enumOf(defs.get("reading").get("properties").get("reading")))
                .containsExactlyInAnyOrderElementsOf(names(OfficialReading.values()));
        assertThat(MAPPER.readTree(SCHEMA).get("required"))
                .extracting(JsonNode::asString)
                .containsExactly("schema_version", "sources", "profiles");
        assertThat(defs.get("dataTiming").get("properties").propertyNames())
                .as("a data-timing item has no probe")
                .doesNotContain("probe_id");
    }

    private static List<String> enumOf(JsonNode property) {
        List<String> values = new ArrayList<>();
        property.get("enum").forEach(value -> values.add(value.asString()));
        return values;
    }

    private static List<String> names(Enum<?>... constants) {
        return Stream.of(constants).map(Enum::name).toList();
    }

    /**
     * A schema pattern that refuses a real compiled version would block the production profiles, and
     * invented values would not show it: every registered pack and rule version, the Nota Final
     * included, is a valid profile for both.
     */
    @Test
    void everyCompiledPackAndRuleVersionIsAcceptedByTheSchemaAndTheLoader() throws Exception {
        List<PackDescriptor> compiled = ReleaseGateRegistry.registeredPacks();
        ObjectNode root = fixture();
        ArrayNode all = MAPPER.createArrayNode();
        for (PackDescriptor descriptor : compiled) {
            ObjectNode copy = profile(root).deepCopy();
            copy.put("pack", descriptor.id()).put("rule_version", descriptor.ruleVersion());
            all.add(copy);
        }
        root.set("profiles", all);

        assertThat(compiled).hasSize(8);
        assertThat(schema().validate(root)).isEmpty();
        MethodologyProfileRegistry registry = MethodologyProfileRegistry.fromJson(root.toString());
        for (PackDescriptor descriptor : compiled) {
            assertThat(registry.profile(descriptor.id(), descriptor.ruleVersion())
                            .pack())
                    .isEqualTo(descriptor.id());
        }
    }

    // ---- the documents, grouped by what they break

    /** What the schema states and the loader checks the same way. */
    static Stream<Bad> refusedByBoth() {
        return Stream.of(
                new Bad(
                        "another schema_version",
                        root -> root.put("schema_version", "2"),
                        "unsupported schema_version 2"),
                new Bad("a missing top-level field", root -> root.remove("sources"), "document needs sources"),
                new Bad(
                        "an unknown top-level field",
                        root -> root.put("notes", "x"),
                        "document has unknown field notes"),
                new Bad("no sources at all", root -> root.putArray("sources"), "sources must not be empty"),
                new Bad("no profiles at all", root -> root.putArray("profiles"), "profiles must not be empty"),
                new Bad(
                        "a profile without dimensions",
                        root -> profile(root).putArray("dimensions"),
                        "has no methodology dimension"),
                new Bad(
                        "a profile without the data_timing field",
                        root -> profile(root).remove("data_timing"),
                        "needs data_timing"),
                new Bad(
                        "a profile without the declared_limitations field",
                        root -> profile(root).remove("declared_limitations"),
                        "needs declared_limitations"),
                new Bad(
                        "a profile without the declared_conventions field",
                        root -> profile(root).remove("declared_conventions"),
                        "needs declared_conventions"),
                new Bad(
                        "a limitation without decision_refs",
                        root -> limitation(root, 0).set("decision_refs", array()),
                        "needs at least one decision_ref"),
                new Bad(
                        "a limitation without its text",
                        root -> limitation(root, 0).remove("what"),
                        "needs what"),
                new Bad(
                        "a limitation id that is not a stable id",
                        root -> limitation(root, 0).put("id", "Person"),
                        "declared limitation id must look like"),
                new Bad(
                        "a limitation with a probe_id",
                        root -> limitation(root, 0).put("probe_id", ANCHOR),
                        "has unknown field probe_id"),
                new Bad(
                        "a convention without decision_refs",
                        root -> convention(root, 0).set("decision_refs", array()),
                        "needs at least one decision_ref"),
                new Bad(
                        "a convention without its research_ref",
                        root -> convention(root, 0).remove("research_ref"),
                        "needs research_ref"),
                new Bad(
                        "a convention with a blank research_ref",
                        root -> convention(root, 0).put("research_ref", " "),
                        "research_ref of fx.convention.team-type-date must not be blank"),
                new Bad(
                        "a convention probe_id that is not a stable id",
                        root -> convention(root, 0).put("probe_id", "team"),
                        "probe_id of fx.convention.team-type-date must look like"),
                new Bad(
                        "a data-timing item that names a probe_id",
                        root -> timing(root, 0).put("probe_id", ANCHOR),
                        "profiles[0].data_timing[0] names a probe_id"),
                new Bad(
                        "a timing item of a kind that is not one",
                        root -> timing(root, 1).put("kind", "SOMETIMES"),
                        "SOMETIMES is not one of"),
                new Bad(
                        "a reading outside SAME, DIFFERENT and UNKNOWN",
                        root -> reading(root, Q_2026, CREDIT).put("reading", "MAYBE"),
                        "MAYBE is not one of [SAME, DIFFERENT, UNKNOWN]"),
                new Bad(
                        "a lower-case reading",
                        root -> reading(root, Q_2026, CREDIT).put("reading", "same"),
                        "same is not one of [SAME, DIFFERENT, UNKNOWN]"),
                new Bad(
                        "DIFFERENT without the official reading text",
                        root -> reading(root, Q_2026, SAME_DAY).remove("official_reading_text"),
                        "a DIFFERENT reading needs official_reading_text"),
                new Bad(
                        "DIFFERENT with a blank official reading text",
                        root -> reading(root, Q_2026, SAME_DAY).put("official_reading_text", "  "),
                        "a DIFFERENT reading needs official_reading_text"),
                new Bad(
                        "an official reading text on a SAME reading",
                        root -> reading(root, Q_2026, CREDIT).put("official_reading_text", "the same"),
                        "only a DIFFERENT reading carries official_reading_text"),
                new Bad(
                        "an official reading text on an UNKNOWN reading",
                        root -> reading(root, Q_2025, CREDIT).put("official_reading_text", "unknown"),
                        "only a DIFFERENT reading carries official_reading_text"),
                new Bad(
                        "a null official reading text, which is not an omitted one",
                        root -> reading(root, Q_2026, CREDIT).putNull("official_reading_text"),
                        "official_reading_text must be a string"),
                new Bad(
                        "a reading without its source_ref",
                        root -> reading(root, Q_2026, CREDIT).remove("source_ref"),
                        "needs source_ref"),
                new Bad(
                        "a reading that is not an object",
                        root -> readings(root, Q_2026).put(CREDIT, "SAME"),
                        "fx.team.credit must be an object"),
                new Bad(
                        "no official readings at all",
                        root -> profile(root).putObject("official_readings"),
                        "declares no official reading of any quadrimestre"),
                new Bad(
                        "a quadrimestre that does not exist",
                        root -> renameKey((ObjectNode) profile(root).get("official_readings"), Q_2026, "2026Q4"),
                        "not a quadrimestre like 2026Q1"),
                new Bad(
                        "a quadrimestre key that picks the latest",
                        root -> renameKey((ObjectNode) profile(root).get("official_readings"), Q_2026, "latest"),
                        "not a quadrimestre like 2026Q1"),
                new Bad(
                        "a quadrimestre that classifies none of the dimensions",
                        root -> readings(root, Q_2025).removeAll(),
                        "2025Q3 does not classify dimension"),
                new Bad(
                        "an edition without sources",
                        root -> edition(root, Q_2026).putArray("source_refs"),
                        "needs source_refs that name each source once"),
                new Bad(
                        "an edition that names a source twice",
                        root -> edition(root, Q_2026).set("source_refs", array(NT_2026, NT_2026)),
                        "needs source_refs that name each source once"),
                new Bad(
                        "a source with a plain http url",
                        root -> source(root, 0).put("url", "http://example.org/ficha"),
                        "url of source fx-ficha-2025 must look like https://..."),
                new Bad(
                        "a source with a publication that is not date-shaped",
                        root -> source(root, 0).put("published", "03/02/2025"),
                        "published 03/02/2025 is not an ISO date"),
                new Bad(
                        "a source of a kind that is not one",
                        root -> source(root, 0).put("kind", "BLOG"),
                        "BLOG is not one of"),
                new Bad(
                        "a source without a title",
                        root -> source(root, 0).put("title", " "),
                        "title of source fx-ficha-2025 must not be blank"),
                new Bad(
                        "a dimension id in upper case",
                        root -> dimension(root, 0).put("id", "FX.Cohort"),
                        "dimension id must look like c2.cohort.second-birthday"),
                new Bad(
                        "a probe_id that is not a stable id",
                        root -> dimension(root, 0).put("probe_id", "anchor"),
                        "probe_id of fx.cohort.anchor-date must look like"),
                new Bad(
                        "a blank local reading",
                        root -> dimension(root, 0).put("local_reading", " "),
                        "local_reading of fx.cohort.anchor-date must not be blank"),
                new Bad(
                        "a decision ref in lower case",
                        root -> dimension(root, 0).set("decision_refs", array("amb-fx-01")),
                        "decision_ref of fx.cohort.anchor-date must look like AMB-C2-03"),
                new Bad(
                        "a decision ref listed twice",
                        root -> dimension(root, 1).set("decision_refs", array("AMB-FX-02", "AMB-FX-02")),
                        "decision_ref AMB-FX-02 of fx.visits.same-day is listed twice"),
                new Bad(
                        "a rule_version that is not <pack>@<x.y.z>",
                        root -> profile(root).put("rule_version", "fixture-pack@1.0"),
                        "rule_version must look like"));
    }

    /** What needs more than the shape of one document: uniqueness, cross-references and agreement between fields. */
    static Stream<Bad> refusedByTheLoaderOnly() {
        return Stream.of(
                new Bad(
                        "a second profile for the same pack and rule_version",
                        root -> profiles(root).add(profile(root).deepCopy()),
                        "duplicate profile for pack fixture-pack rule_version fixture-pack@1.0.0"),
                new Bad(
                        "a rule_version that is not of its pack",
                        root -> ((ObjectNode) profiles(root).get(1)).put("rule_version", "other-pack@1.1.0"),
                        "rule_version other-pack@1.1.0 must be fixture-pack@<version>"),
                new Bad(
                        "a repeated dimension id",
                        root -> dimension(root, 1).put("id", ANCHOR),
                        "duplicate dimension id fx.cohort.anchor-date"),
                new Bad(
                        "a probe shared by two dimensions of a profile",
                        root -> dimension(root, 1).put("probe_id", ANCHOR),
                        "duplicate probe_id fx.cohort.anchor-date across the dimensions"),
                new Bad(
                        "a repeated declared limitation id",
                        root -> ((ArrayNode) profile(root).get("declared_limitations"))
                                .add(limitation(root, 0).deepCopy()),
                        "duplicate declared limitation id " + LIMITATION),
                new Bad(
                        "a convention id that is a dimension id",
                        root -> convention(root, 0).put("id", ANCHOR),
                        "id " + ANCHOR + " is both a convention and a dimension or data-timing item"),
                new Bad(
                        "a convention id that is a data-timing id",
                        root -> convention(root, 0).put("id", CUTOFF),
                        "id " + CUTOFF + " is both a convention and a dimension or data-timing item"),
                new Bad(
                        "a repeated declared convention id",
                        root -> ((ArrayNode) profile(root).get("declared_conventions"))
                                .add(convention(root, 0).deepCopy()),
                        "duplicate declared convention id " + CONVENTION),
                new Bad(
                        "a convention probe_id that is the probe of a dimension",
                        root -> convention(root, 0).put("probe_id", SAME_DAY),
                        "probe_id " + SAME_DAY + " of convention " + CONVENTION + " is also the probe of a dimension"),
                new Bad(
                        "a convention probe_id used by two conventions",
                        root -> ((ArrayNode) profile(root).get("declared_conventions"))
                                .add(convention(root, 0).deepCopy().put("id", "fx.convention.other")),
                        "duplicate probe_id " + CONVENTION + " across the conventions"),
                new Bad(
                        "a quadrimestre that reads a declared convention",
                        root -> readings(root, Q_2026).set(CONVENTION, sameReading(NT_2026)),
                        "2026Q1 reads " + CONVENTION + ", a declared convention"),
                new Bad(
                        "an id that is a dimension and a data-timing item",
                        root -> timing(root, 0).put("id", CREDIT),
                        "id fx.team.credit is both a dimension and a data-timing item"),
                new Bad(
                        "a repeated data-timing id",
                        root -> timing(root, 1).put("id", CUTOFF),
                        "duplicate data-timing id fx.timing.transmission-cutoff"),
                new Bad(
                        "a reading of a dimension the profile does not have",
                        root -> readings(root, Q_2026).set("fx.bogus.thing", sameReading(NT_2026)),
                        "2026Q1 reads unknown dimension fx.bogus.thing"),
                new Bad(
                        "a reading of a data-timing item",
                        root -> readings(root, Q_2026).set(REPROCESSING, sameReading(NT_2026)),
                        "2026Q1 reads fx.timing.reprocessing, a data-timing item"),
                new Bad(
                        "a quadrimestre that leaves a dimension unclassified",
                        root -> readings(root, Q_2026).remove(CREDIT),
                        "2026Q1 does not classify dimension fx.team.credit"),
                new Bad(
                        "a source_ref that does not resolve to sources[]",
                        root -> reading(root, Q_2026, CREDIT).put("source_ref", "fx-nowhere"),
                        "fx-nowhere does not resolve to sources[]"),
                new Bad(
                        "an edition source that does not resolve to sources[]",
                        root -> edition(root, Q_2026).set("source_refs", array(NT_2026, "fx-nowhere")),
                        "fx-nowhere does not resolve to sources[]"),
                new Bad(
                        "a reading that cites a source its edition does not list",
                        root -> reading(root, Q_2026, ANCHOR).put("source_ref", FICHA_2025),
                        "cites source fx-ficha-2025, which is not among the sources of edition fixture-2026-v1"),
                new Bad(
                        "a source id used twice",
                        root -> source(root, 1).put("id", FICHA_2025),
                        "duplicate source id fx-ficha-2025"),
                new Bad(
                        "a publication that is date-shaped and not a date",
                        root -> source(root, 0).put("published", "2025-02-30"),
                        "published 2025-02-30 is not an ISO date"));
    }

    /** One date-like field per kind of object of the contract: the loader says the same thing everywhere. */
    static Stream<DateField> dateLikeFields() {
        return Stream.of(
                new DateField("the document", root -> root, "latest"),
                new DateField("a source", root -> source(root, 0), "valid_until"),
                new DateField("a profile", MethodologyProfileRegistryTest::profile, "valid_from"),
                new DateField("a dimension", root -> dimension(root, 0), "effective_date"),
                new DateField("a data-timing item", root -> timing(root, 0), "signed_at"),
                new DateField("a quadrimestre", root -> quadrimestre(root, Q_2026), "applies_since"),
                new DateField("an edition", root -> edition(root, Q_2026), "floor_date"),
                new DateField("a reading", root -> reading(root, Q_2026, CREDIT), "deadline"));
    }

    private static void renameKey(ObjectNode object, String from, String to) {
        object.set(to, object.remove(from));
    }
}
