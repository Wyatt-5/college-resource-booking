package edu.college.booking.catalog;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ServletCatalogAccess implements CatalogAccess {
    private final HttpServletRequest request;
    private final CatalogScopeProvider scopes;

    public ServletCatalogAccess(HttpServletRequest request, CatalogScopeProvider scopes) {
        this.request = request;
        this.scopes = scopes;
    }

    @Override
    public Actor current() {
        Principal principal = request.getUserPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()
                || principal.getName().length() > 200) {
            throw new CatalogException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        }
        String loginName = principal.getName();
        boolean all = scopes.managesAllLocations(loginName);
        Set<Long> allowed = scopes.manageableLocations(loginName);
        return new Actor(loginName, all, allowed);
    }
}
