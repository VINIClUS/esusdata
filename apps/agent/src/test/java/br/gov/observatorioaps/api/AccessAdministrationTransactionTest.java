package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AccessAdministrationService;
import br.gov.observatorioaps.identityaccess.AuthorizationVersionGuard;
import br.gov.observatorioaps.identityaccess.Grant;
import br.gov.observatorioaps.identityaccess.Role;
import br.gov.observatorioaps.identityaccess.ScopeKind;
import br.gov.observatorioaps.identityaccess.UserState;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** §1.12.7 L541: access mutations and session invalidation must be one atomic operation. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccessAdministrationTransactionTest extends ApiFixtureSupport {

    private static final String MUNICIPALITY = "3541307";
    private static final String ACTOR = "access-admin";

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void failedGrantRollsBackTheInsertedGrant() {
        String target = createUser("grant-target-" + System.nanoTime());
        AccessAdministrationService service = serviceWhoseGuardFails();

        assertThatThrownBy(() -> service.grant(target, Role.MANAGER, ScopeKind.MUNICIPALITY,
                MUNICIPALITY, null, null, ACTOR)).isInstanceOf(IllegalStateException.class);

        assertThat(grantRepository.activeGrantsForUser(target)).isEmpty();
    }

    @Test
    void failedRevokeRollsBackTheRevocation() {
        String target = createUser("revoke-target-" + System.nanoTime());
        grantTeam(target, Role.MANAGER, MUNICIPALITY, null, null);
        Grant existing = grantRepository.activeGrantsForUser(target).get(0);
        AccessAdministrationService service = serviceWhoseGuardFails();

        assertThatThrownBy(() -> service.revoke(target, existing.grantId(), ACTOR))
                .isInstanceOf(IllegalStateException.class);

        assertThat(grantRepository.activeGrantsForUser(target))
                .singleElement().extracting(Grant::grantId).isEqualTo(existing.grantId());
    }

    @Test
    void failedBlockRollsBackTheUserStateChange() {
        String target = createUser("block-target-" + System.nanoTime());
        AccessAdministrationService service = serviceWhoseGuardFails();

        assertThatThrownBy(() -> service.block(target, ACTOR)).isInstanceOf(IllegalStateException.class);

        assertThat(userRepository.findById(target).orElseThrow().state()).isEqualTo(UserState.ACTIVE);
    }

    private AccessAdministrationService serviceWhoseGuardFails() {
        AuthorizationVersionGuard failingGuard = mock(AuthorizationVersionGuard.class);
        doThrow(new IllegalStateException("guard failed")).when(failingGuard)
                .bumpAndRevokeSessions(anyString(), anyString(), anyString(), anyString());
        return new AccessAdministrationService(
                userRepository, grantRepository, failingGuard, transactionTemplate, clock);
    }
}
