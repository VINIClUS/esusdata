package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Quadrimestre;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EligibilityTest {

    private static GatePack packSignedOn(LocalDate date) {
        return new GatePack("c9-invented", "C9", 199, date);
    }

    @Test
    void aSignatureOfJuneMakesTheSecondQuadrimestreTheFirstEligible() {
        GatePack pack = packSignedOn(LocalDate.of(2026, 6, 24));

        assertThat(Eligibility.firstEligible(pack)).isEqualTo(new Quadrimestre(2026, 2));
        assertThat(Eligibility.waitingFor(pack)).isEqualTo("aguardando 2026Q2 no SIAPS");
    }

    @Test
    void aSignatureOnTheLastDayOfAQuadrimestreDoesNotMakeItEligible() {
        assertThat(Eligibility.firstEligible(packSignedOn(LocalDate.of(2026, 8, 31))))
                .isEqualTo(new Quadrimestre(2026, 3));
        assertThat(Eligibility.firstEligible(packSignedOn(LocalDate.of(2026, 8, 30))))
                .isEqualTo(new Quadrimestre(2026, 2));
    }

    @Test
    void theYearRollsOverAfterTheThirdQuadrimestre() {
        assertThat(Eligibility.firstEligible(packSignedOn(LocalDate.of(2026, 12, 31))))
                .isEqualTo(new Quadrimestre(2027, 1));
    }

    @Test
    void whenOnlyTheIneligibleQuadrimestreIsPublishedThereIsNoReference() {
        GatePack pack = packSignedOn(LocalDate.of(2026, 6, 24));

        assertThat(Eligibility.reference(pack, List.of("2026Q1", "2025Q3"))).isEmpty();
    }

    @Test
    void theReferenceIsTheMostRecentPublishedEligibleQuadrimestre() {
        GatePack pack = packSignedOn(LocalDate.of(2026, 6, 24));

        assertThat(Eligibility.reference(pack, List.of("2026Q1", "2026Q2", "2026Q3")))
                .contains(new Quadrimestre(2026, 3));
        assertThat(Eligibility.reference(pack, List.of("2026Q1", "2026Q2"))).contains(new Quadrimestre(2026, 2));
    }

    @Test
    void theNt8SignatureIsAFloorWhenItIsLaterThanTheFicha() {
        GatePack early = packSignedOn(LocalDate.of(2026, 4, 1));

        assertThat(early.floor()).isEqualTo(GatePack.NT8_LAST_SIGNATURE);
        assertThat(Eligibility.firstEligible(early)).isEqualTo(new Quadrimestre(2026, 2));
    }

    @Test
    void everyPackOfTheTableWaitsForTheSecondQuadrimestreOf2026() {
        for (GatePack pack : GatePack.all()) {
            assertThat(Eligibility.firstEligible(pack)).as(pack.code()).isEqualTo(new Quadrimestre(2026, 2));
        }
        assertThat(GatePack.all()).hasSize(7);
        assertThat(GatePack.bySiapsCode(110)).map(GatePack::code).contains("C1");
        assertThat(GatePack.byPackId(GatePack.all().get(6).packId()))
                .map(GatePack::code)
                .contains("C7");
    }

    @Test
    void quadrimestreSpellingsConvertBothWays() {
        assertThat(SiapsFormats.quadrimestre("2026Q2")).isEqualTo(Quadrimestre.parse("2026-Q2"));
        assertThat(SiapsFormats.quadrimestre("2º Quadrimestre/2026")).isEqualTo(Quadrimestre.parse("2026-Q2"));
        assertThat(SiapsFormats.quadrimestre(Quadrimestre.parse("2025-Q3"))).isEqualTo("2025Q3");
        assertThatThrownBy(() -> SiapsFormats.quadrimestre("Q3/25")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void municipalityAndIneAreNormalized() {
        assertThat(SiapsFormats.ibgeOfSiaps("3541307")).isEqualTo("354130");
        assertThat(SiapsFormats.ibgeOfSiaps("354130")).isEqualTo("354130");
        assertThat(SiapsFormats.ine("11")).isEqualTo("0000000011");
        assertThat(SiapsFormats.ine("0000000011")).isEqualTo("0000000011");
        assertThatThrownBy(() -> SiapsFormats.ine("12345678901")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsFormats.ine("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiapsFormats.ibgeOfSiaps("35")).isInstanceOf(IllegalArgumentException.class);
    }
}
