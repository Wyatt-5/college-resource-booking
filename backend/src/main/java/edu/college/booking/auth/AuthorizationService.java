package edu.college.booking.auth;

import edu.college.booking.common.ApiProblem;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    private final AccountRepository accounts;

    public AuthorizationService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account requireActive(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        }
        Account account = accounts.findByLogin(authentication.getName())
                .orElseThrow(() -> new ApiProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"));
        if (!account.active()) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已停用");
        }
        return account;
    }

    public UserProfile currentProfile(Authentication authentication) {
        return accounts.profile(requireActive(authentication));
    }

    public Account requireSystemAdmin(Authentication authentication) {
        Account account = requireActive(authentication);
        boolean granted = accounts.grants(account.id()).stream()
                .anyMatch(grant -> "SYSTEM_ADMIN".equals(grant.role()));
        if (!granted) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有系统管理权限");
        }
        return account;
    }

    public Account requireReviewerOtherThanApplicant(Authentication authentication, long applicantId) {
        Account account = requireActive(authentication);
        if (account.id() == applicantId) {
            throw new ApiProblem(HttpStatus.CONFLICT, "SELF_APPROVAL_FORBIDDEN", "不能审核自己的申请");
        }
        if (!accounts.isReviewer(account.id())) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有审核权限");
        }
        return account;
    }
    public Account requireReviewer(Authentication authentication) {
        Account account = requireActive(authentication);
        if (!accounts.isReviewer(account.id())) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有审核权限");
        }
        return account;
    }


    public Account requireSpaceManager(Authentication authentication) {
        Account account = requireActive(authentication);
        if (accounts.grants(account.id()).stream().noneMatch(grant ->
                "SYSTEM_ADMIN".equals(grant.role()) || "COUNSELOR_ADMIN".equals(grant.role())
                        || "RESOURCE_MANAGER".equals(grant.role()))) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有地点管理权限");
        }
        return account;
    }

    public boolean isCounselor(long userId) {
        return accounts.grants(userId).stream().anyMatch(grant -> "COUNSELOR_ADMIN".equals(grant.role()));
    }

    public boolean canManageLocation(Authentication authentication, long locationId) {
        Account account = requireActive(authentication);
        List<Grant> grants = accounts.grants(account.id());
        return grants.stream().anyMatch(grant -> "SYSTEM_ADMIN".equals(grant.role())
                || "COUNSELOR_ADMIN".equals(grant.role())
                || ("RESOURCE_MANAGER".equals(grant.role()) && Long.valueOf(locationId).equals(grant.locationId())));
    }
}
