package edu.college.booking.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import edu.college.booking.audit.AuditService;
import edu.college.booking.common.ApiProblem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

class LastSystemAdminGuardTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AdminService service = new AdminService(accounts, authorization, audit, passwords);

    private final Account actor = new Account(1L, "admin", "管理员", "hash", "ACTIVE");
    private final Account target = new Account(2L, "other-admin", "另一位管理员", "hash", "ACTIVE");
    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken("admin", "", List.of());

    @Test
    void cannotDisableLastActiveSystemAdmin() {
        given(authorization.requireSystemAdmin(principal)).willReturn(actor);
        given(accounts.findById(2L)).willReturn(Optional.of(target));
        given(accounts.hasUnfinishedBookings(2L)).willReturn(false);
        given(accounts.isSystemAdmin(2L)).willReturn(true);
        given(accounts.countActiveSystemAdmins()).willReturn(1L);

        ApiProblem error = assertThrows(ApiProblem.class,
                () -> service.disable(principal, 2L));
        assertEquals("LAST_SYSTEM_ADMIN", error.code());
        verify(accounts, never()).disable(2L);
    }

    @Test
    void canDisableSystemAdminWhenAnotherRemains() {
        given(authorization.requireSystemAdmin(principal)).willReturn(actor);
        given(accounts.findById(2L)).willReturn(Optional.of(target));
        given(accounts.hasUnfinishedBookings(2L)).willReturn(false);
        given(accounts.isSystemAdmin(2L)).willReturn(true);
        given(accounts.countActiveSystemAdmins()).willReturn(2L);
        given(accounts.profile(target)).willReturn(new UserProfile(2L, "other-admin", "另一位管理员",
                "ACTIVE", List.of(), false, ""));

        service.disable(principal, 2L);
        verify(accounts).disable(2L);
    }

    @Test
    void cannotStripLastActiveSystemAdminOfRole() {
        given(authorization.requireSystemAdmin(principal)).willReturn(actor);
        given(accounts.findById(2L)).willReturn(Optional.of(target));
        given(accounts.isSystemAdmin(2L)).willReturn(true);
        given(accounts.countActiveSystemAdmins()).willReturn(1L);

        // 新授权里不再包含 SYSTEM_ADMIN（降级为审核员）
        var update = new AdminService.GrantUpdate(List.of(), true);
        ApiProblem error = assertThrows(ApiProblem.class,
                () -> service.replaceGrants(principal, 2L, update));
        assertEquals("LAST_SYSTEM_ADMIN", error.code());
        verify(accounts, never()).replaceGrants(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyLong());
    }
}
