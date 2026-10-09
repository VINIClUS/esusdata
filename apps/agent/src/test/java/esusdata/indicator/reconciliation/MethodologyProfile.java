package esusdata.indicator.reconciliation;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The methodology profile of one compiled rule (spec §8.3), read from {@code
 * contracts/indicators/siaps-methodology-profiles.json} by {@link MethodologyProfileRegistry}. It
 * says, for {@code pack + rule_version}:
 *
 * <ul>
 *   <li>the METHODOLOGY {@link Dimension}s of the rule, each with the local reading, the decision
 *       and limitation records behind it and the probe that proves, on the data of a reference,
 *       whether the other reading changes the result; a dimension without a probe must read
 *       {@code SAME} in every quadrimestre, as there is nothing left for a probe to prove;
 *   <li>the {@link DeclaredLimitation}s (ADR 0034 §6): what the local rule cannot reach. They have
 *       no probe and no official reading, and they are recorded in the dossier;
 *   <li>the {@link DeclaredConvention}s (ADR 0034 §7): what the ficha leaves open and the local
 *       rule settles by a stated reading. A convention may have a probe, recorded in the dossier
 *       and never read by the verdict, and it is never a reading of a quadrimestre;
 *   <li>the {@link DataTiming} items (transmission cutoff, late sending, reprocessing), which are
 *       not probed and never enter the compatibility verdict: the reconciliation judges them by
 *       tolerance (spec §14, §22);
 *   <li>per quadrimestre, the {@link OfficialEdition} and what it reads for every dimension.
 * </ul>
 *
 * <p>Compatibility is decided by these readings and by the probes, never by a date and never by
 * comparing the local output with the official one (spec §6.3, §9.5). The only date in a profile
 * is {@link Source#published()}, metadata for people. A profile is valid or it does not exist: the
 * constructors refuse a profile without dimensions, ids or probes that repeat, a convention that
 * takes the id or the probe of a dimension, and a quadrimestre that leaves a dimension
 * unclassified or reads one that is not there (a convention included).
 *
 * @param pack the pack id, e.g. {@code c2-desenvolvimento-infantil}
 * @param ruleVersion the compiled rule version, {@code <pack>@<version>}
 * @param dimensions the METHODOLOGY dimensions, in declaration order; never empty
 * @param dataTiming the DATA_TIMING items, possibly none
 * @param declaredLimitations the declared limitations (ADR 0034 §6), possibly none
 * @param declaredConventions the declared conventions (ADR 0034 §7), possibly none
 * @param officialReadings the readings by quadrimestre in the SIAPS spelling ({@code 2026Q1}); an
 *     identification, nothing orders or selects by it
 */
public record MethodologyProfile(
        String pack,
        String ruleVersion,
        List<Dimension> dimensions,
        List<DataTiming> dataTiming,
        List<DeclaredLimitation> declaredLimitations,
        List<DeclaredConvention> declaredConventions,
        Map<String, OfficialEdition> officialReadings) {

    /** The shape of the stable ids of dimensions, probes and timing items, e.g. {@code c2.cohort.second-birthday}. */
    static final Pattern STABLE_ID = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z0-9]+(-[a-z0-9]+)*)+");

    private static final Pattern PACK = Pattern.compile("[a-z0-9-]+");
    private static final Pattern RULE_VERSION = Pattern.compile("[a-z0-9-]+@[0-9]+\\.[0-9]+\\.[0-9]+");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern DECISION_REF = Pattern.compile("[A-Z][A-Z0-9]*(-[A-Z0-9]+)+");
    private static final Pattern URL = Pattern.compile("https://\\S+");
    private static final String STABLE_ID_EXAMPLE = "c2.cohort.second-birthday";

    /**
     * A METHODOLOGY dimension: one way the local rule reads the ficha that an edition may read
     * otherwise.
     *
     * @param id the stable id, unique in the profile and among its timing items
     * @param localReading what the local rule does, in words
     * @param decisionRefs the decision and limitation records behind it ({@code AMB-C2-03},
     *     {@code LACUNA-L3}); possibly none
     * @param probeId the probe that proves it; unique in the profile, shared by no other dimension
     *     and no convention. Empty only for a dimension that every quadrimestre reads {@code SAME}
     *     (the profile checks it)
     */
    public record Dimension(String id, String localReading, List<String> decisionRefs, Optional<String> probeId) {

        public Dimension {
            matching(STABLE_ID, id, "dimension id", STABLE_ID_EXAMPLE);
            text(localReading, "local_reading of " + id);
            decisionRefs = distinctDecisionRefs(decisionRefs, id);
            probeId = optionalStableId(probeId, "probe_id of " + id);
        }

        /** A dimension proved by the probe {@code probeId}. */
        public Dimension(String id, String localReading, List<String> decisionRefs, String probeId) {
            this(id, localReading, decisionRefs, Optional.of(probeId));
        }
    }

    /**
     * A declared limitation (ADR 0034 §6): something the local rule cannot reach. It has no probe
     * and no official reading; the dossier records it.
     *
     * @param id the stable id, unique among the limitations of the profile
     * @param what what the local rule cannot reach, in words
     * @param decisionRefs the decision and limitation records behind it; never empty
     */
    public record DeclaredLimitation(String id, String what, List<String> decisionRefs) {

        public DeclaredLimitation {
            matching(STABLE_ID, id, "declared limitation id", STABLE_ID_EXAMPLE);
            text(what, "what of " + id);
            decisionRefs = requiredDecisionRefs(decisionRefs, id);
        }
    }

    /**
     * A declared convention (ADR 0034 §7): something the ficha leaves open and the local rule
     * settles by a stated reading. It never appears in the readings of a quadrimestre. Its probe,
     * when it has one, is recorded in the dossier and the verdict never reads it.
     *
     * @param id the stable id, disjoint from the dimension and data-timing ids of the profile
     * @param localReading what the local rule does, in words
     * @param decisionRefs the decision records behind it; never empty
     * @param researchRef where the omission of the ficha is tagged (K3), e.g. {@code
     *     edicoes-oficiais-siaps.md §6.6 n1 (K3)}
     * @param probeId the probe recorded for it, if any; unique, and not the probe of a dimension
     */
    public record DeclaredConvention(
            String id, String localReading, List<String> decisionRefs, String researchRef, Optional<String> probeId) {

        public DeclaredConvention {
            matching(STABLE_ID, id, "declared convention id", STABLE_ID_EXAMPLE);
            text(localReading, "local_reading of " + id);
            decisionRefs = requiredDecisionRefs(decisionRefs, id);
            text(researchRef, "research_ref of " + id);
            probeId = optionalStableId(probeId, "probe_id of " + id);
        }
    }

    /**
     * A DATA_TIMING item: a difference in when the data reaches the result, not in what it means.
     * It has no probe and no official reading, and it never enters the compatibility verdict.
     *
     * @param id the stable id, unique in the profile and among its dimensions
     * @param kind which timing difference it is
     * @param localReading what the local result does, in words
     * @param decisionRefs the decision and limitation records behind it, possibly none
     */
    public record DataTiming(String id, TimingKind kind, String localReading, List<String> decisionRefs) {

        /** The timing differences between the PEC and the SIAPS that the reconciliation judges by tolerance. */
        public enum TimingKind {
            /** The SIAPS cuts the submissions at the 20th business day; the PEC result does not. */
            TRANSMISSION_CUTOFF,
            /** A municipality sends after the cutoff, and the SIAPS may or may not take it in. */
            LATE_SENDING,
            /** The SIAPS reprocesses a period after it was published. */
            REPROCESSING
        }

        public DataTiming {
            matching(STABLE_ID, id, "data-timing id", STABLE_ID_EXAMPLE);
            Objects.requireNonNull(kind, "kind of " + id);
            text(localReading, "local_reading of " + id);
            decisionRefs = distinctDecisionRefs(decisionRefs, id);
        }
    }

    /**
     * An official document the readings rest on.
     *
     * @param id its id in {@code sources[]}
     * @param title the title of the document
     * @param url where it is published, {@code https}
     * @param published when it was published: metadata for people, it selects nothing
     * @param kind what sort of document it is
     */
    public record Source(String id, String title, String url, LocalDate published, DocumentKind kind) {

        /** What sort of document a source is. */
        public enum DocumentKind {
            /** The ficha de qualificação of an indicator. */
            FICHA,
            /** A nota técnica. */
            TECHNICAL_NOTE,
            /** A portaria. */
            ORDINANCE,
            /** The release notes or the version history of the SIAPS. */
            SIAPS_RELEASE,
            /** A manual of a SIAPS module. */
            SIAPS_MANUAL,
            /** An official calendar. */
            CALENDAR,
            /** An official question-and-answer page, the SIAPS's or the Ministry's. */
            FAQ
        }

        public Source {
            matching(SLUG, id, "source id", "ficha-c2-2026");
            text(title, "title of source " + id);
            matching(URL, url, "url of source " + id, "https://...");
            Objects.requireNonNull(published, "published of source " + id);
            Objects.requireNonNull(kind, "kind of source " + id);
        }
    }

    /**
     * What an official edition reads for one dimension.
     *
     * @param reading whether it is the local reading, another one or not known
     * @param officialReadingText the official reading, quoted: set exactly when {@code reading} is
     *     {@link OfficialReading#DIFFERENT}, {@code null} otherwise
     * @param source the source of the reading
     */
    public record Reading(OfficialReading reading, String officialReadingText, Source source) {

        public Reading {
            Objects.requireNonNull(reading, "reading");
            Objects.requireNonNull(source, "source");
            boolean differs = reading == OfficialReading.DIFFERENT;
            if (differs && (officialReadingText == null || officialReadingText.isBlank())) {
                throw new IllegalArgumentException(
                        "a DIFFERENT reading needs official_reading_text, the official reading quoted");
            }
            if (!differs && officialReadingText != null) {
                throw new IllegalArgumentException(
                        "only a DIFFERENT reading carries official_reading_text, not a " + reading + " one");
            }
        }
    }

    /**
     * The edition of the official methodology a quadrimestre was processed with, and its reading of
     * every dimension of the profile.
     *
     * @param id the id of the edition, e.g. {@code fichas-2026-v2}
     * @param sources the official sources of the edition; never empty
     * @param readings the reading of every dimension of the profile, by dimension id; each cites one
     *     of {@code sources}
     */
    public record OfficialEdition(String id, List<Source> sources, Map<String, Reading> readings) {

        public OfficialEdition {
            matching(SLUG, id, "edition id", "fichas-2026-v2");
            sources = List.copyOf(sources);
            readings = ordered(readings);
            if (sources.isEmpty() || new HashSet<>(sources).size() != sources.size()) {
                throw new IllegalArgumentException("edition " + id + " needs source_refs that name each source once");
            }
            for (Map.Entry<String, Reading> entry : readings.entrySet()) {
                Source cited = entry.getValue().source();
                if (!sources.contains(cited)) {
                    throw new IllegalArgumentException("the reading of " + entry.getKey() + " cites source "
                            + cited.id() + ", which is not among the sources of edition " + id);
                }
            }
        }
    }

    public MethodologyProfile {
        matching(PACK, pack, "pack", "c2-desenvolvimento-infantil");
        matching(RULE_VERSION, ruleVersion, "rule_version", "c2-desenvolvimento-infantil@0.3.0");
        if (!ruleVersion.startsWith(pack + "@")) {
            throw new IllegalArgumentException("rule_version " + ruleVersion + " must be " + pack + "@<version>");
        }
        dimensions = List.copyOf(dimensions);
        dataTiming = List.copyOf(dataTiming);
        declaredLimitations = List.copyOf(declaredLimitations);
        declaredConventions = List.copyOf(declaredConventions);
        officialReadings = ordered(officialReadings);
        if (dimensions.isEmpty()) {
            throw new IllegalArgumentException(
                    ruleVersion + " has no methodology dimension: a profile without one would prove nothing");
        }
        if (officialReadings.isEmpty()) {
            throw new IllegalArgumentException(ruleVersion + " declares no official reading of any quadrimestre");
        }
        requireDistinctIds(dimensions, dataTiming);
        requireDistinctProbes(dimensions);
        requireDistinctLimitations(declaredLimitations);
        requireDisjointConventions(declaredConventions, dimensions, dataTiming);
        for (Map.Entry<String, OfficialEdition> entry : officialReadings.entrySet()) {
            requireClassified(entry.getKey(), entry.getValue(), dimensions, dataTiming, declaredConventions);
        }
        requireSameWithoutProbe(dimensions, officialReadings);
    }

    /**
     * The probes the verdict reads: those of the dimensions that have one, in the order of the
     * dimensions. Data-timing items, limitations and conventions add none.
     */
    public List<String> requiredProbeIds() {
        return dimensions.stream()
                .map(Dimension::probeId)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The probes of the conventions that have one, in declaration order. They are recorded in the
     * dossier and never read by the verdict (ADR 0034 §7).
     */
    public List<String> conventionProbeIds() {
        return declaredConventions.stream()
                .map(DeclaredConvention::probeId)
                .flatMap(Optional::stream)
                .toList();
    }

    /** The declared limitation with this id (ADR 0034 §6), if the profile declares it. */
    public Optional<DeclaredLimitation> limitation(String id) {
        return declaredLimitations.stream()
                .filter(limitation -> limitation.id().equals(id))
                .findFirst();
    }

    /** Whether the profile declares the limitation with this id. */
    public boolean declaresLimitation(String id) {
        return limitation(id).isPresent();
    }

    /**
     * The edition and readings declared for a quadrimestre in the SIAPS spelling, by exact match;
     * empty when the profile does not declare it, which leaves a reference of that quadrimestre
     * without official readings (and so inconclusive).
     */
    public Optional<OfficialEdition> officialEdition(String quadrimestre) {
        return Optional.ofNullable(officialReadings.get(quadrimestre));
    }

    private static void requireDistinctIds(List<Dimension> dimensions, List<DataTiming> timing) {
        Set<String> dimensionIds = new HashSet<>();
        for (Dimension dimension : dimensions) {
            if (!dimensionIds.add(dimension.id())) {
                throw new IllegalArgumentException("duplicate dimension id " + dimension.id());
            }
        }
        Set<String> timingIds = new HashSet<>();
        for (DataTiming item : timing) {
            if (dimensionIds.contains(item.id())) {
                throw new IllegalArgumentException(
                        "id " + item.id() + " is both a dimension and a data-timing item: ids are unique in a profile");
            }
            if (!timingIds.add(item.id())) {
                throw new IllegalArgumentException("duplicate data-timing id " + item.id());
            }
        }
    }

    private static void requireDistinctProbes(List<Dimension> dimensions) {
        Set<String> probeIds = new HashSet<>();
        for (Dimension dimension : dimensions) {
            Optional<String> probe = dimension.probeId();
            if (probe.isPresent() && !probeIds.add(probe.get())) {
                throw new IllegalArgumentException("duplicate probe_id " + probe.get()
                        + " across the dimensions: each dimension has a probe of its own");
            }
        }
    }

    private static void requireDistinctLimitations(List<DeclaredLimitation> limitations) {
        Set<String> ids = new HashSet<>();
        for (DeclaredLimitation limitation : limitations) {
            if (!ids.add(limitation.id())) {
                throw new IllegalArgumentException("duplicate declared limitation id " + limitation.id());
            }
        }
    }

    /** A convention takes no id and no probe of a dimension or a timing item, and none of another convention. */
    private static void requireDisjointConventions(
            List<DeclaredConvention> conventions, List<Dimension> dimensions, List<DataTiming> timing) {
        Set<String> taken = new HashSet<>();
        dimensions.forEach(dimension -> taken.add(dimension.id()));
        timing.forEach(item -> taken.add(item.id()));
        Set<String> dimensionProbes = new HashSet<>();
        dimensions.forEach(dimension -> dimension.probeId().ifPresent(dimensionProbes::add));
        Set<String> conventionIds = new HashSet<>();
        Set<String> conventionProbes = new HashSet<>();
        for (DeclaredConvention convention : conventions) {
            if (taken.contains(convention.id())) {
                throw new IllegalArgumentException("id " + convention.id()
                        + " is both a convention and a dimension or data-timing item: ids are unique in a profile");
            }
            if (!conventionIds.add(convention.id())) {
                throw new IllegalArgumentException("duplicate declared convention id " + convention.id());
            }
            Optional<String> probe = convention.probeId();
            if (probe.isPresent() && dimensionProbes.contains(probe.get())) {
                throw new IllegalArgumentException("probe_id " + probe.get() + " of convention " + convention.id()
                        + " is also the probe of a dimension: the verdict reads the probes of the dimensions only");
            }
            if (probe.isPresent() && !conventionProbes.add(probe.get())) {
                throw new IllegalArgumentException("duplicate probe_id " + probe.get() + " across the conventions");
            }
        }
    }

    /** A dimension without a probe has nothing proving it, so no quadrimestre may read it otherwise than SAME. */
    private static void requireSameWithoutProbe(
            List<Dimension> dimensions, Map<String, OfficialEdition> officialReadings) {
        for (Dimension dimension : dimensions) {
            if (dimension.probeId().isPresent()) {
                continue;
            }
            for (Map.Entry<String, OfficialEdition> entry : officialReadings.entrySet()) {
                Reading read = entry.getValue().readings().get(dimension.id());
                if (read.reading() != OfficialReading.SAME) {
                    throw new IllegalArgumentException("dimension " + dimension.id() + " has no probe_id, so "
                            + entry.getKey() + " must read it SAME, not " + read.reading());
                }
            }
        }
    }

    /** One quadrimestre reads exactly the dimensions of the profile: none left unclassified, none invented. */
    private static void requireClassified(
            String quadrimestre,
            OfficialEdition edition,
            List<Dimension> dimensions,
            List<DataTiming> timing,
            List<DeclaredConvention> conventions) {
        ReferenceFormats.quadrimestre(quadrimestre);
        Set<String> dimensionIds = new HashSet<>();
        dimensions.forEach(dimension -> dimensionIds.add(dimension.id()));
        Set<String> timingIds = new HashSet<>();
        timing.forEach(item -> timingIds.add(item.id()));
        Set<String> conventionIds = new HashSet<>();
        conventions.forEach(convention -> conventionIds.add(convention.id()));
        for (String read : edition.readings().keySet()) {
            if (conventionIds.contains(read)) {
                throw new IllegalArgumentException(quadrimestre + " reads " + read + ", a declared convention: a"
                        + " convention is never a reading of a quadrimestre (ADR 0034 section 7)");
            }
            if (timingIds.contains(read)) {
                throw new IllegalArgumentException(quadrimestre + " reads " + read + ", a data-timing item: timing is"
                        + " judged by the reconciliation and has no official reading");
            }
            if (!dimensionIds.contains(read)) {
                throw new IllegalArgumentException(quadrimestre + " reads unknown dimension " + read);
            }
        }
        for (Dimension dimension : dimensions) {
            if (!edition.readings().containsKey(dimension.id())) {
                throw new IllegalArgumentException(quadrimestre + " does not classify dimension " + dimension.id()
                        + ": every dimension needs a reading in every declared quadrimestre");
            }
        }
    }

    private static <V> Map<String, V> ordered(Map<String, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private static List<String> distinctDecisionRefs(List<String> refs, String owner) {
        List<String> copy = List.copyOf(refs);
        Set<String> seen = new HashSet<>();
        for (String ref : copy) {
            matching(DECISION_REF, ref, "decision_ref of " + owner, "AMB-C2-03");
            if (!seen.add(ref)) {
                throw new IllegalArgumentException("decision_ref " + ref + " of " + owner + " is listed twice");
            }
        }
        return copy;
    }

    private static List<String> requiredDecisionRefs(List<String> refs, String owner) {
        List<String> distinct = distinctDecisionRefs(refs, owner);
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException(owner + " needs at least one decision_ref");
        }
        return distinct;
    }

    private static Optional<String> optionalStableId(Optional<String> value, String what) {
        Objects.requireNonNull(value, what);
        value.ifPresent(id -> matching(STABLE_ID, id, what, STABLE_ID_EXAMPLE));
        return value;
    }

    private static void matching(Pattern shape, String value, String what, String example) {
        if (value == null || !shape.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must look like " + example + ", not " + value);
        }
    }

    private static void text(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }
}
