package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Runs the L6 and foot-exam DW inventory, {@code contracts/compatibility/inventory/dw-l6-foot-inventory.sql},
 * over JDBC against a real PEC and writes what it prints to {@code apps/agent/target/dw-l6-foot/}
 * (git-ignored, deleted by {@code mvn clean}): the share of {@code
 * tb_fat_visita_domiciliar.nu_medicao_pressao_arterial} that has the "120/80" shape and the other
 * shapes it takes (L6), and the columns that could record the diabetic foot exam, with their
 * non-null counts (C4). Aggregates only, enforced by {@link DwFootInventoryGuard}. The gate and the
 * read-only session are {@link LivePecInventory}'s, so no ordinary build reads the production PEC.
 * The procedure is the "Inventário L6 e exame do pé" section of {@code
 * docs/discovery/runbook-inventario-dw.md}.
 */
class DwFootInventoryLiveTest {

    static final String SCRIPT_RESOURCE = "/compatibility/inventory/dw-l6-foot-inventory.sql";

    @Test
    void writesTheBloodPressureShapeAndFootExamCountsWithoutReadingRows() throws Exception {
        Path output = LivePecInventory.assumeAvailable()
                .run(
                        SCRIPT_RESOURCE,
                        "dw-l6-foot",
                        "dw-l6-foot-inventory",
                        "Inventário L6 e exame do pé do DW do PEC, DwFootInventoryLiveTest",
                        DwFootInventoryGuard::check);

        assertThat(output).isNotEmptyFile();
    }
}
