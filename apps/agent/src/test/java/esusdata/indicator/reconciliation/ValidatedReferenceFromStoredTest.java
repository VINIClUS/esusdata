package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.NormalizedReference.IndicatorRow;
import esusdata.indicator.reconciliation.OfficialTeamExportCsvParser.ExpectedScope;
import esusdata.indicator.reconciliation.ValidatedReference.UniverseConfidence;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reference a diagnostic run compares with is the stored subset, loaded again: it must be the
 * reference the export it was taken from gives, for every pack and the Nota Final, and it must
 * not be built from anything that is not an official team export of its own pack.
 */
class ValidatedReferenceFromStoredTest {

    private static final OffsetDateTime CAPTURED = OffsetDateTime.of(2026, 10, 8, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String OTHER_FILE_NAME = "export.csv";

    @TempDir
    Path artifacts;

    private ValidatedReference storedAndLoaded(OfficialTeamReference export, byte[] raw, GatePack pack)
            throws IOException {
        ReferenceArtifactStore store = new ReferenceArtifactStore(artifacts);
        NormalizedReference subset = export.subset(pack);
        CaptureMetadata metadata = new CaptureMetadata(
                SiapsReferenceManifest.referenceId(
                        "zz",
                        export.municipalityIbge(),
                        export.quadrimestre(),
                        pack,
                        SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                        1),
                CAPTURED,
                export.officialGeneratedAt(),
                "SIAPS / Avaliação do Quadrimestre",
                OTHER_FILE_NAME);
        SiapsReferenceManifest manifest = store.store(raw, subset, metadata);
        return ValidatedReference.fromStored(store.load(manifest), pack);
    }

    @Test
    void theStoredSubsetOfEveryPackGivesTheReferenceOfTheExportItWasTakenFrom() throws IOException {
        byte[] raw = SiapsTeamExportFixtures.standard().bytes();
        OfficialTeamReference export = OfficialTeamExportCsvParser.parse(
                raw, ExpectedScope.ofMunicipality(SiapsTeamExportFixtures.MUNICIPALITY_IBGE));

        for (GatePack pack : GatePack.allWithNotaFinal()) {
            ValidatedReference stored = storedAndLoaded(export, raw, pack);

            assertThat(stored).as(pack.code()).isEqualTo(ValidatedReference.from(export, pack));
            assertThat(stored.gaps()).as(pack.code()).isEmpty();
            assertThat(stored.universe()).isEqualTo(UniverseConfidence.OFFICIAL);
            assertThat(stored.sourceKind()).isEqualTo(SourceKind.OFFICIAL_TEAM_EXPORT_CSV);
        }
    }

    @Test
    void aTypeWithNoTeamInTheSubsetIsAnExplicitZeroOfTheOfficialUniverse() throws IOException {
        byte[] raw = SiapsTeamExportFixtures.export()
                .team(
                        SiapsTeamExportFixtures.ESF_1,
                        SiapsParser.ESF,
                        Classification.BOM,
                        SiapsTeamExportFixtures.all(Classification.BOM))
                .bytes();
        OfficialTeamReference export = OfficialTeamExportCsvParser.parse(
                raw, ExpectedScope.ofMunicipality(SiapsTeamExportFixtures.MUNICIPALITY_IBGE));

        ValidatedReference stored = storedAndLoaded(export, raw, GatePack.all().getFirst());

        assertThat(stored.counts().get(SiapsParser.EAP)).isEqualTo(ClassCounts.EMPTY);
        assertThat(stored.counts().get(SiapsParser.ESF).total()).isEqualTo(1);
        assertThat(stored)
                .isEqualTo(ValidatedReference.from(export, GatePack.all().getFirst()));
    }

    @Test
    void aSubsetOfAnotherPackIsRefused() {
        NormalizedReference c2 = subset(GatePack.all().get(1).siapsCode(), SourceKind.OFFICIAL_TEAM_EXPORT_CSV);
        GatePack c1 = GatePack.all().getFirst();

        assertThatThrownBy(() -> ValidatedReference.fromStored(c2, c1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("C1");
    }

    @Test
    void aSubsetThatIsNotAnOfficialTeamExportHasNoHistoricalUniverseAndIsRefused() {
        GatePack c1 = GatePack.all().getFirst();
        NormalizedReference aggregate = subset(c1.siapsCode(), SourceKind.PUBLIC_AGGREGATE);

        assertThatThrownBy(() -> ValidatedReference.fromStored(aggregate, c1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historical universe");
    }

    private static NormalizedReference subset(int indicatorCode, SourceKind kind) {
        return new NormalizedReference(
                SiapsTeamExportFixtures.MUNICIPALITY_IBGE,
                SiapsTeamExportFixtures.QUADRIMESTRE,
                kind,
                OfficialTeamExportCsvParser.PARSER_VERSION,
                OfficialStatus.PRELIMINARY,
                indicatorCode,
                List.of(new IndicatorRow(
                        SiapsTeamExportFixtures.ESF_1,
                        SiapsParser.ESF,
                        new BigDecimal("63"),
                        Classification.BOM,
                        new BigDecimal("0.75"),
                        BigDecimal.ONE,
                        new BigDecimal("0.75"))));
    }

    @Test
    void theStoreKeepsTheGenerationMomentOutOfTheContentTheReferenceIsBuiltFrom() throws IOException {
        byte[] raw = SiapsTeamExportFixtures.standard().bytes();
        byte[] later = SiapsTeamExportFixtures.standard()
                .generatedAt("09 de outubro de 2026 - 08:15h")
                .bytes();
        ExpectedScope scope = ExpectedScope.ofMunicipality(SiapsTeamExportFixtures.MUNICIPALITY_IBGE);
        OfficialTeamReference first = OfficialTeamExportCsvParser.parse(raw, scope);
        OfficialTeamReference second = OfficialTeamExportCsvParser.parse(later, scope);
        GatePack c1 = GatePack.all().getFirst();

        assertThat(second.officialGeneratedAt()).isNotEqualTo(first.officialGeneratedAt());
        assertThat(storedAndLoaded(second, later, c1)).isEqualTo(storedAndLoaded(first, raw, c1));
    }
}
