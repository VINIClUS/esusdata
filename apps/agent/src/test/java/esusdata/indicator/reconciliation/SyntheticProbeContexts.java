package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.SiapsSnapshot.Team;
import esusdata.indicator.reconciliation.ValidatedReference.UniverseConfidence;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Probe contexts over invented data, for the tests of the probes that live in the package of their
 * pack (C2 to C7) and so cannot build the package-private reference and manifest themselves. The
 * reference holds the teams it is given and no official class; the manifest and the fingerprint
 * are fixed fakes. Never for a real run.
 */
public final class SyntheticProbeContexts {

    private static final String FAKE_FINGERPRINT = "sha256:" + "0".repeat(64);

    private SyntheticProbeContexts() {}

    /**
     * The context of the quadrimestre of {@code inputs} (its four months, in order), with the
     * baseline the rule gives each month today and a revision made of {@code revisionTeams}.
     *
     * @param inputs the four monthly inputs of one pack and municipality
     * @param revisionTeams INE to {@code eSF} or {@code eAP}
     */
    public static PackProbeContext pack(List<PackInput> inputs, Map<String, String> revisionTeams) {
        PackInput first = inputs.getFirst();
        Quadrimestre quadrimestre = Quadrimestre.of(first.context().competencia());
        String municipality = first.context().municipalityIbge();
        GatePack pack = GatePack.byPackId(first.rule().descriptor().id()).orElseThrow();
        List<RuleOutcome> baseline = inputs.stream()
                .map(input -> input.rule().evaluate(input.data(), input.context()))
                .toList();
        return new PackProbeContext(
                quadrimestre,
                inputs,
                baseline,
                reference(pack, municipality, SiapsFormats.quadrimestre(quadrimestre), revisionTeams),
                manifest(pack, municipality, SiapsFormats.quadrimestre(quadrimestre)),
                FAKE_FINGERPRINT);
    }

    private static ValidatedReference reference(
            GatePack pack, String municipality, String quadrimestre, Map<String, String> revisionTeams) {
        List<Team> teams = new ArrayList<>();
        revisionTeams.forEach((ine, type) -> teams.add(new Team(ine, type)));
        return new ValidatedReference(
                pack,
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                municipality,
                quadrimestre,
                UniverseConfidence.OFFICIAL,
                List.of(),
                Map.of(SiapsParser.ESF, ClassCounts.EMPTY, SiapsParser.EAP, ClassCounts.EMPTY),
                teams,
                Map.of());
    }

    private static SiapsReferenceManifest manifest(GatePack pack, String municipality, String quadrimestre) {
        return new SiapsReferenceManifest(
                SiapsReferenceManifest.referenceId(
                        "SP", municipality, quadrimestre, pack, SourceKind.OFFICIAL_TEAM_EXPORT_CSV, 1),
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                municipality,
                quadrimestre,
                OffsetDateTime.parse("2026-10-08T12:00:00-03:00"),
                LocalDateTime.parse("2026-10-08T11:00:00"),
                OfficialStatus.FINAL,
                "synthetic probe context",
                SiapsReferenceManifest.sourceFilenameOf("a".repeat(64)),
                "a".repeat(64),
                "b".repeat(64),
                OfficialTeamExportCsvParser.PARSER_VERSION,
                1,
                List.of(pack.siapsCode()),
                List.of(SiapsParser.EAP, SiapsParser.ESF),
                false,
                List.of());
    }
}
