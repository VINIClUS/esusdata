package esusdata.auth.dto;

import java.util.List;

/** {@code GET /users}: one account and its active grants. No password material, ever. */
public record UserResponse(
        String userId,
        String username,
        String displayName,
        String state,
        String createdAt,
        String lastLoginAt,
        List<GrantResponse> grants) {}
