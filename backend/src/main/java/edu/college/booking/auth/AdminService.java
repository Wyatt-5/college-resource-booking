package edu.college.booking.auth;

import edu.college.booking.audit.AuditService;
import edu.college.booking.common.ApiProblem;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    public record NewUser(String loginName, String displayName, String password) {}
    public record GrantUpdate(List<Grant> grants, boolean reviewer) {}

    private final AccountRepository accounts;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final PasswordEncoder passwords;

    public AdminService(AccountRepository accounts, AuthorizationService authorization,
            AuditService audit, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.authorization = authorization;
        this.audit = audit;
        this.passwords = passwords;
    }

    @Transactional
    public UserProfile create(Authentication authentication, NewUser input) {
        Account actor = authorization.requireSystemAdmin(authentication);
        if (input == null || input.loginName() == null
                || !input.loginName().matches("[a-zA-Z0-9._-]{3,40}")
                || input.displayName() == null || input.displayName().isBlank()
                || input.displayName().length() > 100
                || input.password() == null || input.password().length() < 12) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "账号须为 3–40 位字母数字或 ._-，姓名不能为空，密码至少 12 位");
        }
        String loginName = input.loginName().toLowerCase(Locale.ROOT);
        try {
            long id = accounts.createUser(loginName, input.displayName().trim(), passwords.encode(input.password()));
            UserProfile profile = accounts.profile(accounts.findById(id).orElseThrow());
            audit.record(actor.id(), "USER_CREATED", "USER", id, null, profile);
            return profile;
        } catch (DuplicateKeyException exception) {
            throw new ApiProblem(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "账号已存在");
        }
    }

    public List<UserProfile> list(Authentication authentication) {
        authorization.requireSystemAdmin(authentication);
        return accounts.listUsers();
    }

    @Transactional
    public UserProfile replaceGrants(Authentication authentication, long targetId, GrantUpdate input) {
        Account actor = authorization.requireSystemAdmin(authentication);
        if (actor.id() == targetId) {
            throw new ApiProblem(HttpStatus.CONFLICT, "SELF_GRANT_FORBIDDEN", "不能修改自己的授权");
        }
        Account target = accounts.findById(targetId)
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "账号不存在"));
        if (input == null || input.grants() == null) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "必须提供授权列表");
        }
        Set<Grant> unique = new HashSet<>(input.grants());
        if (unique.size() != input.grants().size()) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "授权不能重复");
        }
        for (Grant grant : input.grants()) {
            if (grant == null || !Set.of("COUNSELOR_ADMIN", "RESOURCE_MANAGER", "SYSTEM_ADMIN")
                    .contains(grant.role())) {
                throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "角色无效");
            }
            if ("RESOURCE_MANAGER".equals(grant.role())) {
                if (grant.locationId() == null || !accounts.locationExists(grant.locationId())) {
                    throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "资源管理员须指定有效地点");
                }
            } else if (grant.locationId() != null) {
                throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "该角色不能指定地点");
            }
        }
        UserProfile before = accounts.profile(target);
        accounts.replaceGrants(targetId, input.grants(), input.reviewer(), actor.id());
        UserProfile after = accounts.profile(target);
        audit.record(actor.id(), "GRANTS_REPLACED", "USER", targetId, before, after);
        return after;
    }
}
