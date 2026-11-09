package edu.college.booking.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final AccountRepository accounts;

    public AccountUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String loginName) throws UsernameNotFoundException {
        Account account = accounts.findByLogin(loginName)
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_USER");
        accounts.grants(account.id()).forEach(grant -> authorities.add("ROLE_" + grant.role()));
        if (accounts.isReviewer(account.id())) authorities.add("ROLE_REVIEWER");
        var granted = authorities.stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();
        return new SessionAccount(account.loginName(), account.passwordHash(), account.active(), granted);
    }
}
