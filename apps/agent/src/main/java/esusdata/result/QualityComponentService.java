package esusdata.result;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.pack.componente3.ComponentIIIInput;
import esusdata.indicator.pack.componente3.ComponentIIIResult;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import esusdata.source.SourceCoverageService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The Nota Final do Componente III, computed on read (ADR 0030, NT 8/2026): the newest published
 * result of each pack of C1–C7 and closed month of the quadrimestre, handed to {@link
 * ComponentIII#consolidation()} as one unit for the municipality and one per team (INE) found in the
 * results' per-team breakdown. Nothing is acquired, enqueued or stored; the response names the ids
 * it read and their fingerprint, so the same published results always yield the same note.
 *
 * <p>A competência counts only once closed by the service's clock — before the current month in
 * the municipality's zone ({@link SourceCoverageService#ZONE}). A result already published for the
 * month in progress is read as absent: the month can still change, and a note over it would too.
 *
 * <p>The service never computes the note itself: finishing the consolidation (today {@code
 * BLOCKED}) replaces {@code ComponentIII.consolidation()} without touching this class.
 */
public final class QualityComponentService {

    /** C1–C7 by pack id: the bands the consolidation classifies each mean with. */
    private static final Map<String, IndicatorRule> RULES = rulesById();

    private static final Set<String> COMPONENT_PACKS = Set.copyOf(ComponentIII.DESCRIPTOR.dependsOn());

    private final ResultRepository resultRepository;
    private final Clock clock;

    public QualityComponentService(ResultRepository resultRepository, Clock clock) {
        this.resultRepository = resultRepository;
        this.clock = clock;
    }

    /** The consolidated quadrimestre and the fingerprint of the published results it read. */
    public record Consolidated(ComponentIIIResult result, String inputFingerprint) {}

    public Consolidated consolidate(String municipalityIbge, Quadrimestre quadrimestre) {
        List<PublishedResult> read = read(municipalityIbge, quadrimestre);
        ComponentIIIInput input = new ComponentIIIInput(municipalityIbge, quadrimestre, units(read));
        return new Consolidated(ComponentIII.consolidation().consolidate(input, RULES), fingerprint(read));
    }

    /**
     * What the note reads: the newest published result of each pack of C1–C7 and month of the
     * quadrimestre, of the competências the service's clock has already closed.
     */
    List<PublishedResult> read(String municipalityIbge, Quadrimestre quadrimestre) {
        YearMonth current = YearMonth.now(clock.withZone(SourceCoverageService.ZONE));
        return resultRepository
                .findLatestPublishedInRange(
                        municipalityIbge,
                        null,
                        quadrimestre.firstMonth().toString(),
                        quadrimestre.lastMonth().toString())
                .stream()
                .filter(result -> COMPONENT_PACKS.contains(result.indicatorPack()))
                .filter(result -> YearMonth.parse(result.referencePeriod()).isBefore(current))
                .toList();
    }

    /**
     * The municipality first, then one unit per INE, in INE order. {@code ine == null} names the
     * municipality, built only from the municipal results; the per-team breakdown's bucket of
     * records without a team (also {@code ine == null}) never becomes a unit — it would be a second
     * municipality.
     */
    static List<ComponentIIIInput.Unit> units(List<PublishedResult> results) {
        List<ComponentIIIInput.Monthly> municipal = new ArrayList<>();
        Map<String, List<ComponentIIIInput.Monthly>> byTeam = new TreeMap<>();
        Map<String, String> cnesByTeam = new TreeMap<>();
        for (PublishedResult result : results) {
            YearMonth month = YearMonth.parse(result.referencePeriod());
            IndicatorStatus status = IndicatorStatus.valueOf(result.status());
            municipal.add(new ComponentIIIInput.Monthly(
                    result.indicatorPack(),
                    month,
                    result.resultId(),
                    status,
                    status == IndicatorStatus.COMPUTED ? result.valueExact() : null,
                    result.consolidationEligible(),
                    result.ruleVersion()));
            for (ResultJson.StoredTeam team : ResultJson.readTeams(result.teamResultsJson())) {
                if (team.ine() == null) {
                    continue; // the no-team bucket: its records are already in the municipal unit
                }
                IndicatorStatus teamStatus = IndicatorStatus.valueOf(team.status());
                ExactRatio value = teamStatus == IndicatorStatus.COMPUTED ? team.value() : null;
                byTeam.computeIfAbsent(team.ine(), ine -> new ArrayList<>())
                        .add(new ComponentIIIInput.Monthly(
                                result.indicatorPack(),
                                month,
                                result.resultId(),
                                teamStatus,
                                value,
                                team.consolidationEligible(),
                                result.ruleVersion()));
                if (team.cnes() != null) {
                    cnesByTeam.putIfAbsent(team.ine(), team.cnes());
                }
            }
        }
        List<ComponentIIIInput.Unit> units = new ArrayList<>();
        units.add(new ComponentIIIInput.Unit(null, null, municipal));
        byTeam.forEach((ine, monthly) -> units.add(new ComponentIIIInput.Unit(ine, cnesByTeam.get(ine), monthly)));
        return List.copyOf(units);
    }

    /** {@code sha256:} + SHA-256 of the ids read, sorted and joined by {@code \n}. */
    static String fingerprint(List<PublishedResult> results) {
        List<String> ids = new ArrayList<>(results.size());
        for (PublishedResult result : results) {
            ids.add(result.resultId());
        }
        Collections.sort(ids);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(digest.digest(String.join("\n", ids).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Map<String, IndicatorRule> rulesById() {
        Map<String, IndicatorRule> rules = new LinkedHashMap<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            rules.put(rule.descriptor().id(), rule);
        }
        return Collections.unmodifiableMap(rules);
    }
}
