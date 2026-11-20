package edu.college.booking.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countUsers() {
        return jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
    }

    public long countActiveSystemAdmins() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM role_grants rg
                JOIN users u ON u.id = rg.user_id
                WHERE rg.role = 'SYSTEM_ADMIN' AND u.status = 'ACTIVE'
                """, Long.class);
    }

    public boolean isSystemAdmin(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM role_grants WHERE user_id = ? AND role = 'SYSTEM_ADMIN')",
                Boolean.class, userId));
    }

    public Optional<Account> findByLogin(String loginName) {
        return jdbc.query("SELECT id, login_name, display_name, password_hash, status FROM users WHERE login_name = ?",
                (rs, row) -> mapAccount(rs), loginName).stream().findFirst();
    }

    public Optional<Account> lockByLogin(String loginName) {
        return jdbc.query("SELECT id, login_name, display_name, password_hash, status FROM users WHERE login_name = ? FOR UPDATE",
                (rs, row) -> mapAccount(rs), loginName).stream().findFirst();
    }
    public void reactivate(long id, String passwordHash) {
        jdbc.update("UPDATE users SET status='ACTIVE', password_hash=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='DISABLED'", passwordHash, id);
    }

    public Optional<Account> findById(long id) {
        return jdbc.query("SELECT id, login_name, display_name, password_hash, status FROM users WHERE id = ?",
                (rs, row) -> mapAccount(rs), id).stream().findFirst();
    }

    public List<UserProfile> listUsers() {
        return jdbc.query("SELECT id, login_name, display_name, password_hash, status FROM users ORDER BY id",
                (rs, row) -> profile(mapAccount(rs)));
    }

    public long createUser(String loginName, String displayName, String passwordHash) {
        return jdbc.queryForObject(
                "INSERT INTO users (login_name, display_name, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class, loginName, displayName, passwordHash);
    }

    public boolean departmentExists(String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM departments WHERE name=?)", Boolean.class, name));
    }
    public void setDepartment(long id, String department) {
        jdbc.update("INSERT INTO user_details(user_id,department) VALUES (?,?) ON CONFLICT(user_id) DO UPDATE SET department=EXCLUDED.department",id,department);
    }
    public String department(long id) {
        return jdbc.query("SELECT department FROM user_details WHERE user_id=?", (rs,n)->rs.getString(1),id).stream().findFirst().orElse("");
    }

    public boolean hasUnfinishedBookings(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM reservations WHERE applicant_id=? AND
                    ((status='PENDING_APPROVAL' AND start_at>CURRENT_TIMESTAMP)
                    OR (status='CONFIRMED' AND (end_at>CURRENT_TIMESTAMP
                        OR handover_status IN ('CHECKED_OUT','RETURN_SUBMITTED','RETURN_REJECTED')))))
                """, Boolean.class, id));
    }
    public void disable(long id) {
        jdbc.update("UPDATE users SET status='DISABLED', updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    public int replacePassword(long id, String previousHash, String newHash) {
        return jdbc.update("UPDATE users SET password_hash = ? WHERE id = ? AND password_hash = ?",
                newHash, id, previousHash);
    }

    public UserProfile profile(Account account) {
        return new UserProfile(account.id(), account.loginName(), account.displayName(), account.status(),
                grants(account.id()), isReviewer(account.id()), department(account.id()));
    }

    public List<Grant> grants(long userId) {
        return jdbc.query("SELECT role, location_id FROM role_grants WHERE user_id = ? ORDER BY role, location_id",
                (rs, row) -> new Grant(rs.getString("role"), rs.getObject("location_id", Long.class)), userId);
    }

    public boolean isReviewer(long userId) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM reviewer_memberships WHERE user_id = ?)",
                Boolean.class, userId);
    }

    public boolean locationExists(long locationId) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM locations WHERE id = ? AND status = 'ACTIVE')",
                Boolean.class, locationId);
    }

    public void addGrant(long userId, Grant grant, Long actorId) {
        jdbc.update("INSERT INTO role_grants (user_id, role, location_id, granted_by) VALUES (?, ?, ?, ?)",
                userId, grant.role(), grant.locationId(), actorId);
    }

    public void replaceGrants(long userId, List<Grant> grants, boolean reviewer, long actorId) {
        jdbc.update("DELETE FROM role_grants WHERE user_id = ?", userId);
        for (Grant grant : grants) {
            addGrant(userId, grant, actorId);
        }
        jdbc.update("DELETE FROM reviewer_memberships WHERE user_id = ?", userId);
        if (reviewer) {
            jdbc.update("INSERT INTO reviewer_memberships (user_id, added_by) VALUES (?, ?)", userId, actorId);
        }
    }

    private Account mapAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Account(rs.getLong("id"), rs.getString("login_name"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getString("status"));
    }
}
