package edu.college.booking.catalog;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ServletCatalogAccess implements CatalogAccess {
    private final HttpServletRequest request;
    private final ObjectProvider<CatalogScopeProvider> scopes;
    public ServletCatalogAccess(HttpServletRequest request, ObjectProvider<CatalogScopeProvider> scopes) {
        this.request = request; this.scopes = scopes;
    }
    public Actor current() {
        Principal principal = request.getUserPrincipal();
        if (principal == null) throw new CatalogException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        if (principal.getName() == null || principal.getName().isBlank() || principal.getName().length() > 200)
            throw new CatalogException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录身份无效");
        boolean all = request.isUserInRole("SYSTEM_ADMIN") || request.isUserInRole("COUNSELOR_ADMIN");
        CatalogScopeProvider provider = scopes.getIfAvailable();
        Set<Long> allowed = provider == null ? Set.of() : provider.manageableLocations(principal.getName());
        if (allowed == null) allowed = Set.of();
        return new Actor(principal.getName(), all, allowed);
    }
}
