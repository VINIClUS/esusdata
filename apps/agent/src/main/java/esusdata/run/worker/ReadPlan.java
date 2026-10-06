package esusdata.run.worker;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PartRequirement;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.acquisition.AcquisitionPart;
import esusdata.run.extract.ExtractStore;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.ManifestChecksums;
import esusdata.run.extract.ManifestPart;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.CapabilityContract;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

/**
 * What one run of a rule reads (ADR 0030), resolved before any I/O: a canonical v1 read (C1, one
 * compiled-in capability over the competência, exactly as before), which may carry a
 * <em>supplement</em> — canonical v2 parts of the rule's {@code supplements} (C1's {@code team}, ADR
 * 0033) read as an extract of their own and checked like a v2 extract — or a canonical v2 read — one
 * {@link AcquisitionPart} per {@link PartRequirement}, with the version, query checksum and record
 * kind of the packaged {@link CapabilityContract}, and the period of the command as the span of
 * every part. A requirement whose binds are not the ones its contract declares is a pack error and
 * fails here, never inside the execution plane.
 *
 * <p>The same plan checks what came back: an extract is accepted only when its manifest lists
 * exactly these parts — capability, version, query checksum, record kind, window, binds and their
 * checksum, the binds and checksums computed solely by {@link ManifestChecksums}, as the side that
 * published it did.
 */
final class ReadPlan {

    private static final String DATE_BIND = "DATE";
    private static final String EXTRACT = "extract ";
    private static final String TEXT_ARRAY_BIND = "TEXT_ARRAY";

    private final IndicatorRule rule;
    private final DataRequirements requirements;
    private final List<AcquisitionPart> parts;
    private final DateWindow span;
    private final List<AcquisitionPart> supplement;
    private final DateWindow supplementSpan;

    private ReadPlan(
            IndicatorRule rule,
            DataRequirements requirements,
            List<AcquisitionPart> parts,
            DateWindow span,
            List<AcquisitionPart> supplement,
            DateWindow supplementSpan) {
        this.rule = rule;
        this.requirements = requirements;
        this.parts = List.copyOf(parts);
        this.span = span;
        this.supplement = List.copyOf(supplement);
        this.supplementSpan = supplementSpan;
    }

    /**
     * Resolves the rule's requirements for {@code competencia}.
     *
     * @throws IllegalArgumentException for a capability this release does not package or binds the
     *     contract does not declare (→ {@code INVALID_REQUEST})
     */
    static ReadPlan of(IndicatorRule rule, YearMonth competencia, CapabilityCatalog catalog) {
        DataRequirements requirements = rule.requirements(competencia);
        DateWindow span = null;
        for (PartRequirement requirement : requirements.parts()) {
            DateWindow window = new DateWindow(requirement.periodStart(), requirement.periodEndExclusive());
            span = span == null ? window : span.span(window);
        }
        List<PartRequirement> supplements = rule.supplements(competencia);
        List<AcquisitionPart> supplement = new ArrayList<>();
        DateWindow supplementSpan = null;
        for (PartRequirement requirement : supplements) {
            DateWindow window = new DateWindow(requirement.periodStart(), requirement.periodEndExclusive());
            supplementSpan = supplementSpan == null ? window : supplementSpan.span(window);
            supplement.add(acquisitionPart(rule, requirement, catalog));
        }
        if (requirements.canonicalSchemaVersion() == DataRequirements.V1) {
            return new ReadPlan(rule, requirements, List.of(), span, supplement, supplementSpan);
        }
        if (!supplement.isEmpty()) {
            throw new IllegalArgumentException(rule.descriptor().id()
                    + " reads canonical v2: it lists every part in requirements, not as supplements");
        }
        List<AcquisitionPart> parts = new ArrayList<>();
        for (PartRequirement requirement : requirements.parts()) {
            parts.add(acquisitionPart(rule, requirement, catalog));
        }
        return new ReadPlan(rule, requirements, parts, span, List.of(), null);
    }

    private static AcquisitionPart acquisitionPart(
            IndicatorRule rule, PartRequirement requirement, CapabilityCatalog catalog) {
        CapabilityContract contract = catalog.find(requirement.capability())
                .orElseThrow(
                        () -> new IllegalArgumentException(rule.descriptor().id() + " reads capability "
                                + requirement.capability() + ", which this release does not package"));
        requireDeclaredBinds(rule, requirement, contract);
        return new AcquisitionPart(
                contract.capability(),
                contract.adapterVersion(),
                contract.queryChecksum(),
                contract.recordKind(),
                requirement.periodStart(),
                requirement.periodEndExclusive(),
                requirement.arrayParams(),
                requirement.dateParams());
    }

    boolean isCanonicalV2() {
        return requirements.canonicalSchemaVersion() == DataRequirements.V2;
    }

    /** Every capability the run reads: the descriptor's and the requirements', without repetition. */
    Set<String> capabilities() {
        Set<String> capabilities = new LinkedHashSet<>(rule.descriptor().requiredCapabilities());
        requirements.parts().forEach(part -> capabilities.add(part.capability()));
        return capabilities;
    }

    List<AcquisitionPart> parts() {
        return parts;
    }

    /** True when the run also reads a canonical v2 extract of its own beside the v1 one (C1's {@code team}). */
    boolean hasSupplement() {
        return !supplement.isEmpty();
    }

    /** The capabilities of the supplementary extract, which the source must have {@code VALIDATED}. */
    Set<String> supplementCapabilities() {
        Set<String> capabilities = new LinkedHashSet<>();
        supplement.forEach(part -> capabilities.add(part.capability()));
        return capabilities;
    }

    /** The id of the supplementary extract that goes with {@code extractionId}: read and replayed as a pair. */
    static String supplementExtractionId(String extractionId) {
        return extractionId + "-team";
    }

    /**
     * The supplementary acquisition: a canonical v2 command over the supplement's parts with the
     * pack's budget ceilings, read in its own read-only transaction before the v1 one.
     */
    AcquisitionCommand supplementCommand(
            PecConnectionProperties properties, PecSourceIdentity identity, String extractionId, String sourceZoneId) {
        return new AcquisitionCommand(
                properties,
                identity,
                budget(),
                supplementExtractionId(extractionId),
                supplementSpan.start(),
                supplementSpan.endExclusive(),
                sourceZoneId,
                supplement);
    }

    DateWindow span() {
        return span;
    }

    /**
     * The acquisition of this plan. C1 keeps its v1 command and the engineering read budget; a v2
     * command carries its parts and the pack's budget ceilings, with the engineering timeouts — the
     * ones the ENG-51 cooldown margin is derived from.
     */
    AcquisitionCommand command(
            PecConnectionProperties properties, PecSourceIdentity identity, String extractionId, String sourceZoneId) {
        if (!isCanonicalV2()) {
            return new AcquisitionCommand(
                    properties,
                    identity,
                    ReadBudget.initialEngineeringProposal(),
                    extractionId,
                    span.start(),
                    span.endExclusive(),
                    sourceZoneId);
        }
        return new AcquisitionCommand(
                properties, identity, budget(), extractionId, span.start(), span.endExclusive(), sourceZoneId, parts);
    }

    private ReadBudget budget() {
        ReadBudget engineering = ReadBudget.initialEngineeringProposal();
        var hint = rule.descriptor().budget();
        return new ReadBudget(
                engineering.poolMaxSize(),
                engineering.connectionTimeout(),
                engineering.acquisitionTimeout(),
                engineering.statementTimeoutMs(),
                engineering.lockTimeoutMs(),
                engineering.idleInTransactionTimeoutMs(),
                hint.maxRows(),
                hint.maxDurationMs(),
                hint.maxPayloadBytes(),
                hint.maxTempFileBytes());
    }

    /**
     * Refuses an extract this plan did not ask for: another source, municipality or period, the
     * other canonical schema, or — in v2 — any part that differs from the plan.
     */
    void requireCovers(ExtractionManifest manifest, RunExecutor.RunContext context) {
        if (manifest.isCanonicalV2() != isCanonicalV2()) {
            throw new IllegalStateException(EXTRACT + manifest.extractionId() + " is canonical v"
                    + manifest.canonicalSchemaVersion() + " but "
                    + rule.descriptor().ruleVersion()
                    + " reads a canonical v" + requirements.canonicalSchemaVersion() + " extract (ADR 0030)");
        }
        if (!manifest.sourceId().equals(context.sourceId())
                || !manifest.municipalityIbge().equals(context.municipalityIbge())
                || !manifest.periodStart().equals(span.start().toString())
                || !manifest.periodEndExclusive().equals(span.endExclusive().toString())) {
            // The extract file matches the requested extractionId but its actual scope (source,
            // municipality, or period) does not match what the job asked for — all FKs stay
            // individually valid, so nothing else would catch this. This matters even for an
            // extract with zero matching records: a rule's per-record checks never run on an
            // empty extract, so a scope mismatch would otherwise publish a plausible-looking
            // zero-count result under the wrong municipality/period with wrong provenance,
            // silently — exactly what §1.10.1 forbids.
            throw new IllegalStateException(EXTRACT + manifest.extractionId() + " covers source " + manifest.sourceId()
                    + "/municipality " + manifest.municipalityIbge() + "/period ["
                    + manifest.periodStart() + ", " + manifest.periodEndExclusive()
                    + ") but job " + context.jobId() + " requested source " + context.sourceId()
                    + "/municipality " + context.municipalityIbge() + "/period "
                    + context.referencePeriod());
        }
        if (isCanonicalV2()) {
            requireSameParts(manifest, parts);
        }
    }

    /** Refuses a supplementary extract that is not the one this plan asked for, as {@link #requireCovers} does. */
    void requireSupplementCovers(ExtractionManifest primary, ExtractionManifest manifest) {
        if (!manifest.isCanonicalV2()
                || !manifest.extractionId().equals(supplementExtractionId(primary.extractionId()))
                || !manifest.sourceId().equals(primary.sourceId())
                || !manifest.municipalityIbge().equals(primary.municipalityIbge())
                || !manifest.periodStart().equals(supplementSpan.start().toString())
                || !manifest.periodEndExclusive()
                        .equals(supplementSpan.endExclusive().toString())) {
            throw new IllegalStateException(EXTRACT + manifest.extractionId() + " is not the supplementary extract of "
                    + primary.extractionId() + " that " + rule.descriptor().ruleVersion()
                    + " reads: schema, source, municipality or period differ (ADR 0033)");
        }
        requireSameParts(manifest, supplement);
    }

    /** Reads the extract this plan covers: C1's encounters, or every part of a v2 extract. */
    CanonicalDataset read(ExtractStore extractStore, ExtractionManifest manifest) throws IOException {
        if (isCanonicalV2()) {
            return extractStore.readDataset(manifest, null);
        }
        PartRequirement part = requirements.parts().getFirst();
        return CanonicalDataset.ofEncounters(part.capability(), span, extractStore.readEncounters(manifest));
    }

    /**
     * The v1 extract together with its supplementary extract, which must be read as the plan asks
     * (ADR 0033): without it the rule would run without the capability it requires.
     */
    CanonicalDataset read(ExtractStore extractStore, ExtractionManifest manifest, ExtractionManifest supplementManifest)
            throws IOException {
        requireSupplementCovers(manifest, supplementManifest);
        return read(extractStore, manifest).with(extractStore.readDataset(supplementManifest, null));
    }

    private void requireSameParts(ExtractionManifest manifest, List<AcquisitionPart> parts) {
        Map<String, ManifestPart> listed = new HashMap<>();
        manifest.parts().forEach(part -> listed.put(part.capability(), part));
        Set<String> expected = new TreeSet<>();
        parts.forEach(part -> expected.add(part.capability()));
        if (!listed.keySet().equals(expected)) {
            throw new IllegalStateException(EXTRACT + manifest.extractionId() + " lists parts "
                    + new TreeSet<>(listed.keySet()) + " but "
                    + rule.descriptor().ruleVersion() + " reads "
                    + expected);
        }
        for (AcquisitionPart part : parts) {
            ManifestPart actual = listed.get(part.capability());
            SortedMap<String, List<String>> params = ManifestChecksums.params(part.arrayParams(), part.dateParams());
            if (!actual.adapterVersion().equals(part.adapterVersion())
                    || !actual.queryChecksum().equals(part.queryChecksum())
                    || !actual.recordKind().equals(part.recordKind())
                    || !actual.periodStart().equals(text(part.periodStart()))
                    || !actual.periodEndExclusive().equals(text(part.periodEndExclusive()))
                    || !actual.params().equals(params)
                    || !actual.paramsChecksum().equals(ManifestChecksums.paramsChecksum(params))) {
                throw new IllegalStateException(EXTRACT + manifest.extractionId() + " part " + part.capability()
                        + " was not read as " + rule.descriptor().ruleVersion()
                        + " requires: version, query checksum, record kind, window or binds differ");
            }
        }
    }

    private static void requireDeclaredBinds(
            IndicatorRule rule, PartRequirement requirement, CapabilityContract contract) {
        Set<String> dates = new TreeSet<>();
        Set<String> arrays = new TreeSet<>();
        for (CapabilityContract.Bind bind : contract.binds()) {
            if (DATE_BIND.equals(bind.type())) {
                dates.add(bind.name());
            } else if (TEXT_ARRAY_BIND.equals(bind.type())) {
                arrays.add(bind.name());
            }
        }
        if (!requirement.dateParams().keySet().equals(dates)
                || !requirement.arrayParams().keySet().equals(arrays)) {
            throw new IllegalArgumentException(rule.descriptor().id() + " binds dates "
                    + requirement.dateParams().keySet() + " and code lists "
                    + requirement.arrayParams().keySet()
                    + " to " + contract.capability() + ", which declares dates " + dates + " and code lists " + arrays);
        }
    }

    private static String text(LocalDate date) {
        return date.toString();
    }
}
