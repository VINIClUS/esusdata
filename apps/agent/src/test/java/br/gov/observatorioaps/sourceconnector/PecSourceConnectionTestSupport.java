package br.gov.observatorioaps.sourceconnector;

import java.sql.Connection;

/** Test-only bridge for binding a mocked or direct fixture connection to source properties. */
public final class PecSourceConnectionTestSupport {

    private PecSourceConnectionTestSupport() {
    }

    public static PecSourceConnection bind(
            Connection connection,
            PecConnectionProperties properties,
            PecSourceIdentity sourceIdentity) {
        return PecSourceConnection.forTest(connection, properties, sourceIdentity);
    }
}
