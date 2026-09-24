package esusdata.source.pec;

/** Trusted deployment-supplied identity for the connected PEC source. */
public record PecSourceIdentity(String sourceId, String pecVersion, String readModel, String installationRole) {
    public PecSourceIdentity {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must identify the deployment source");
        }
        if (pecVersion == null || !pecVersion.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("pecVersion must be an exact semantic PEC version");
        }
        if (!"PEC_DW".equals(readModel) && !"PEC_OLTP".equals(readModel)) {
            throw new IllegalArgumentException("readModel must be PEC_DW or PEC_OLTP");
        }
        if (!"PRONTUARIO".equals(installationRole)
                && !"CENTRALIZADOR".equals(installationRole)
                && !"UNKNOWN".equals(installationRole)) {
            throw new IllegalArgumentException("installationRole must be PRONTUARIO, CENTRALIZADOR, or UNKNOWN");
        }
    }

    public boolean isComplete() {
        return !"UNKNOWN".equals(installationRole);
    }
}
