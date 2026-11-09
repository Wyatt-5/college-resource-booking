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
    public record NewUser(String loginName, String displayName, String password, String department) {
        public NewUser(String loginName, String displayName, String password) { this(loginName, displayName, password, null); }
    }
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
        String department = input.department() == null ? "" : input.department().strip();
        if (department.length() > 100 || (!department.isEmpty() && !accounts.departmentExists(department)))
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请选择有效的部门");
        String loginName = input.loginName().toLowerCase(Locale.ROOT);
        var old = accounts.lockByLogin(loginName);
        if (old.isPresent()) {
            Account existing = old.get();
            if (existing.active()) throw new ApiProblem(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "账号已存在");
            if (!existing.displayName().strip().equals(input.displayName().strip()))
                throw new ApiProblem(HttpStatus.CONFLICT, "ACCOUNT_NAME_MISMATCH", "该账号曾属于其他姓名，不能直接重新启用，请使用其他账号名");
            UserProfile before = accounts.profile(existing);
            accounts.reactivate(existing.id(), passwords.encode(input.password()));
            accounts.replaceGrants(existing.id(), List.of(), false, actor.id());
            if (!department.isEmpty()) accounts.setDepartment(existing.id(), department);
            UserProfile after = accounts.profile(accounts.findById(existing.id()).orElseThrow());
            audit.record(actor.id(), "USER_REACTIVATED", "USER", existing.id(), before, after);
            return after;
        }
        try {
            long id = accounts.createUser(loginName, input.displayName().trim(), passwords.encode(input.password()));
            if (!department.isEmpty()) accounts.setDepartment(id, department);
            UserProfile profile = accounts.profile(accounts.findById(id).orElseThrow());
            audit.record(actor.id(), "USER_CREATED", "USER", id, null, profile);
            return profile;
        } catch (DuplicateKeyException exception) {
            throw new ApiProblem(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "账号已存在");
        }
    }

    @Transactional
    public void disable(Authentication authentication, long targetId) {
        Account actor = authorization.requireSystemAdmin(authentication);
        if (actor.id() == targetId) throw new ApiProblem(HttpStatus.CONFLICT, "SELF_DISABLE_FORBIDDEN", "不能移除当前登录的账号");
        Account target = accounts.findById(targetId).orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "账号不存在"));
        if (accounts.hasUnfinishedBookings(targetId)) throw new ApiProblem(HttpStatus.CONFLICT, "USER_HAS_BOOKINGS", "该账号仍有待处理预约或未完成借还，请先处理");
        UserProfile before = accounts.profile(target);
        accounts.disable(targetId);
        audit.record(actor.id(), "USER_DISABLED", "USER", targetId, before, accounts.profile(accounts.findById(targetId).orElseThrow()));
    }

    public record PasswordChange(String oldPassword, String newPassword) {}

    @Transactional
    public void changePassword(Authentication authentication, PasswordChange input) {
        Account account = authorization.requireActive(authentication);
        if (input == null || input.oldPassword() == null || input.newPassword() == null
                || input.newPassword().length() < 12
                || input.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "新密码至少 12 位，UTF-8 编码不超过 72 字节");
        }
        if (!passwords.matches(input.oldPassword(), account.passwordHash())) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "原密码不正确");
        }
        if (accounts.replacePassword(account.id(), account.passwordHash(), passwords.encode(input.newPassword())) != 1) {
            throw new ApiProblem(HttpStatus.CONFLICT, "PASSWORD_CHANGED", "密码已发生变化，请重新登录后重试");
        }
        audit.record(account.id(), "PASSWORD_CHANGED", "USER", account.id(), null, null);
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
            if (grant == null || !"SYSTEM_ADMIN".equals(grant.role()) || grant.locationId() != null)
                throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "仅支持系统管理员或审核员权限");
        }
        if (!input.grants().isEmpty() && input.reviewer())
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请选择一种账号身份");
        UserProfile before = accounts.profile(target);
        accounts.replaceGrants(targetId, input.grants(), input.reviewer(), actor.id());
        UserProfile after = accounts.profile(target);
        audit.record(actor.id(), "GRANTS_REPLACED", "USER", targetId, before, after);
        return after;
    }
}
