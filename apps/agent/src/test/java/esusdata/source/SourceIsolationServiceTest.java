package esusdata.source;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceIsolationCheck.MunicipalityCount;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceIsolationServiceTest {

    @Test
    void countsSplitIntoTheRegisteredMunicipalityOthersAndUnidentified() {
        SourceIsolationService.Summary summary = SourceIsolationService.summarize(
                "3541307",
                List.of(
                        new MunicipalityCount("3541307", 10_029),
                        new MunicipalityCount("3550308", 12),
                        new MunicipalityCount("1100015", 3),
                        new MunicipalityCount(null, 4),
                        new MunicipalityCount("-", 1)));

        assertThat(summary).isEqualTo(new SourceIsolationService.Summary(10_029, 15, 2, 5));
    }

    @Test
    void aBaseWithOnlyTheRegisteredMunicipalityHasNothingElse() {
        SourceIsolationService.Summary summary =
                SourceIsolationService.summarize("3541307", List.of(new MunicipalityCount("3541307", 7)));

        assertThat(summary).isEqualTo(new SourceIsolationService.Summary(7, 0, 0, 0));
    }

    @Test
    void anEmptyCompetenciaHasNoRegisteredAtendimentos() {
        assertThat(SourceIsolationService.summarize("3541307", List.of()))
                .isEqualTo(new SourceIsolationService.Summary(0, 0, 0, 0));
    }

    @Test
    void aPaddedCodeStillMatchesTheRegisteredMunicipality() {
        assertThat(SourceIsolationService.summarize("3541307", List.of(new MunicipalityCount("3541307 ", 2))))
                .isEqualTo(new SourceIsolationService.Summary(2, 0, 0, 0));
    }
}
