package esusdata.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Limitation;
import esusdata.indicator.model.Limitation.Kind;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * S2: the standing limitations are typed by the decision records. Only a {@code BLOCKING_GAP}
 * blocks, and until the team-type wiring lands the only one left in each pack is the L1 team type.
 */
class LimitationKindsTest {

    private static PackDescriptor pack(String id) {
        return allPacks().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<PackDescriptor> allPacks() {
        List<PackDescriptor> packs = new ArrayList<>(IndicatorRuleRegistry.all().stream()
                .map(IndicatorRule::descriptor)
                .toList());
        packs.add(ComponentIII.DESCRIPTOR);
        return packs;
    }

    @Test
    void everyLimitationHasAStableCodeOfItsPackAndNoCodeInItsText() {
        for (PackDescriptor d : allPacks()) {
            assertThat(d.standingLimitations()).isNotEmpty();
            for (Limitation l : d.standingLimitations()) {
                assertThat(l.code()).matches("(C[1-7]|CIII)-LIM-\\d{2}");
                assertThat(l.text()).doesNotStartWith(l.code());
                assertThat(l.display()).startsWith(l.code() + ": ");
            }
            assertThat(d.standingLimitations().stream().map(Limitation::code)).doesNotHaveDuplicates();
        }
    }

    @Test
    void theTeamTypeIsTheOnlyBlockingGapOfEachIndicatorPack() {
        Map<String, String> teamGap = Map.of(
                "c1-mais-acesso", "C1-LIM-03",
                "c2-desenvolvimento-infantil", "C2-LIM-05",
                "c3-gestacao-puerperio", "C3-LIM-05",
                "c4-cuidado-diabetes", "C4-LIM-03",
                "c5-cuidado-hipertensao", "C5-LIM-04",
                "c6-cuidado-pessoa-idosa", "C6-LIM-05",
                "c7-prevencao-cancer", "C7-LIM-04");
        teamGap.forEach((id, code) -> assertThat(pack(id).blockingLimitations())
                .extracting(Limitation::code)
                .containsExactly(code));
    }

    @Test
    void componenteIiiHasNoBlockingGap() {
        assertThat(pack("componente-iii-nota-final").blockingLimitations()).isEmpty();
    }

    @Test
    void theHomeVisitPressureAndTheFootFieldAreOutOfReachForThisInstallation() {
        Map<String, Kind> kinds = allPacks().stream()
                .flatMap(d -> d.standingLimitations().stream())
                .collect(Collectors.toMap(Limitation::code, Limitation::kind));
        assertThat(kinds)
                .containsEntry("C4-LIM-05", Kind.OUT_OF_REACH)
                .containsEntry("C4-LIM-07", Kind.OUT_OF_REACH)
                .containsEntry("C5-LIM-10", Kind.OUT_OF_REACH);
        assertThat(pack("c4-cuidado-diabetes").standingLimitationLines())
                .anyMatch(l -> l.startsWith("C4-LIM-05:") && l.contains("não está registrada no DW desta instalação"));
    }
}
