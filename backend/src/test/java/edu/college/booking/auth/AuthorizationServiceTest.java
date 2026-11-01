package edu.college.booking.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import edu.college.booking.common.ApiProblem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AuthorizationServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthorizationService authorization = new AuthorizationService(accounts);
    private final Account reviewer = new Account(5L, "reviewer", "审核员", "hash", "ACTIVE");
    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken("reviewer", "", List.of());

    @Test
    void disabledAccountCannotContinueUsingExistingSession() {
        given(accounts.findByLogin("reviewer")).willReturn(Optional.of(
                new Account(5L, "reviewer", "审核员", "hash", "DISABLED")));
        ApiProblem error = assertThrows(ApiProblem.class, () -> authorization.requireActive(principal));
        assertEquals("ACCOUNT_DISABLED", error.code());
    }

    @Test
    void roleIsReadFromDatabaseEachTime() {
        given(accounts.findByLogin("reviewer")).willReturn(Optional.of(reviewer));
        ApiProblem error = assertThrows(ApiProblem.class, () -> authorization.requireSystemAdmin(principal));
        assertEquals("FORBIDDEN", error.code());
        given(accounts.grants(5L)).willReturn(List.of(new Grant("SYSTEM_ADMIN", null)));
        assertEquals(reviewer, authorization.requireSystemAdmin(principal));
    }

    @Test
    void reviewerCannotApproveOwnRequest() {
        given(accounts.findByLogin("reviewer")).willReturn(Optional.of(reviewer));
        ApiProblem error = assertThrows(ApiProblem.class,
                () -> authorization.requireReviewerOtherThanApplicant(principal, 5L));
        assertEquals("SELF_APPROVAL_FORBIDDEN", error.code());
    }

    @Test
    void nonReviewerCannotApproveOthersRequest() {
        given(accounts.findByLogin("reviewer")).willReturn(Optional.of(reviewer));
        ApiProblem error = assertThrows(ApiProblem.class,
                () -> authorization.requireReviewerOtherThanApplicant(principal, 6L));
        assertEquals("FORBIDDEN", error.code());
    }
}
