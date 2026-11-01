package edu.college.booking.auth;

import edu.college.booking.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final String login;
    private final String password;

    public AdminBootstrap(AccountRepository accounts, PasswordEncoder passwords, AuditService audit,
            @Value("${BOOTSTRAP_ADMIN_LOGIN:}") String login,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.audit = audit;
        this.login = login;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.countUsers() != 0) return;
        if (login.isBlank() && password.isBlank()) return;
        if (!login.matches("[a-zA-Z0-9._-]{3,40}") || password.length() < 12) {
            throw new IllegalStateException("首次管理员账号或密码不符合要求");
        }
        long id = accounts.createUser(login.toLowerCase(java.util.Locale.ROOT), "初始系统管理员",
                passwords.encode(password));
        accounts.addGrant(id, new Grant("SYSTEM_ADMIN", null), null);
        audit.record(null, "SYSTEM_BOOTSTRAP", "USER", id, null, accounts.profile(accounts.findById(id).orElseThrow()));
    }
}
