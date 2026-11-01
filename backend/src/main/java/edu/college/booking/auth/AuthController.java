package edu.college.booking.auth;

import edu.college.booking.common.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record LoginRequest(String loginName, String password) {}
    public record CsrfResponse(String token, String headerName) {}

    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final AuthorizationService authorization;

    public AuthController(AuthenticationManager manager, SecurityContextRepository contexts,
            AuthorizationService authorization) {
        this.manager = manager;
        this.contexts = contexts;
        this.authorization = authorization;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/login")
    public UserProfile login(@RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        if (body == null || body.loginName() == null || body.loginName().isBlank()
                || body.password() == null || body.password().isBlank()) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "账号和密码不能为空");
        }
        try {
            Authentication authentication = manager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.loginName().toLowerCase(Locale.ROOT), body.password()));
            if (request.getSession(false) != null) request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            return authorization.currentProfile(authentication);
        } catch (BadCredentialsException | DisabledException exception) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
        }
    }

    @GetMapping("/me")
    public UserProfile me(Authentication authentication) {
        return authorization.currentProfile(authentication);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
