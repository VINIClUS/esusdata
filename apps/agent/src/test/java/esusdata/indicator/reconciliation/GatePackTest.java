package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatePackTest {

    @Test
    void theTableHasTheSevenPacksAndFindsThemByTheirCodes() {
        assertThat(GatePack.all()).hasSize(7);
        assertThat(GatePack.bySiapsCode(110)).map(GatePack::code).contains("C1");
        assertThat(GatePack.byPackId(GatePack.all().get(6).packId()))
                .map(GatePack::code)
                .contains("C7");
        assertThat(GatePack.allWithNotaFinal()).hasSize(8).endsWith(GatePack.NOTA_FINAL);
        assertThat(GatePack.bySiapsCode(0)).isEmpty();
    }

    @Test
    void theCheckOfEveryPackIsTheOneThePolicyDeclaresForIt() {
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            assertThat(pack.checkId()).as(pack.code()).isEqualTo(ReferencePolicy.checkFor(pack.packId()));
            assertThat(pack.checkId()).as(pack.code()).endsWith("@2");
        }
        assertThat(GatePack.NOTA_FINAL.checkId()).isEqualTo(ReferencePolicy.CHECK_NOTA_FINAL);
        assertThat(GatePack.all().getFirst().checkId()).isEqualTo(ReferencePolicy.CHECK_DISTRIBUTION);
        assertThat(Comparison.CHECK_ID).isEqualTo(ReferencePolicy.CHECK_DISTRIBUTION);
        assertThat(Comparison.CHECK_ID_NOTA_FINAL).isEqualTo(ReferencePolicy.CHECK_NOTA_FINAL);
    }
}
