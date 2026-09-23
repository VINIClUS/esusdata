package esusdata.source.pec;

/**
 * Resolves a {@code secretRef} to the actual credential value at the point of use. The API
 * response layer must only ever return {@code secretRef} / state, never the value (§1.12.7) — this
 * interface is the one seam allowed to see the real password, and only in memory, only for the
 * duration of establishing a connection.
 */
@FunctionalInterface
public interface PecSecretResolver {
    char[] resolve(String secretRef);
}
