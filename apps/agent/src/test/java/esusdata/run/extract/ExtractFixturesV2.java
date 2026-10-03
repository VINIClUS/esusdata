package esusdata.run.extract;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RecordKind;
import esusdata.indicator.model.SourceRef;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CapabilityContract;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds a synthetic canonical v2 extract (ADR 0030) for the replay test of a pack (ENG-19): the
 * parts are exactly what {@link IndicatorRule#requirements} asks for one competência, resolved in
 * the packaged capability descriptors ({@code contracts/compatibility/capabilities}) with the binds
 * and checksums of {@link ManifestChecksums} — what {@code RunExecutor.runFromExtract} demands — and
 * the lines are canonical records ({@link CanonicalFixtures}) written column by column as the
 * execution plane writes them. Every record is checked on {@code add} exactly as the reader will
 * check it (municipality, required columns, scope date inside its part's window), so a fixture
 * mistake fails where it is made.
 *
 * <p>A record goes to the part of its kind; when two parts of the rule read the same kind ({@code
 * care_encounter}/{@code dental_encounter} write {@code care_event}, {@code procedure_performed}/
 * {@code exam_request_evaluation} write {@code procedure_event}), name the capability. The line
 * never carries the source id: the reader namespaces every {@link SourceRef} with the manifest's.
 *
 * <p>Copy this into {@code <Pacote>ReplayTest} (in the pack's test package) and replace the
 * records and the expected counts with the ficha's case — {@code PackReplayTest} runs it as written
 * against the C2 skeleton, {@code ComputingRuleReplayTest} against a rule that computes:
 *
 * <pre>{@code
 * @TempDir Path dataDir;
 *
 * @Test
 * void eng19_replaysASyntheticExtract() throws Exception {
 *     YearMonth competencia = YearMonth.of(2026, 3);
 *     try (JobRunnerTestFixture fixture = new JobRunnerTestFixture(dataDir, Clock.systemUTC())) {
 *         fixture.registerSource("src-1", CanonicalFixtures.IBGE);
 *         fixture.registerPrincipal("gestor", CanonicalFixtures.IBGE);
 *         ExtractionManifest extract = ExtractFixturesV2.forRule(new C2Pack(), competencia)
 *                 .add(CanonicalFixtures.person("p1", LocalDate.of(2024, 5, 10), "FEMININO"))
 *                 .add(CanonicalFixtures.registration("p1", LocalDate.of(2025, 9, 1), "2750325", "0000346268"))
 *                 .add(Capabilities.CARE_ENCOUNTER, // named: needed when two parts read the same kind
 *                         CanonicalFixtures.encounter("p1", LocalDate.of(2026, 2, 10), "225142", false))
 *                 .write(fixture.extractsDir, "ext-c2-2026-03", "src-1");
 *
 *         RunExecutor.RunOutcome run = fixture.replay(extract, new C2Pack(), competencia, "gestor");
 *
 *         PublishedResult published = fixture.published(run.resultId(), CanonicalFixtures.IBGE);
 *         assertThat(published.status()).isEqualTo("BLOCKED"); // gates incomplete: counts, no value
 *         assertThat(published.valueText()).isNull();
 *         assertThat(ResultJson.readComponents(published.componentsJson()))
 *                 .extracting(ResultJson.StoredComponent::code, ResultJson.StoredComponent::numerator,
 *                         ResultJson.StoredComponent::denominator)
 *                 .contains(tuple("B", "0", "1"));
 *         assertThat(ResultJson.readTeams(published.teamResultsJson()))
 *                 .extracting(ResultJson.StoredTeam::ine)
 *                 .containsExactly("0000346268");
 *         assertThat(fixture.evidence(run.resultId(), CanonicalFixtures.IBGE))
 *                 .extracting(EvidenceRecord::subjectKey, EvidenceRecord::component, EvidenceRecord::decision)
 *                 .contains(tuple("p1", "B", "PRACTICE_NOT_MET"));
 *     }
 * }
 * }</pre>
 */
public final class ExtractFixturesV2 {

    /** The instants every fixture manifest records, so a replayed fingerprint is stable. */
    public static final String STARTED_AT = "2026-09-19T12:00:00Z";

    public static final String FINISHED_AT = "2026-09-19T12:00:01Z";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CapabilityCatalog CATALOG = CapabilityCatalog.packaged();

    private ExtractFixturesV2() {}

    /** A builder of the extract {@code rule} reads for {@code competencia}. */
    public static Builder forRule(IndicatorRule rule, YearMonth competencia) {
        return new Builder(rule, competencia);
    }

    /**
     * The manifest parts {@code rule} requires for {@code competencia}, indexed in requirement order,
     * with {@code rowCount} 0: capability, packaged version, query checksum and record kind, window,
     * binds and their checksum.
     */
    public static List<ManifestPart> parts(IndicatorRule rule, YearMonth competencia) {
        List<PartRequirement> requirements = rule.requirements(competencia).parts();
        List<ManifestPart> parts = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            parts.add(part(index, requirements.get(index), 0));
        }
        return List.copyOf(parts);
    }

    /** {@code record} as a line of {@code capability} carries it: one property per descriptor column. */
    public static ObjectNode recordJson(String capability, Record record) {
        CapabilityContract contract = CATALOG.require(capability);
        RecordKind kind = RecordKind.fromWireName(contract.recordKind());
        if (!kind.recordType().isInstance(record)) {
            throw new IllegalArgumentException(
                    capability + " writes " + kind.recordType().getSimpleName() + " records, not "
                            + record.getClass().getSimpleName());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object value = value(component, record);
            if (value instanceof SourceRef ref) {
                values.put(contract.entityTypeColumn(), ref.entityType());
                values.put(contract.recordIdColumn(), ref.recordId());
            } else {
                values.put(CanonicalRecordMapper.snakeCase(component.getName()), value);
            }
        }
        ObjectNode json = MAPPER.createObjectNode();
        for (CapabilityContract.Column column : contract.columns()) {
            put(json, column.name(), values.get(column.name()));
        }
        return json;
    }

    private static ManifestPart part(int index, PartRequirement requirement, long rowCount) {
        CapabilityContract contract = CATALOG.require(requirement.capability());
        SortedMap<String, List<String>> params =
                ManifestChecksums.params(requirement.arrayParams(), requirement.dateParams());
        return new ManifestPart(
                index,
                contract.capability(),
                contract.adapterVersion(),
                contract.queryChecksum(),
                contract.recordKind(),
                requirement.periodStart().toString(),
                requirement.periodEndExclusive().toString(),
                params,
                ManifestChecksums.paramsChecksum(params),
                rowCount);
    }

    private static Object value(RecordComponent component, Record record) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read " + component.getName(), e);
        }
    }

    private static void put(ObjectNode json, String name, Object value) {
        switch (value) {
            case null -> json.putNull(name);
            case String text -> json.put(name, text);
            case Boolean flag -> json.put(name, flag);
            case List<?> list -> {
                ArrayNode array = json.putArray(name);
                list.forEach(element -> array.add(String.valueOf(element)));
            }
            default -> throw new IllegalArgumentException("no JSON form for " + name + " = " + value);
        }
    }

    /** Collects the records of one extract, part by part. */
    public static final class Builder {

        private final IndicatorRule rule;
        private final List<ManifestPart> parts;
        private final Map<String, List<ObjectNode>> lines = new LinkedHashMap<>();
        private String municipalityIbge = CanonicalFixtures.IBGE;

        private Builder(IndicatorRule rule, YearMonth competencia) {
            this.rule = rule;
            this.parts = parts(rule, competencia);
            parts.forEach(part -> lines.put(part.capability(), new ArrayList<>()));
        }

        /** The municipality of the extract and of every record; {@link CanonicalFixtures#IBGE} by default. */
        public Builder municipality(String ibge) {
            this.municipalityIbge = ibge;
            return this;
        }

        /** Adds a record to the one part of the rule that reads its kind. */
        public Builder add(Record record) {
            List<String> capabilities = parts.stream()
                    .filter(part -> RecordKind.fromWireName(part.recordKind())
                            .recordType()
                            .isInstance(record))
                    .map(ManifestPart::capability)
                    .toList();
            if (capabilities.size() != 1) {
                throw new IllegalArgumentException(
                        rule.descriptor().id() + " reads " + record.getClass().getSimpleName() + " through "
                                + capabilities + ": name the capability with add(capability, record)");
            }
            return add(capabilities.getFirst(), record);
        }

        /** Adds a record to the part of {@code capability}, checked as the reader will check it. */
        public Builder add(String capability, Record record) {
            ManifestPart part = parts.stream()
                    .filter(p -> p.capability().equals(capability))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(rule.descriptor().id() + " does not read " + capability));
            ObjectNode json = recordJson(capability, record);
            CanonicalRecordMapper.forContract(CATALOG.require(capability))
                    .map(
                            json,
                            "fixture",
                            municipalityIbge,
                            LocalDate.parse(part.periodStart()),
                            LocalDate.parse(part.periodEndExclusive()));
            lines.get(capability).add(json);
            return this;
        }

        public Builder addAll(Record... records) {
            for (Record record : records) {
                add(record);
            }
            return this;
        }

        /**
         * Writes the data file and its manifest to {@code extractsDir} as {@code extractionId}, for
         * {@code sourceId} — the job's source — and returns the manifest.
         */
        public ExtractionManifest write(Path extractsDir, String extractionId, String sourceId) throws IOException {
            List<ManifestPart> counted = new ArrayList<>(parts.size());
            long total = 0;
            DateWindow span = null;
            try (ExtractWriterV2 writer = new ExtractWriterV2(extractsDir, extractionId)) {
                for (ManifestPart part : parts) {
                    List<ObjectNode> partLines = lines.get(part.capability());
                    for (ObjectNode line : partLines) {
                        writer.write(part.index(), part.recordKind(), line);
                    }
                    counted.add(withRowCount(part, partLines.size()));
                    total += partLines.size();
                    DateWindow window = new DateWindow(
                            LocalDate.parse(part.periodStart()), LocalDate.parse(part.periodEndExclusive()));
                    span = span == null ? window : span.span(window);
                }
                DateWindow period = span;
                long rows = total;
                return writer.publish(checksum -> new ExtractionManifest(
                        extractionId,
                        sourceId,
                        municipalityIbge,
                        period.start().toString(),
                        period.endExclusive().toString(),
                        STARTED_AT,
                        FINISHED_AT,
                        ExtractionManifest.CANONICAL_SCHEMA_VERSION_V2,
                        "COMPLETE",
                        "SNAPSHOT",
                        "America/Sao_Paulo",
                        rows,
                        0,
                        checksum,
                        ManifestChecksums.compositeQueryChecksum(counted),
                        adapterVersion(counted),
                        counted));
            }
        }

        private static ManifestPart withRowCount(ManifestPart part, long rowCount) {
            return new ManifestPart(
                    part.index(),
                    part.capability(),
                    part.adapterVersion(),
                    part.queryChecksum(),
                    part.recordKind(),
                    part.periodStart(),
                    part.periodEndExclusive(),
                    part.params(),
                    part.paramsChecksum(),
                    rowCount);
        }

        /** The plan's version: the parts' common version, or every version joined by {@code +}. */
        private static String adapterVersion(List<ManifestPart> parts) {
            Set<String> versions = new TreeSet<>();
            parts.forEach(part -> versions.add(part.adapterVersion()));
            return String.join("+", versions);
        }
    }
}
