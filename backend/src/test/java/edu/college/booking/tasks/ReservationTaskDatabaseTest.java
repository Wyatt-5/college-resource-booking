package edu.college.booking.tasks;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import edu.college.booking.common.ApiProblem;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="tasks.service-token=task-test-token-at-least-32-characters")
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="CATALOG_DB_TEST", matches="true")
class ReservationTaskDatabaseTest {
    @Autowired JdbcTemplate db;
    @Autowired ReservationTaskService tasks;
    @Autowired MockMvc mvc;
    @Autowired edu.college.booking.auth.AccountUserDetailsService users;
    long owner, other, item, space;
    String ownerLogin, otherLogin;
    Instant now;
    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        ownerLogin = "task-owner-" + suffix; otherLogin = "task-other-" + suffix;
        owner = createUser(ownerLogin); other = createUser(otherLogin);
        long location = db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'STORAGE') RETURNING id", Long.class, suffix);
        item = db.queryForObject("INSERT INTO items(location_id,name,status,is_valuable,max_advance_days,max_borrow_days) VALUES (?,'任务测试物品','ACTIVE',true,14,7) RETURNING id", Long.class, location);
        space = db.queryForObject("INSERT INTO bookable_spaces(name,capacity) VALUES ('任务测试地点',10) RETURNING id", Long.class);
        now = Instant.now();
    }
    long createUser(String name) { return db.queryForObject("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,?,'hash') RETURNING id", Long.class, name, name); }
    long booking(String type, String status, String handover, long startOffset, long endOffset) {
        return db.queryForObject("""
            INSERT INTO reservations(applicant_id,resource_type,item_id,space_id,start_at,end_at,purpose,quantity,
                status,handover_status,idempotency_key,payload_hash) VALUES (?,?,?,?,?,?,'自动任务测试',1,?,?,?,'hash') RETURNING id
            """, Long.class, owner,type,"ITEM".equals(type)?item:null,"SPACE".equals(type)?space:null,
                Timestamp.from(now.plusSeconds(startOffset)), Timestamp.from(now.plusSeconds(endOffset)),status,handover,UUID.randomUUID().toString());
    }
    ReservationTaskService.Task claim(long id) {
        return tasks.claim(20).tasks().stream().filter(t -> t.reservationId()==id).findFirst().orElseThrow();
    }
    ReservationTaskService.Completion completion(ReservationTaskService.Task task, String outcome) {
        return new ReservationTaskService.Completion(task.leaseToken(),outcome,"借用已到期，请提交归还材料；须核验后恢复可借。");
    }
    long count(String table, String column, long id) { return db.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + "=?", Long.class,id); }

    @Test void pendingItemsAndSpacesExpireWithOneRecordedSystemOutcome() {
        long a = booking("ITEM","PENDING_APPROVAL",null,-60,3600);
        long b = booking("SPACE","PENDING_APPROVAL",null,-60,3600);
        var batch = tasks.claim(20);
        for (long id : List.of(a,b)) {
            var task = batch.tasks().stream().filter(t -> t.reservationId()==id).findFirst().orElseThrow();
            assertEquals("SUCCEEDED",tasks.complete(task.id(),completion(task,"EXPIRE")).state());
            assertEquals("SUCCEEDED",tasks.complete(task.id(),completion(task,"EXPIRE")).state());
            var row = db.queryForMap("SELECT status,decision,decision_reason,reviewed_by,reviewed_at FROM reservations WHERE id=?",id);
            assertEquals("EXPIRED",row.get("status")); assertEquals("EXPIRED",row.get("decision"));
            assertNotNull(row.get("decision_reason")); assertNotNull(row.get("reviewed_at")); assertNull(row.get("reviewed_by"));
            assertEquals(1,count("audit_logs","target_id",id));
        }
        assertTrue(tasks.claim(20).tasks().isEmpty());
    }
    @Test void futureApprovedCancelledAndUncollectedReservationsAreUntouched() {
        long future=booking("ITEM","PENDING_APPROVAL",null,3600,7200);
        long approved=booking("SPACE","CONFIRMED",null,-3600,-60);
        long cancelled=booking("ITEM","CANCELLED",null,-3600,-60);
        long uncollected=booking("ITEM","CONFIRMED","NOT_PICKED_UP",-3600,-60);
        assertTrue(tasks.claim(20).tasks().isEmpty());
        for (long id:List.of(future,approved,cancelled,uncollected)) assertEquals(0,count("background_tasks","reservation_id",id));
    }
    @Test void overdueReminderIsPrivateDeduplicatedAndDoesNotReleasePhysicalUnits() throws Exception {
        long id=booking("ITEM","CONFIRMED","CHECKED_OUT",-3600,-60);
        long unit=db.queryForObject("INSERT INTO item_units(item_id,unit_code,status) VALUES (?,?,'CHECKED_OUT') RETURNING id",Long.class,item,UUID.randomUUID().toString());
        db.update("INSERT INTO reservation_units(reservation_id,unit_id) VALUES (?,?)",id,unit);
        var task=claim(id);
        tasks.complete(task.id(),completion(task,"REMIND")); tasks.complete(task.id(),completion(task,"REMIND"));
        assertEquals(1,count("return_reminders","reservation_id",id));
        assertEquals("CHECKED_OUT",db.queryForObject("SELECT status FROM item_units WHERE id=?",String.class,unit));
        assertEquals("CHECKED_OUT",db.queryForObject("SELECT handover_status FROM reservations WHERE id=?",String.class,id));
        mvc.perform(get("/api/v1/return-reminders").with(user(users.loadUserByUsername(ownerLogin))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].unitCodes").isString());
        mvc.perform(get("/api/v1/return-reminders").with(user(users.loadUserByUsername(otherLogin))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/return-reminders").param("managed","true").with(user(users.loadUserByUsername(otherLogin)))).andExpect(status().isForbidden());
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)",other);
        mvc.perform(get("/api/v1/return-reminders").param("managed","true").with(user(users.loadUserByUsername(otherLogin))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        db.update("UPDATE reservations SET handover_status='RETURN_SUBMITTED' WHERE id=?",id);
        assertEquals(0,tasks.reminders(new UsernamePasswordAuthenticationToken(ownerLogin,"",List.of()),false,0,20).totalElements());
        db.update("UPDATE reservations SET handover_status='RETURN_REJECTED' WHERE id=?",id);
        assertEquals(1,tasks.reminders(new UsernamePasswordAuthenticationToken(ownerLogin,"",List.of()),false,0,20).totalElements());
        assertTrue(tasks.claim(20).tasks().isEmpty());
        assertEquals(1,count("return_reminders","reservation_id",id));
    }
    @Test void staleWorkerResultsCannotOverrideCancellationOrReturnSubmission() {
        long pending=booking("SPACE","PENDING_APPROVAL",null,-60,3600);
        var first=claim(pending);
        db.update("UPDATE reservations SET status='CANCELLED' WHERE id=?",pending);
        assertEquals("SKIPPED",tasks.complete(first.id(),completion(first,"EXPIRE")).state());
        long borrowed=booking("ITEM","CONFIRMED","CHECKED_OUT",-3600,-60);
        var second=claim(borrowed);
        db.update("UPDATE reservations SET handover_status='RETURN_SUBMITTED' WHERE id=?",borrowed);
        assertEquals("SKIPPED",tasks.complete(second.id(),completion(second,"REMIND")).state());
        assertEquals(0,count("return_reminders","reservation_id",borrowed));
        db.update("UPDATE reservations SET handover_status='RETURN_REJECTED' WHERE id=?",borrowed);
        var retry=claim(borrowed);
        assertEquals("SUCCEEDED",tasks.complete(retry.id(),completion(retry,"REMIND")).state());
        assertEquals(1,count("return_reminders","reservation_id",borrowed));
    }
    @Test void liveLeasesAreExclusiveAndExpiredTokensCannotCompleteReclaimedWork() {
        long id=booking("SPACE","PENDING_APPROVAL",null,-60,3600);
        var first=claim(id);
        assertTrue(tasks.claim(20).tasks().isEmpty());
        db.update("UPDATE background_tasks SET lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?",first.id());
        assertThrows(ApiProblem.class,()->tasks.complete(first.id(),completion(first,"EXPIRE")));
        var second=claim(id);
        assertNotEquals(first.leaseToken(),second.leaseToken());
        assertThrows(ApiProblem.class,()->tasks.complete(first.id(),completion(first,"EXPIRE")));
        assertEquals("SUCCEEDED",tasks.complete(second.id(),completion(second,"EXPIRE")).state());
    }
    @Test void failuresBackOffAndStopAfterFiveAttemptsWithoutDroppingTheReservation() {
        long id=booking("ITEM","PENDING_APPROVAL",null,-60,3600);
        for (int attempt=1;attempt<=5;attempt++) {
            var task=claim(id);
            var failure=new ReservationTaskService.Failure(task.leaseToken(),"测试网络失败");
            assertEquals(attempt==5?"FAILED":"PENDING",tasks.fail(task.id(),failure).state());
            assertEquals(attempt==5?"FAILED":"PENDING",tasks.fail(task.id(),failure).state());
            assertTrue(tasks.claim(20).tasks().isEmpty());
            db.update("UPDATE background_tasks SET available_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?",task.id());
        }
        assertTrue(tasks.claim(20).tasks().isEmpty());
        assertEquals("PENDING_APPROVAL",db.queryForObject("SELECT status FROM reservations WHERE id=?",String.class,id));
    }
    @Test void workerCannotSkipDueWorkOrChooseAnOutcomeFromAnotherTaskType() {
        var task=claim(booking("ITEM","PENDING_APPROVAL",null,-60,3600));
        assertThrows(ApiProblem.class,()->tasks.complete(task.id(),completion(task,"SKIP")));
        assertThrows(ApiProblem.class,()->tasks.complete(task.id(),completion(task,"REMIND")));
        assertEquals("LEASED",db.queryForObject("SELECT state FROM background_tasks WHERE id=?",String.class,task.id()));
    }
    @Test void internalApiUsesServiceTokenInsteadOfUserSessionsAndUserWritesStillRequireCsrf() throws Exception {
        mvc.perform(post("/api/v1/internal/tasks/claim")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/tasks/claim").with(user(users.loadUserByUsername(ownerLogin))).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/tasks/claim").header("Authorization","Bearer incorrect")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/tasks/claim").header("Authorization","Bearer task-test-token-at-least-32-characters"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.serverTime").exists());
        mvc.perform(post("/api/v1/announcements/read").with(user(users.loadUserByUsername(ownerLogin)))
            .contentType("application/json").content("{\"announcements\":[]}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/return-reminders")).andExpect(status().isUnauthorized());
    }
}
