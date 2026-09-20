package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AccessAdministrationService;
import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.Grant;
import br.gov.observatorioaps.identityaccess.Permission;
import br.gov.observatorioaps.identityaccess.Role;
import br.gov.observatorioaps.identityaccess.ScopeKind;
import br.gov.observatorioaps.identityaccess.UserProvisioning;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * §1.12.6 L513 / plan decision (ADR 0008): user/grant administration, the destravamento the
 * bootstrap admin needs to make the piloto operable (TECHNICAL_ADMIN has no {@code read_clinical}/
 * {@code run_indicator} — see {@code BootstrapActivation}). Every mutation requires {@code
 * MANAGE_ACCESS} at {@code INSTALLATION} scope plus reauthentication within the last few minutes
 * (§1.12.7 L539) and goes through {@link AccessAdministrationService}, which alone decides whether
 * a grant/revoke/block also revokes the target's sessions (§1.12.7 L541).
 */
@RestController
public class AdminController {

    private final UserProvisioning userProvisioning;
    private final AccessAdministrationService accessAdministrationService;
    private final ApiAuthorization authorization;

    public AdminController(
            UserProvisioning userProvisioning, AccessAdministrationService accessAdministrationService,
            ApiAuthorization authorization) {
        this.userProvisioning = userProvisioning;
        this.accessAdministrationService = accessAdministrationService;
        this.authorization = authorization;
    }

    @PostMapping("/api/v1/users")
    public ResponseEntity<CreateUserResponse> createUser(
            @AuthenticationPrincipal AuthenticatedSession session, @RequestBody CreateUserRequest request) {
        authorization.requireInstallationPermission(session, Permission.MANAGE_ACCESS);
        authorization.requireRecentReauth(session);
        UserProvisioning.ProvisionedUser provisioned =
                userProvisioning.provision(request.username(), request.displayName(), session.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateUserResponse(
                provisioned.userId(), provisioned.activationToken(), provisioned.expiresAt().toString()));
    }

    @PostMapping("/api/v1/users/{id}/grants")
    public ResponseEntity<GrantResponse> grant(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String userId,
            @RequestBody CreateGrantRequest request) {
        authorization.requireInstallationPermission(session, Permission.MANAGE_ACCESS);
        authorization.requireRecentReauth(session);
        if (request.role() == null || request.scopeKind() == null) {
            throw new IllegalArgumentException("role and scopeKind are required");
        }
        Grant grant = accessAdministrationService.grant(
                userId, Role.valueOf(request.role()), ScopeKind.valueOf(request.scopeKind()),
                request.municipalityIbge(), request.cnes(), request.ine(), session.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(grant));
    }

    @DeleteMapping("/api/v1/users/{id}/grants/{grantId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String userId,
            @PathVariable("grantId") String grantId) {
        authorization.requireInstallationPermission(session, Permission.MANAGE_ACCESS);
        authorization.requireRecentReauth(session);
        accessAdministrationService.revoke(userId, grantId, session.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/users/{id}/block")
    public ResponseEntity<Void> block(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String userId) {
        authorization.requireInstallationPermission(session, Permission.MANAGE_ACCESS);
        authorization.requireRecentReauth(session);
        accessAdministrationService.block(userId, session.userId());
        return ResponseEntity.noContent().build();
    }

    private GrantResponse toResponse(Grant grant) {
        return new GrantResponse(
                grant.grantId(), grant.userId(), grant.role().name(), grant.scopeKind().name(),
                grant.municipalityIbge(), grant.cnes(), grant.ine(), grant.grantedAt().toString(),
                grant.grantedBy());
    }
}
