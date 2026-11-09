package edu.college.booking.catalog;

import edu.college.booking.auth.Account;
import edu.college.booking.auth.AccountRepository;
import edu.college.booking.auth.Grant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DbCatalogScopeProvider implements CatalogScopeProvider {
    private final AccountRepository accounts;

    public DbCatalogScopeProvider(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean managesAllLocations(String loginName) {
        Account account = active(loginName);
        return accounts.isReviewer(account.id()) || accounts.grants(account.id()).stream().anyMatch(grant ->
                "SYSTEM_ADMIN".equals(grant.role()) || "COUNSELOR_ADMIN".equals(grant.role()) || "RESOURCE_MANAGER".equals(grant.role()));
    }

    @Override
    public Set<Long> manageableLocations(String loginName) {
        Account account = active(loginName);
        return accounts.grants(account.id()).stream()
                .filter(grant -> "RESOURCE_MANAGER".equals(grant.role()))
                .map(Grant::locationId)
                .collect(Collectors.toSet());
    }

    private Account active(String loginName) {
        Account account = accounts.findByLogin(loginName)
                .orElseThrow(() -> new CatalogException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"));
        if (!account.active()) {
            throw new CatalogException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已停用");
        }
        return account;
    }
}
