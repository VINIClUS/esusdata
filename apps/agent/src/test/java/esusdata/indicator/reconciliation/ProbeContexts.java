package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The checks that make a {@link ProbeContext} hold together (spec §12: the quadrimestre, the
 * municipality and the hashes must agree across every artifact before anything is compared). Each
 * refusal is an {@link IllegalArgumentException} that says which artifact disagrees.
 */
final class ProbeContexts {

    private static final Pattern FINGERPRINT = Pattern.compile("sha256:[0-9a-f]{64}");

    private ProbeContexts() {}

    /** C1 to C7: the months in order, one rule, a baseline per month, and a reference of this pack. */
    static void requirePack(
            Quadrimestre quadrimestre,
            List<PackInput> inputs,
            List<RuleOutcome> baseline,
            ValidatedReference reference,
            SiapsReferenceManifest manifest,
            String fingerprint) {
        List<YearMonth> months = quadrimestre.months();
        require(
                inputs.size() == months.size(),
                "a quadrimestre has " + months.size() + " months, not " + inputs.size());
        require(baseline.size() == inputs.size(), "each input needs its baseline outcome");
        for (int i = 0; i < months.size(); i++) {
            YearMonth month = inputs.get(i).context().competencia();
            require(months.get(i).equals(month), "input " + (i + 1) + " is " + month + ", not " + months.get(i));
        }
        String rule = inputs.getFirst().rule().descriptor().ruleVersion();
        require(
                inputs.stream()
                        .allMatch(input -> rule.equals(input.rule().descriptor().ruleVersion())),
                "the four months are not all of " + rule);
        String municipality = manifest.municipalityIbge();
        require(
                inputs.stream()
                        .allMatch(input -> municipality.equals(input.context().municipalityIbge())),
                "an input is of another municipality than the manifest's " + municipality);
        require(
                reference
                        .pack()
                        .packId()
                        .equals(inputs.getFirst().rule().descriptor().id()),
                "the reference was validated for " + reference.pack().code() + ", not for " + rule);
        requireReference(quadrimestre, reference, manifest, fingerprint);
    }

    /** The Nota Final: all seven packs, every month of the quadrimestre, and a reference of the Nota Final. */
    static void requireNotaFinal(
            Quadrimestre quadrimestre,
            Map<String, Map<YearMonth, List<TeamResult>>> monthlyResults,
            ValidatedReference reference,
            SiapsReferenceManifest manifest,
            String fingerprint) {
        require(
                reference.pack().isNotaFinal(),
                "the reference was validated for " + reference.pack().code() + ", not for the Nota Final");
        Set<String> known =
                Set.copyOf(GatePack.all().stream().map(GatePack::packId).toList());
        require(
                known.containsAll(monthlyResults.keySet()),
                "results of a pack that is not C1 to C7: " + monthlyResults.keySet());
        List<String> missing = LocalClasses.missingNotaFinalInputs(quadrimestre, monthlyResults);
        require(missing.isEmpty(), "the Nota Final lacks the results of " + missing);
        List<YearMonth> months = quadrimestre.months();
        monthlyResults.forEach((pack, byMonth) ->
                require(months.containsAll(byMonth.keySet()), pack + " holds results outside " + quadrimestre));
        requireReference(quadrimestre, reference, manifest, fingerprint);
    }

    /** The reference, its manifest and the fingerprint speak of the same quadrimestre, source, pack and municipality. */
    private static void requireReference(
            Quadrimestre quadrimestre,
            ValidatedReference reference,
            SiapsReferenceManifest manifest,
            String fingerprint) {
        String spelling = SiapsFormats.quadrimestre(quadrimestre);
        require(
                spelling.equals(reference.quadrimestre()),
                "the reference is of " + reference.quadrimestre() + ", not " + spelling);
        require(
                spelling.equals(manifest.quadrimestre()),
                "the manifest is of " + manifest.quadrimestre() + ", not " + spelling);
        require(
                reference.sourceKind() == manifest.sourceKind(),
                "the manifest is of another source than the reference");
        require(
                SiapsFormats.ibgeOfSiaps(manifest.municipalityIbge()).equals(reference.municipalityIbge()),
                "the manifest and the reference are of different municipalities");
        require(
                manifest.indicatorCodes().equals(List.of(reference.pack().siapsCode())),
                "the manifest is not that of " + reference.pack().code());
        require(
                fingerprint != null && FINGERPRINT.matcher(fingerprint).matches(),
                "a local source fingerprint is sha256:<64 hex>, not " + fingerprint);
    }

    /** An unmodifiable deep copy, so a probe cannot change what another one reads. */
    static Map<String, Map<YearMonth, List<TeamResult>>> immutable(
            Map<String, Map<YearMonth, List<TeamResult>>> results) {
        Map<String, Map<YearMonth, List<TeamResult>>> copy = new LinkedHashMap<>();
        results.forEach((pack, byMonth) -> {
            Map<YearMonth, List<TeamResult>> months = new LinkedHashMap<>();
            byMonth.forEach((month, teams) -> months.put(month, List.copyOf(teams)));
            copy.put(pack, Collections.unmodifiableMap(months));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
