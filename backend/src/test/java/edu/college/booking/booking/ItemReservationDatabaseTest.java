package edu.college.booking.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.college.booking.common.ApiProblem;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CATALOG_DB_TEST", matches = "true")
class ItemReservationDatabaseTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ItemReservationService service;
    long itemId;
    long firstId;
    long secondId;
    String firstLogin;
    String secondLogin;
    Instant start;
    Instant end;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        firstLogin = "booking-a-" + suffix;
        secondLogin = "booking-b-" + suffix;
        firstId = db.queryForObject(
                "INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'测试甲','test') RETURNING id",
                Long.class, firstLogin);
        secondId = db.queryForObject(
                "INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'测试乙','test') RETURNING id",
                Long.class, secondLogin);
        long location = db.queryForObject("SELECT id FROM locations WHERE status='ACTIVE' ORDER BY id LIMIT 1",
                Long.class);
        itemId = db.queryForObject("""
                INSERT INTO items(location_id,name,status,is_valuable,max_advance_days,max_borrow_days)
                VALUES (?,'预约测试物品','ACTIVE',false,14,7) RETURNING id
                """, Long.class, location);
        db.update("INSERT INTO item_units(item_id,unit_code,status) VALUES (?,?,'AVAILABLE')",
                itemId, "BOOK-" + suffix);
        start = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        end = start.plus(1, ChronoUnit.HOURS);
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM audit_logs WHERE target_type='RESERVATION' AND target_id IN "
                + "(SELECT id FROM reservations WHERE item_id=?)", itemId);
        db.update("DELETE FROM reservations WHERE item_id=?", itemId);
        db.update("DELETE FROM item_units WHERE item_id=?", itemId);
        db.update("DELETE FROM items WHERE id=?", itemId);
        db.update("DELETE FROM users WHERE id IN (?,?)", firstId, secondId);
    }

    String body(Instant from, Instant to) {
        return json.writeValueAsString(Map.of("resourceType", "ITEM", "resourceId", itemId,
                "startAt", from.toString(), "endAt", to.toString(),
                "purpose", "学院活动", "quantity", 1));
    }

    @Test
    void loginRequiredIdempotentAndValuableNeedsApproval() throws Exception {
        String payload = body(start, end);
        mvc.perform(post("/api/v1/reservations").with(csrf()).header("Idempotency-Key", "booking-key-1")
                .contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reservations").with(user(firstLogin)).header("Idempotency-Key", "booking-key-1")
                .contentType("application/json").content(payload)).andExpect(status().isForbidden());
        String created = mvc.perform(post("/api/v1/reservations").with(user(firstLogin)).with(csrf())
                .header("Idempotency-Key", "booking-key-1").contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.submissionSeq").isNumber())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        mvc.perform(post("/api/v1/reservations").with(user(firstLogin)).with(csrf())
                .header("Idempotency-Key", "booking-key-1").contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/api/v1/reservations").with(user(firstLogin)).with(csrf())
                .header("Idempotency-Key", "booking-key-1").contentType("application/json")
                .content(body(start.plusSeconds(60), end.plusSeconds(60))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(post("/api/v1/reservations").with(user(secondLogin)).with(csrf())
                .header("Idempotency-Key", "booking-key-2").contentType("application/json").content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
        mvc.perform(get("/api/v1/me/reservations").with(user(firstLogin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/v1/me/reservations").with(user(secondLogin)))
                .andExpect(jsonPath("$.totalElements").value(0));
        db.update("UPDATE items SET is_valuable=true WHERE id=?", itemId);
        mvc.perform(post("/api/v1/reservations").with(user(secondLogin)).with(csrf())
                .header("Idempotency-Key", "booking-key-3").contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void simultaneousRequestsForLastUnitYieldOneConfirmation() throws Exception {
        var request = new ItemReservationService.CreateRequest("ITEM", itemId,
                start.toString(), end.toString(), "活动摄影", 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(firstLogin, secondLogin).stream().map(login -> pool.submit(() -> {
                ready.countDown();
                go.await();
                Authentication actor = new UsernamePasswordAuthenticationToken(login, "test", List.of());
                try {
                    return "CONFIRMED".equals(service.create(actor, request, UUID.randomUUID().toString()).status());
                } catch (ApiProblem problem) {
                    assertEquals(HttpStatus.CONFLICT, problem.status());
                    assertEquals("RESOURCE_CONFLICT", problem.code());
                    return false;
                }
            })).toList();
            ready.await();
            go.countDown();
            int confirmed = 0;
            for (var task : tasks) if (task.get()) confirmed++;
            assertEquals(1, confirmed);
            assertEquals(1L, db.queryForObject(
                    "SELECT count(*) FROM reservations WHERE item_id=? AND status='CONFIRMED'",
                    Long.class, itemId));
        }
    }
}
