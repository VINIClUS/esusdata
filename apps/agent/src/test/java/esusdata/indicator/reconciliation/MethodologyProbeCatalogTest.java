package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MethodologyProbeCatalogTest {

    @Test
    void everyProbeHasAStableUniqueIdAndServesKnownPacks() {
        List<MethodologyProbe> all = MethodologyProbeCatalog.all();
        Set<String> packs =
                GatePack.allWithNotaFinal().stream().map(GatePack::packId).collect(Collectors.toSet());

        assertThat(all).isNotEmpty();
        assertThat(all).extracting(MethodologyProbe::id).doesNotHaveDuplicates();
        assertThat(all).allSatisfy(probe -> {
            assertThat(probe.id()).matches(MethodologyProfile.STABLE_ID);
            assertThat(packs).containsAll(probe.packs());
        });
    }

    @Test
    void theProbesOfAPackAreExactlyThoseThatServeIt() {
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            List<MethodologyProbe> probes = MethodologyProbeCatalog.forPack(pack.packId());

            assertThat(probes).allSatisfy(probe -> assertThat(probe.packs()).contains(pack.packId()));
            assertThat(MethodologyProbeCatalog.all().stream()
                            .filter(probe -> probe.packs().contains(pack.packId()))
                            .map(MethodologyProbe::id)
                            .toList())
                    .containsExactlyElementsOf(
                            probes.stream().map(MethodologyProbe::id).toList());
        }
        assertThat(MethodologyProbeCatalog.forPack("no-such-pack")).isEmpty();
    }
}
