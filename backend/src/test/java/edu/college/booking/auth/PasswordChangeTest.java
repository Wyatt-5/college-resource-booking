package edu.college.booking.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.college.booking.audit.AuditService;
import edu.college.booking.common.ApiProblem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordChangeTest {
    @Test
    void verifiesOldPasswordAndStoresOnlyTheEncodedNewPassword() {
        var accounts = mock(AccountRepository.class);
        var authorization = mock(AuthorizationService.class);
        var audit = mock(AuditService.class);
        var passwords = new BCryptPasswordEncoder();
        var principal = new UsernamePasswordAuthenticationToken("test", "", List.of());
        String oldHash = passwords.encode("oldpassword123");
        when(authorization.requireActive(principal)).thenReturn(new Account(1, "test", "测试", oldHash, "ACTIVE"));
        var service = new AdminService(accounts, authorization, audit, passwords);
        assertThrows(ApiProblem.class, () -> service.changePassword(principal,
                new AdminService.PasswordChange("wrong", "newpassword123")));
        verify(accounts, never()).replacePassword(anyLong(), anyString(), anyString());
        when(accounts.replacePassword(eq(1L), eq(oldHash), anyString())).thenReturn(1);
        service.changePassword(principal, new AdminService.PasswordChange("oldpassword123", "newpassword123"));
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(accounts).replacePassword(eq(1L), eq(oldHash), hash.capture());
        assertTrue(passwords.matches("newpassword123", hash.getValue()));
    }
}
