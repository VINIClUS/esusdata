package br.gov.observatorioaps.sourceconnector.infrastructure.jdbc;

import java.sql.Connection;
import br.gov.observatorioaps.sourceconnector.domain.PecConnectionProperties;
import br.gov.observatorioaps.sourceconnector.domain.PecSourceIdentity;
import br.gov.observatorioaps.sourceconnector.domain.ReadBudget;
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
