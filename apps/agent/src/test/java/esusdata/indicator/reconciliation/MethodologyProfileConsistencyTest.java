package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c3.C3MethodologyProbes;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c4.C4MethodologyProbes;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.pack.c5.C5MethodologyProbes;
import esusdata.indicator.pack.c5.C5Pack;
import esusdata.indicator.pack.c6.C6Pack;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredConvention;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredLimitation;
import esusdata.indicator.reconciliation.MethodologyProfile.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The production methodology profiles ({@code contracts/indicators/siaps-methodology-profiles.json},
 * spec 2026-10-08 §8.3, ADR 0034 §§3-8) against everything they must agree with, so that any drift
 * fails loudly: the compiled rule versions, the note of official editions they were written from
 * ({@code docs/indicadores/portoes/edicoes-oficiais-siaps.md}: its headings, its K3 marks, its
 * out-of-reach table), the probe catalog, the probes' declared limitations and the placeholder
 * rule of the user decision (b').
 */
class MethodologyProfileConsistencyTest {

    private static final Path CONTRACTS = Path.of("..", "..", "contracts", "indicators");
    private static final Path PROFILES = CONTRACTS.resolve("siaps-methodology-profiles.json");
    private static final Path SCHEMA = CONTRACTS.resolve("siaps-methodology-profiles.schema.json");
    private static final Path NOTE = Path.of("..", "..", "docs", "indicadores", "portoes", "edicoes-oficiais-siaps.md");
    private static final Path REGISTERS = Path.of("..", "..", "docs", "indicadores", "decisoes");

    /** The convention every pack shares, K3 in section 5 of the note. */
    private static final String COMMON_CONVENTION = CommonMethodologyProbes.TEAM_TYPE_REFERENCE_DATE;

    /**
     * The convention probes another slice adds (C6 and C7). Until it is integrated the catalog
     * does not hold them yet; remove this set then, and the test demands them.
     */
    private static final Set<String> PENDING_INTEGRATION =
            Set.of("c6.age.birthday-rule", "c6.team.eap-credit", "c7.age-and-window.civil");

    /**
     * The structural blockers of the user decision (b'): dimensions this installation cannot
     * observe completely in any quadrimestre, and the only values a placeholder may name.
     */
    private static final Map<String, Set<String>> STRUCTURAL = Map.of(
            C2Pack.ID, Set.of("c2.consult.puericultura-filter"),
            C3Pack.ID, Set.of("c3.codes.pregnancy-puerperium", "c3.miac.counting-rule"),
            C4Pack.ID, Set.of("c4.condition.code-list", "c4.condition.entry-history"),
            C5Pack.ID, Set.of("c5.condition.entry-history"));

    private static final Map<String, String> COMPILED = compiled();

    private static final Pattern HEADING = Pattern.compile("^### 6\\.\\d+ .*\\(`([a-z0-9-]+)@([0-9.]+)`");
    private static final Pattern ROW = Pattern.compile("^\\| `([a-z0-9.\\-]+)` \\(n(\\d+)\\) \\|");
    private static final Pattern NOTE_START = Pattern.compile("^(?:- )?n(\\d+)\\. ");
    private static final Pattern OOR_ROW = Pattern.compile("^\\| `(oor\\.[a-z0-9.\\-]+)` \\|[^|]*\\| ([^|]*) \\|");

    private static final int NO_NOTE = -1;

    private static MethodologyProfileRegistry registry;
    private static List<String> noteLines;

    @BeforeAll
    static void load() throws IOException {
        registry = MethodologyProfileRegistry.load(PROFILES);
        noteLines = Files.readAllLines(NOTE);
    }

    private static Map<String, String> compiled() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put(GatePack.all().get(0).packId(), C1Rule.RULE_VERSION);
        versions.put(C2Pack.ID, C2Pack.RULE_VERSION);
        versions.put(C3Pack.ID, C3Pack.RULE_VERSION);
        versions.put(C4Pack.ID, C4Pack.RULE_VERSION);
        versions.put(C5Pack.ID, C5Pack.RULE_VERSION);
        versions.put(C6Pack.ID, C6Pack.RULE_VERSION);
        versions.put(C7Pack.ID, C7Pack.RULE_VERSION);
        versions.put(ComponentIII.ID, ComponentIII.RULE_VERSION);
        return versions;
    }

    private static MethodologyProfile profileOf(String packId) {
        return registry.profile(packId, COMPILED.get(packId));
    }

    // ---- the file

    @Test
    void theProfilesLoadAndValidateAgainstTheSchema() throws IOException {
        JsonNode document = new ObjectMapper().readTree(Files.readString(PROFILES));
        Schema schema;
        try (InputStream stream = Files.newInputStream(SCHEMA)) {
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(stream);
        }

        assertThat(schema.validate(document)).isEmpty();
        assertThat(registry.profiles()).isNotEmpty();
    }

    @Test
    void thereIsOneProfileForEachCompiledRuleVersionAndNoOtherOne() {
        assertThat(registry.profiles())
                .extracting(MethodologyProfile::pack, MethodologyProfile::ruleVersion)
                .containsExactlyInAnyOrderElementsOf(COMPILED.entrySet().stream()
                        .map(entry -> org.assertj.core.groups.Tuple.tuple(entry.getKey(), entry.getValue()))
                        .toList());
        assertThat(COMPILED.keySet())
                .containsExactlyInAnyOrderElementsOf(GatePack.allWithNotaFinal().stream()
                        .map(GatePack::packId)
                        .toList());
    }

    @Test
    void theHeadingsOfTheNoteNameTheCompiledRuleVersions() {
        Map<String, String> headings = new LinkedHashMap<>();
        for (String line : noteLines) {
            Matcher heading = HEADING.matcher(line);
            if (heading.find()) {
                headings.put(heading.group(1), heading.group(1) + "@" + heading.group(2));
            }
        }

        assertThat(headings).isEqualTo(COMPILED);
    }

    @Test
    void everyDecisionRefExistsInTheRegisters() throws IOException {
        String registers = allRegisterText();
        Set<String> refs = new TreeSet<>();
        for (MethodologyProfile profile : registry.profiles()) {
            profile.dimensions().forEach(dimension -> refs.addAll(dimension.decisionRefs()));
            profile.dataTiming().forEach(item -> refs.addAll(item.decisionRefs()));
            profile.declaredLimitations().forEach(limitation -> refs.addAll(limitation.decisionRefs()));
            profile.declaredConventions().forEach(convention -> refs.addAll(convention.decisionRefs()));
        }

        assertThat(refs).isNotEmpty().allSatisfy(ref -> assertThat(registers).contains(ref));
    }

    private static String allRegisterText() throws IOException {
        StringBuilder text = new StringBuilder();
        try (Stream<Path> files = Files.list(REGISTERS)) {
            for (Path file : files.toList()) {
                text.append(Files.readString(file));
            }
        }
        Path methodology = REGISTERS.resolveSibling("..").resolve("metodologia");
        try (Stream<Path> files = Files.list(methodology)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".md")).toList()) {
                text.append(Files.readString(file));
            }
        }
        return text.toString();
    }

    // ---- probes

    @Test
    void everyProbeAProfileNamesResolvesInTheCatalogOfItsPack() {
        for (MethodologyProfile profile : registry.profiles()) {
            Set<String> catalog = catalogIds(profile.pack());
            Set<String> missing = new TreeSet<>(namedProbes(profile));
            missing.removeAll(catalog);
            missing.removeAll(PENDING_INTEGRATION);

            assertThat(missing)
                    .as("probes of %s missing from the catalog", profile.ruleVersion())
                    .isEmpty();
        }
    }

    @Test
    void everyProbeOfTheCatalogIsNamedByAProfileOfItsPack() {
        for (MethodologyProfile profile : registry.profiles()) {
            Set<String> extra = new TreeSet<>(catalogIds(profile.pack()));
            extra.removeAll(namedProbes(profile));

            assertThat(extra)
                    .as("catalog probes of %s no profile names", profile.ruleVersion())
                    .isEmpty();
        }
        assertThat(MethodologyProbeCatalog.all())
                .allSatisfy(probe -> assertThat(probe.packs()).isNotEmpty().isSubsetOf(COMPILED.keySet()));
    }

    @Test
    void theOnlyProbesMissingFromTheCatalogAreThoseAnotherSliceIsAdding() {
        Set<String> named = new TreeSet<>();
        Set<String> inCatalog = new TreeSet<>();
        for (MethodologyProfile profile : registry.profiles()) {
            named.addAll(namedProbes(profile));
            inCatalog.addAll(catalogIds(profile.pack()));
        }
        named.removeAll(inCatalog);

        assertThat(PENDING_INTEGRATION).containsAll(named);
    }

    private static Set<String> catalogIds(String packId) {
        return MethodologyProbeCatalog.forPack(packId).stream()
                .map(MethodologyProbe::id)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> namedProbes(MethodologyProfile profile) {
        Set<String> named = new TreeSet<>(profile.requiredProbeIds());
        named.addAll(profile.conventionProbeIds());
        return named;
    }

    @Test
    void everyLimitationAProbeCitesIsDeclaredInTheProfileOfItsPack() {
        Map<String, Set<String>> cited = Map.of(
                C3Pack.ID, Set.of(C3MethodologyProbes.LIMITATION),
                C4Pack.ID, Set.copyOf(C4MethodologyProbes.LIMITATIONS),
                C5Pack.ID, Set.copyOf(C5MethodologyProbes.LIMITATIONS));

        cited.forEach((pack, limitations) -> {
            Set<String> declared = profileOf(pack).declaredLimitations().stream()
                    .map(DeclaredLimitation::id)
                    .collect(Collectors.toSet());

            assertThat(declared).as("limitations of %s", pack).containsAll(limitations);
        });
    }

    // ---- placeholders

    @Test
    void aPlaceholderNeverServesADimensionThatReadsDifferentAndItsBlockersAreStructural() {
        List<PlaceholderProbe> placeholders = MethodologyProbeCatalog.all().stream()
                .filter(PlaceholderProbe.class::isInstance)
                .map(PlaceholderProbe.class::cast)
                .toList();

        assertThat(placeholders).hasSize(10);
        for (PlaceholderProbe placeholder : placeholders) {
            String pack = placeholder.packs().iterator().next();
            MethodologyProfile profile = profileOf(pack);
            Dimension served = dimensionOfProbe(profile, placeholder.id()).orElseThrow();

            assertThat(readings(profile, served)).as(placeholder.id()).doesNotContain(OfficialReading.DIFFERENT);
            assertThat(STRUCTURAL.getOrDefault(pack, Set.of())).containsAll(placeholder.blockers());
            for (String blocker : placeholder.blockers()) {
                Dimension dimension = dimensionOfProbe(profile, blocker).orElseThrow();

                assertThat(readings(profile, dimension))
                        .as("%s blocks %s", blocker, placeholder.id())
                        .containsAnyOf(OfficialReading.UNKNOWN, OfficialReading.DIFFERENT);
            }
        }
    }

    @Test
    void theNotaFinalHasASiblingPackWithAStructuralBlockerInEveryQuadrimestre() {
        MethodologyProfile notaFinal = profileOf(ComponentIII.ID);

        for (String quadrimestre : notaFinal.officialReadings().keySet()) {
            List<String> packsWithBlocker = STRUCTURAL.entrySet().stream()
                    .filter(entry -> blocksIn(profileOf(entry.getKey()), entry.getValue(), quadrimestre))
                    .map(Map.Entry::getKey)
                    .toList();

            assertThat(packsWithBlocker).as(quadrimestre).isNotEmpty();
        }
    }

    private static boolean blocksIn(MethodologyProfile profile, Set<String> structural, String quadrimestre) {
        return profile.officialEdition(quadrimestre)
                .map(edition -> structural.stream()
                        .anyMatch(id -> dimensionOfProbe(profile, id)
                                .map(dimension ->
                                        edition.readings().get(dimension.id()).reading() != OfficialReading.SAME)
                                .orElse(false)))
                .orElse(false);
    }

    private static Optional<Dimension> dimensionOfProbe(MethodologyProfile profile, String probeId) {
        return profile.dimensions().stream()
                .filter(dimension -> dimension.probeId().filter(probeId::equals).isPresent())
                .findFirst();
    }

    private static Set<OfficialReading> readings(MethodologyProfile profile, Dimension dimension) {
        return profile.officialReadings().values().stream()
                .map(edition -> edition.readings().get(dimension.id()).reading())
                .collect(Collectors.toSet());
    }

    // ---- the note

    @Test
    void theConventionsAreExactlyTheK3RowsOfTheNotePlusTheCommonOne() {
        Map<String, Set<String>> k3 = k3RowsByPack();

        assertThat(k3.keySet()).containsExactlyInAnyOrderElementsOf(COMPILED.keySet());
        k3.forEach((pack, expected) -> {
            Set<String> declared = profileOf(pack).declaredConventions().stream()
                    .map(DeclaredConvention::id)
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<String> wanted = new TreeSet<>(expected);
            wanted.add(COMMON_CONVENTION);

            assertThat(declared).as("conventions of %s", pack).isEqualTo(wanted);
        });
    }

    @Test
    void theCommonConventionIsK3InTheNoteAndNoDimensionOfAProfileIsK3() {
        Map<String, Boolean> sectionFive = k3Marks("## 5.");
        Map<String, Set<String>> k3 = k3RowsByPack();

        assertThat(sectionFive).containsEntry(COMMON_CONVENTION, true);
        k3.forEach((pack, ids) -> assertThat(profileOf(pack).dimensions())
                .extracting(Dimension::id)
                .doesNotContainAnyElementsOf(ids));
    }

    @Test
    void theDeclaredLimitationsAreTheOutOfReachTableOfTheNotePerPack() {
        Map<String, Set<String>> byPack = new TreeMap<>();
        for (String line : noteLines) {
            Matcher row = OOR_ROW.matcher(line);
            if (row.find()) {
                for (String code : packCodes(row.group(2))) {
                    byPack.computeIfAbsent(code, key -> new TreeSet<>()).add(row.group(1));
                }
            }
        }
        Map<String, Set<String>> declared = new TreeMap<>();
        for (MethodologyProfile profile : registry.profiles()) {
            declared.put(
                    codeOf(profile.pack()),
                    profile.declaredLimitations().stream()
                            .map(DeclaredLimitation::id)
                            .collect(Collectors.toCollection(TreeSet::new)));
        }

        assertThat(declared).isEqualTo(byPack);
    }

    private static String codeOf(String packId) {
        return GatePack.allWithNotaFinal().stream()
                .filter(pack -> pack.packId().equals(packId))
                .findFirst()
                .orElseThrow()
                .code();
    }

    /** The packs of the «Packs» column of the out-of-reach table: «C2 a C7», «C3, C4, C5», «C4», «Nota Final». */
    private static List<String> packCodes(String column) {
        String text = column.replaceAll("\\(.*\\)", "").strip();
        if ("Nota Final".equals(text)) {
            return List.of("CIII");
        }
        if (text.contains(" a ")) {
            int from = Integer.parseInt(text.substring(1, 2));
            int to = Integer.parseInt(text.substring(text.length() - 1));
            List<String> codes = new ArrayList<>();
            for (int i = from; i <= to; i++) {
                codes.add("C" + i);
            }
            return codes;
        }
        return List.of(text.split(",\\s*"));
    }

    /** The ids of the rows tagged (K3) in the note of their section, by pack id, read mechanically. */
    private static Map<String, Set<String>> k3RowsByPack() {
        Map<String, Set<String>> byPack = new LinkedHashMap<>();
        marksBySection().forEach((heading, marks) -> {
            Matcher match = HEADING.matcher(heading);
            if (match.find()) {
                byPack.put(
                        match.group(1),
                        marks.entrySet().stream()
                                .filter(Map.Entry::getValue)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toCollection(TreeSet::new)));
            }
        });
        return byPack;
    }

    /** Whether the note of section five tags each row (K3). */
    private static Map<String, Boolean> k3Marks(String headingStart) {
        return marksBySection().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(headingStart))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    /**
     * Each row of the matrices (section five and the sections 6.x), by heading, with whether its
     * note is tagged (K3): the note {@code nK} of row {@code (nK)} runs from its line to the next
     * blank line or note.
     */
    private static Map<String, Map<String, Boolean>> marksBySection() {
        Map<String, Map<String, Boolean>> sections = new LinkedHashMap<>();
        Map<Integer, String> rows = new LinkedHashMap<>();
        Map<Integer, StringBuilder> notes = new LinkedHashMap<>();
        String heading = "";
        int current = NO_NOTE;
        for (String line : noteLines) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                close(sections, heading, rows, notes);
                rows.clear();
                notes.clear();
                current = NO_NOTE;
                heading = line;
                continue;
            }
            Matcher row = ROW.matcher(line);
            Matcher start = NOTE_START.matcher(line);
            if (row.find()) {
                rows.put(Integer.parseInt(row.group(2)), row.group(1));
            } else if (start.find()) {
                current = Integer.parseInt(start.group(1));
                notes.computeIfAbsent(current, key -> new StringBuilder()).append(line);
            } else if (line.isBlank()) {
                current = NO_NOTE;
            } else if (current != NO_NOTE) {
                notes.get(current).append(' ').append(line.strip());
            }
        }
        close(sections, heading, rows, notes);
        return sections;
    }

    private static void close(
            Map<String, Map<String, Boolean>> sections,
            String heading,
            Map<Integer, String> rows,
            Map<Integer, StringBuilder> notes) {
        if (heading.isEmpty() || rows.isEmpty()) {
            return;
        }
        Map<String, Boolean> marks = new LinkedHashMap<>();
        rows.forEach(
                (n, id) -> marks.put(id, notes.containsKey(n) && notes.get(n).indexOf("(K3)") >= 0));
        sections.put(heading, marks);
    }
}
