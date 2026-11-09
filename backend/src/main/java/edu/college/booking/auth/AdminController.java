package edu.college.booking.auth;

import edu.college.booking.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AdminController {
    private final AdminService admins;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public AdminController(AdminService admins, AuthorizationService authorization, AuditService audit) {
        this.admins = admins;
        this.authorization = authorization;
        this.audit = audit;
    }

    @PostMapping("/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody AdminService.PasswordChange input, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        admins.changePassword(authentication, input);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/users")
    public List<UserProfile> users(Authentication authentication) {
        return admins.list(authentication);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfile create(@RequestBody AdminService.NewUser input, Authentication authentication) {
        return admins.create(authentication, input);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable long id, Authentication authentication) {
        admins.disable(authentication, id);
    }

    @PutMapping("/users/{id}/grants")
    public UserProfile grants(@PathVariable long id, @RequestBody AdminService.GrantUpdate input,
            Authentication authentication) {
        return admins.replaceGrants(authentication, id, input);
    }

    @GetMapping("/audit-logs")
    public List<Map<String, Object>> auditLogs(@RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        authorization.requireSystemAdmin(authentication);
        return audit.recent(Math.max(1, Math.min(size, 100)));
    }
}
