package esusdata.auth.dto;

import java.util.List;

/**
 * {@code municipalities}: where the caller may read the municipal aggregate (READ_CLINICAL), so a
 * client can pick its scope at runtime instead of having it baked in at build time.
 */
public record MeResponse(String userId, List<String> municipalities) {}
