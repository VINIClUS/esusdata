package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Runs the transactional team inventory, {@code contracts/compatibility/inventory/tx-team-inventory.sql},
 * over JDBC against a real PEC and writes what it prints to {@code
 * apps/agent/target/inventario-equipe/} (git-ignored, deleted by {@code mvn clean}). It looks, outside
 * the DW, for where the team type (eSF 70, eAP 76) lives, how it reaches the INE the DW uses
 * ({@code tb_dim_equipe.nu_ine}) and the municipality, and whether the type has a history (gap L1,
 * ADR 0031). The procedure, and what may reach the public repository, is the "Inventário
 * transacional do tipo de equipe" section of {@code docs/discovery/runbook-inventario-dw.md}.
 *
 * <p>The gate and the read-only session are {@link LivePecInventory}'s: skipped unless {@code
 * -Dobservatorio.execution-plane.live-pec=true}, with the secret file and a reachable tunnel, so no
 * ordinary build ever reads the production PEC. What each statement may read is decided from its
 * execution plan by {@link TeamInventoryGuard}: aggregates, or small code-domain tables, never a DW
 * fact or a table that names a person. The script is also exercised against a synthetic schema by
 * {@code TeamInventoryScriptTest}.
 */
class TeamInventoryLiveTest {

    static final String SCRIPT_RESOURCE = "/compatibility/inventory/tx-team-inventory.sql";

    @Test
    void writesTheTransactionalTeamInventoryWithoutReadingPersonRows() throws Exception {
        Path output = LivePecInventory.assumeAvailable()
                .run(
                        SCRIPT_RESOURCE,
                        "inventario-equipe",
                        "tx-team-inventory",
                        "Inventário transacional do tipo de equipe do PEC, TeamInventoryLiveTest",
                        TeamInventoryGuard::check);

        assertThat(output).isNotEmptyFile();
    }
}
