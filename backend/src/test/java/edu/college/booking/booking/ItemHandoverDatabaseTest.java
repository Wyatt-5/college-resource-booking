package edu.college.booking.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "return.images.directory=./target/test-return-images")
@EnabledIfEnvironmentVariable(named = "CATALOG_DB_TEST", matches = "true")
class ItemHandoverDatabaseTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    long locationId;
    long itemId;
    long unitId;
    long reservationId;
    long ownerId;
    long managerId;
    String ownerLogin;
    String managerLogin;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        ownerLogin = "handover-owner-" + suffix;
        managerLogin = "handover-manager-" + suffix;
        ownerId = createUser(ownerLogin, "借用人");
        managerId = createUser(managerLogin, "管理员");
        locationId = db.queryForObject("INSERT INTO locations(name,type) VALUES (?,'STORAGE') RETURNING id",
                Long.class, "交接测试-" + suffix);
        db.update("INSERT INTO role_grants(user_id,role) VALUES (?,'SYSTEM_ADMIN')", managerId);
        itemId = db.queryForObject("""
                INSERT INTO items(location_id,name,status,is_valuable,max_advance_days,max_borrow_days)
                VALUES (?,'交接测试相机','ACTIVE',false,14,7) RETURNING id
                """, Long.class, locationId);
        unitId = db.queryForObject("""
                INSERT INTO item_units(item_id,unit_code,status) VALUES (?,?,'AVAILABLE') RETURNING id
                """, Long.class, itemId, "HANDOVER-" + suffix);
        reservationId = db.queryForObject("""
                INSERT INTO reservations
                    (applicant_id,resource_type,item_id,start_at,end_at,purpose,quantity,status,
                     handover_status,idempotency_key,payload_hash)
                VALUES (?,'ITEM',?,?,?,'测试领取归还',1,'CONFIRMED','NOT_PICKED_UP',?,?) RETURNING id
                """, Long.class, ownerId, itemId,
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)),
                "create-" + suffix, "hash-" + suffix);
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM catalog_idempotency WHERE actor IN (?,?)", ownerLogin, managerLogin);
        db.update("DELETE FROM catalog_audit_outbox WHERE actor IN (?,?)", ownerLogin, managerLogin);
        db.update("DELETE FROM audit_logs WHERE target_type='RESERVATION' AND target_id=?", reservationId);
        db.update("DELETE FROM reservation_action_idempotency WHERE reservation_id=?", reservationId);
        db.update("DELETE FROM reservations WHERE id=?", reservationId);
        db.update("DELETE FROM item_units WHERE id=?", unitId);
        db.update("DELETE FROM items WHERE id=?", itemId);
        db.update("DELETE FROM role_grants WHERE user_id=?", managerId);
        db.update("DELETE FROM locations WHERE id=?", locationId);
        db.update("DELETE FROM users WHERE id IN (?,?)", ownerId, managerId);
    }

    @Test
    void pickupRejectResubmitAndAcceptUseRealData() throws Exception {
        String unitCode = db.queryForObject("SELECT unit_code FROM item_units WHERE id=?", String.class, unitId);
        String pickupBody = json.writeValueAsString(java.util.Map.of("unitCodes", java.util.List.of(unitCode)));
        mvc.perform(post("/api/v1/reservations/" + reservationId + "/pickup")
                        .with(user(managerLogin)).with(csrf()).header("Idempotency-Key", "pickup-owner-test")
                        .contentType("application/json").content(pickupBody))
                .andExpect(status().isForbidden());
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post("/api/v1/reservations/" + reservationId + "/pickup")
                            .with(user(ownerLogin)).with(csrf()).header("Idempotency-Key", "pickup-owner-test")
                            .contentType("application/json").content(pickupBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.handoverStatus").value("CHECKED_OUT"))
                    .andExpect(jsonPath("$.unitCodes[0]").value(unitCode));
        }
        assertEquals("CHECKED_OUT", db.queryForObject("SELECT status FROM item_units WHERE id=?", String.class, unitId));

        submitPhoto("return-attempt-1", "第一次归还");
        long firstPhoto = db.queryForObject("""
                SELECT min(p.id) FROM return_photos p
                JOIN return_submissions s ON s.id=p.submission_id
                WHERE s.reservation_id=?
                """, Long.class, reservationId);
        mvc.perform(get("/api/v1/return-photos/" + firstPhoto).with(user(ownerLogin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservations").param("handoverStatus", "RETURN_SUBMITTED")
                        .with(user(managerLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reservationId))
                .andExpect(jsonPath("$[0].unitCodes[0]").value(unitCode))
                .andExpect(jsonPath("$[0].photos[0].url").isString());
        mvc.perform(post("/api/v1/reservations/" + reservationId + "/return-verification")
                        .with(user(managerLogin)).with(csrf()).header("Idempotency-Key", "reject-return-test")
                        .contentType("application/json")
                        .content("{\"decision\":\"REJECT\",\"reason\":\"照片不清楚\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.handoverStatus").value("RETURN_REJECTED"));
        assertEquals("RETURN_SUBMITTED", db.queryForObject("SELECT status FROM item_units WHERE id=?", String.class, unitId));

        submitPhoto("return-attempt-2", "补拍清晰照片");
        mvc.perform(post("/api/v1/reservations/" + reservationId + "/return-verification")
                        .with(user(managerLogin)).with(csrf()).header("Idempotency-Key", "accept-return-test")
                        .contentType("application/json").content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.handoverStatus").value("COMPLETED"));
        assertEquals("AVAILABLE", db.queryForObject("SELECT status FROM item_units WHERE id=?", String.class, unitId));
        assertEquals("COMPLETED", db.queryForObject("SELECT handover_status FROM reservations WHERE id=?", String.class, reservationId));
        assertEquals(2L, db.queryForObject("SELECT count(*) FROM return_submissions WHERE reservation_id=?", Long.class, reservationId));
    }

    private void submitPhoto(String key, String remark) throws Exception {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile photo = new MockMultipartFile("photos", "return.png", "image/png", png);
        mvc.perform(multipart("/api/v1/reservations/" + reservationId + "/return-submission")
                        .file(photo).param("remark", remark).with(user(ownerLogin)).with(csrf())
                        .header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handoverStatus").value("RETURN_SUBMITTED"))
                .andExpect(jsonPath("$.photoCount").value(1));
    }

    private long createUser(String login, String name) {
        return db.queryForObject("""
                INSERT INTO users(login_name,display_name,password_hash) VALUES (?,?, 'test') RETURNING id
                """, Long.class, login, name);
    }
}
