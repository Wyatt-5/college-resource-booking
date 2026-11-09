package edu.college.booking.auth;

import static org.junit.jupiter.api.Assertions.*;
import edu.college.booking.common.ApiProblem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="CATALOG_DB_TEST",matches="true")
class ThreeRolesDatabaseTest {
    @Autowired JdbcTemplate db;
    @Autowired AuthorizationService authorization;
    @Autowired AdminService admins;
    @Autowired DepartmentController departments;
    @Autowired AccountRepository accounts;
    @Autowired AccountUserDetailsService loginDetails;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Test void reviewerHasManagementButCannotManageAccountsAndDisabledCannotAccess() {
        String suffix=UUID.randomUUID().toString().substring(0,10);
        String admin="roles-admin-"+suffix, reviewer="roles-review-"+suffix, normal="roles-user-"+suffix;
        long adminId=user(admin), reviewerId=user(reviewer), normalId=user(normal);
        db.update("INSERT INTO role_grants(user_id,role) VALUES (?,'SYSTEM_ADMIN')",adminId);
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)",reviewerId);
        var adminAuth=new UsernamePasswordAuthenticationToken(admin,"",List.of());
        var reviewAuth=new UsernamePasswordAuthenticationToken(reviewer,"",List.of());
        var normalAuth=new UsernamePasswordAuthenticationToken(normal,"",List.of());
        assertEquals(adminId,authorization.requireReviewer(adminAuth).id());
        assertEquals(reviewerId,authorization.requireSpaceManager(reviewAuth).id());
        assertTrue(authorization.canManageLocation(reviewAuth, 1L));
        assertThrows(ApiProblem.class,()->admins.list(reviewAuth));
        assertThrows(ApiProblem.class,()->authorization.requireSpaceManager(normalAuth));
        assertThrows(ApiProblem.class,()->admins.disable(adminAuth,adminId));
        long locationId=db.queryForObject("SELECT id FROM locations WHERE status='ACTIVE' ORDER BY id LIMIT 1",Long.class);
        long itemId=db.queryForObject("INSERT INTO items(location_id,name,status) VALUES (?,'停用测试物品','ACTIVE') RETURNING id",Long.class,locationId);
        long reservationId=db.queryForObject("""
                INSERT INTO reservations(applicant_id,resource_type,item_id,start_at,end_at,purpose,quantity,status,idempotency_key,payload_hash)
                VALUES (?,'ITEM',?,CURRENT_TIMESTAMP + INTERVAL '1 hour',CURRENT_TIMESTAMP + INTERVAL '2 hours','测试',1,'CONFIRMED',?,'hash') RETURNING id
                """,Long.class,normalId,itemId,UUID.randomUUID().toString());
        assertEquals("USER_HAS_BOOKINGS",assertThrows(ApiProblem.class,()->admins.disable(adminAuth,normalId)).code());
        db.update("UPDATE reservations SET status='CANCELLED' WHERE id=?",reservationId);
        admins.disable(adminAuth,normalId);
        assertEquals("DISABLED",db.queryForObject("SELECT status FROM users WHERE id=?",String.class,normalId));
        assertThrows(ApiProblem.class,()->authorization.requireActive(normalAuth));
    }
    @Test void departmentCanBeAddedOnlyByAdminAndAssignedOptionally() {
        String suffix=UUID.randomUUID().toString().substring(0,10);
        String admin="dept-admin-"+suffix, regular="dept-user-"+suffix;
        long adminId=user(admin); user(regular);
        db.update("INSERT INTO role_grants(user_id,role) VALUES (?,'SYSTEM_ADMIN')",adminId);
        var adminAuth=new UsernamePasswordAuthenticationToken(admin,"",List.of());
        var userAuth=new UsernamePasswordAuthenticationToken(regular,"",List.of());
        assertTrue(departments.list(userAuth).stream().anyMatch(d -> d.name().equals("青协")));
        assertThrows(ApiProblem.class,()->departments.create(userAuth,new DepartmentController.Input("无权添加")));
        String name="测试部门-"+suffix;
        departments.create(adminAuth,new DepartmentController.Input(name));
        var created=admins.create(adminAuth,new AdminService.NewUser("new-"+suffix,"测试同学","password12345",name));
        assertEquals(name,created.department());
        var noDepartment=admins.create(adminAuth,new AdminService.NewUser("empty-"+suffix,"无部门","password12345",null));
        assertEquals("",noDepartment.department());
        assertThrows(ApiProblem.class,()->admins.create(adminAuth,new AdminService.NewUser("invalid-"+suffix,"无效部门","password12345","不存在的部门")));
    }
    @Test void disabledAccountCanBeRestoredOnlyForSamePersonWithoutOldPrivilegesOrSessions() {
        String suffix=UUID.randomUUID().toString().substring(0,10);
        String admin="restore-admin-"+suffix, login="restore-user-"+suffix;
        long adminId=user(admin);
        db.update("INSERT INTO role_grants(user_id,role) VALUES (?,'SYSTEM_ADMIN')",adminId);
        var adminAuth=new UsernamePasswordAuthenticationToken(admin,"",List.of());
        var created=admins.create(adminAuth,new AdminService.NewUser(login,"同学甲","password12345"));
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)",created.id());
        var oldPrincipal=loginDetails.loadUserByUsername(login);
        var oldSession=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(oldPrincipal,null,oldPrincipal.getAuthorities());
        assertEquals(created.id(),authorization.requireActive(oldSession).id());
        admins.disable(adminAuth,created.id());
        assertThrows(ApiProblem.class,()->admins.create(adminAuth,new AdminService.NewUser(login,"同学乙","password12345")));
        var restored=admins.create(adminAuth,new AdminService.NewUser(login,"同学甲","newpassword12345"));
        assertEquals(created.id(),restored.id());
        assertEquals("ACTIVE",restored.status());
        assertFalse(restored.reviewer());
        assertTrue(restored.grants().isEmpty());
        assertTrue(passwords.matches("newpassword12345",accounts.findById(created.id()).orElseThrow().passwordHash()));
        assertThrows(ApiProblem.class,()->authorization.requireActive(oldSession));
        var newPrincipal=loginDetails.loadUserByUsername(login);
        var newSession=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(newPrincipal,null,newPrincipal.getAuthorities());
        assertEquals(created.id(),authorization.requireActive(newSession).id());
    }
    private long user(String login){return db.queryForObject("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'测试账号','hash') RETURNING id",Long.class,login);}
}
