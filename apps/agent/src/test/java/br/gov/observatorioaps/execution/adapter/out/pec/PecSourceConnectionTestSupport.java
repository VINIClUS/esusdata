package br.gov.observatorioaps.execution.adapter.out.pec;

import java.sql.Connection;
import br.gov.observatorioaps.execution.domain.acquisition.PecConnectionProperties;
import br.gov.observatorioaps.execution.domain.acquisition.PecSourceIdentity;
import br.gov.observatorioaps.execution.domain.acquisition.ReadBudget;
/** Test-only bridge for binding a mocked or direct fixture connection to source properties. */
public final class PecSourceConnectionTestSupport {

    private PecSourceConnectionTestSupport() {
    }

    public static PecSourceConnection bind(
            Connection connection,
            PecConnectionProperties properties,
            PecSourceIdentity sourceIdentity) {
        return bind(connection, properties, sourceIdentity, ReadBudget.initialEngineeringProposal());
    }

    public static PecSourceConnection bind(
            Connection connection,
            PecConnectionProperties properties,
            PecSourceIdentity sourceIdentity,
            ReadBudget readBudget) {
        return PecSourceConnection.forTest(connection, properties, sourceIdentity, readBudget);
    }
}
