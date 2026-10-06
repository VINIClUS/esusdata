package esusdata.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** The catalog lists every rule plus the Nota Final, which nothing enqueues (ADR 0030). */
class IndicatorPackCatalogTest {

    @Test
    void listsTheSevenRulesAndTheComponentIIIComposite() {
        assertThat(IndicatorPackCatalog.all())
                .extracting(IndicatorPackCatalog.PackEntry::code)
                .containsExactly("C1", "C2", "C3", "C4", "C5", "C6", "C7", "Componente III");
        assertThat(IndicatorPackCatalog.all())
                .filteredOn(p -> !p.runnable())
                .singleElement()
                .satisfies(p -> assertThat(p.id()).isEqualTo(ComponentIII.ID));
    }

    @Test
    void componentIIIWeighsTheSevenIndicatorsToTen() {
        IndicatorPackCatalog.PackEntry nota =
                IndicatorPackCatalog.find(ComponentIII.ID).orElseThrow();
        assertThat(nota.valueKind()).isEqualTo(ValueKind.FINAL_SCORE);
        assertThat(nota.components().stream().map(ComponentSpec::weight).reduce(BigInteger.ZERO, BigInteger::add))
                .isEqualTo(BigInteger.TEN);
        assertThat(nota.dependsOn()).hasSize(7);
        assertThat(nota.components()).extracting(ComponentSpec::code).containsExactlyElementsOf(nota.dependsOn());
        assertThat(nota.executionEnabled()).isFalse();
    }

    @Test
    void c1IsFiledUnderItsRealFamily() {
        IndicatorPackCatalog.PackEntry c1 =
                IndicatorPackCatalog.find("c1-mais-acesso").orElseThrow();
        assertThat(c1.family()).isEqualTo("QUALIDADE_ESF_EAP");
        assertThat(c1.title()).isEqualTo("Mais acesso");
        // Only D: A passed on 2026-10-06 and B on closing C1-LIM-03; Portão C is decided per source, not by the
        // catalog.
        assertThat(c1.blockedGates()).hasSize(1);
        assertThat(IndicatorPackCatalog.find("c9-inexistente")).isEmpty();
    }

    @Test
    void theResponseCarriesWeightsAsIntegerStrings() {
        IndicatorPackResponse c4 = IndicatorPackResponse.from(
                IndicatorPackCatalog.find("c4-cuidado-diabetes").orElseThrow());
        assertThat(c4.valueKind()).isEqualTo("SCORE");
        assertThat(c4.components())
                .extracting(IndicatorPackResponse.Component::weight)
                .containsExactly("20", "15", "15", "20", "15", "15");
        assertThat(c4.runnable()).isTrue();
    }
}
