package edu.college.booking.booking;

import static org.junit.jupiter.api.Assertions.*;

import edu.college.booking.common.ApiProblem;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CATALOG_DB_TEST", matches = "true")
class SpaceDatabaseTest {
    @Autowired JdbcTemplate db;
    @Autowired SpaceService spaces;
    @Autowired ItemReservationService reservations;
    long spaceId, studentId, counselorId, reviewerId;
    String studentLogin, counselorLogin, reviewerLogin;
    LocalDate tomorrow;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        studentLogin = "space-student-" + suffix;
        counselorLogin = "space-counselor-" + suffix;
        reviewerLogin = "space-reviewer-" + suffix;
        studentId = account(studentLogin);
        counselorId = account(counselorLogin);
        reviewerId = account(reviewerLogin);
        db.update("INSERT INTO role_grants(user_id,role) VALUES (?,'COUNSELOR_ADMIN')", counselorId);
        db.update("INSERT INTO role_grants(user_id,role) VALUES (?,'SYSTEM_ADMIN')", reviewerId);
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)", reviewerId);
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)", counselorId);
        spaceId = spaces.create(auth(reviewerLogin), new SpaceService.SpaceInput("测试活动室", "有投影仪", 24)).id();
        tomorrow = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
    }

    private long account(String login) {
        return db.queryForObject("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,'地点测试','test') RETURNING id",
                Long.class, login);
    }
    private Authentication auth(String login) {
        return new UsernamePasswordAuthenticationToken(login, "", List.of());
    }
    private ItemReservationService.CreateRequest request(LocalDate day, int start, int end) {
        return new ItemReservationService.CreateRequest("SPACE", spaceId,
                day.atTime(start, 0).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString(),
                day.atTime(end, 0).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString(), "学院例会", 1);
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM audit_logs WHERE (target_type='SPACE' AND target_id=?) OR (target_type='RESERVATION' AND target_id IN (SELECT id FROM reservations WHERE space_id=?))", spaceId, spaceId);
        db.update("DELETE FROM reservations WHERE space_id=?", spaceId);
        db.update("DELETE FROM bookable_space_images WHERE space_id=?", spaceId);
        db.update("DELETE FROM bookable_spaces WHERE id=?", spaceId);
        db.update("DELETE FROM reviewer_memberships WHERE user_id IN (?, ?, ?)", studentId, counselorId, reviewerId);
        db.update("DELETE FROM role_grants WHERE user_id IN (?, ?, ?)", studentId, counselorId, reviewerId);
        db.update("DELETE FROM users WHERE id IN (?, ?, ?)", studentId, counselorId, reviewerId);
    }

    @Test
    void enforcesHoursWeekAndLetsReviewerChooseBetweenCounselorAndStudent() {
        assertThrows(ApiProblem.class, () -> reservations.create(auth(studentLogin), request(tomorrow, 7, 8), "space-invalid-7"));
        assertThrows(ApiProblem.class, () -> reservations.create(auth(studentLogin), request(tomorrow, 21, 23), "space-invalid-23"));
        assertThrows(ApiProblem.class, () -> reservations.create(auth(studentLogin), request(tomorrow.plusDays(7), 8, 9), "space-invalid-week"));
        assertEquals("PENDING_APPROVAL", reservations.create(auth(studentLogin),
                request(tomorrow, 8, 9), "space-opening-hour").status());
        ItemReservation student = reservations.create(auth(studentLogin), request(tomorrow, 21, 22), "space-student-1");
        ItemReservation counselor = reservations.create(auth(counselorLogin), request(tomorrow, 21, 22), "space-counselor-1");
        assertEquals("PENDING_APPROVAL", student.status());
        assertEquals("PENDING_APPROVAL", counselor.status());

        // 申请按提交顺序展示，审核人可以选择其中一条
        var queue = reservations.pending(auth(reviewerLogin), 0, 50).content();
        var counselorEntry = queue.stream().filter(entry -> entry.id() == counselor.id()).findFirst().orElseThrow();
        var studentEntry = queue.stream().filter(entry -> entry.id() == student.id()).findFirst().orElseThrow();
        assertEquals("STANDARD", counselorEntry.priorityTier());
        assertEquals("STANDARD", studentEntry.priorityTier());
        assertEquals(1, studentEntry.competingPending());

        assertThrows(ApiProblem.class, () -> reservations.review(auth(counselorLogin), counselor.id(),
                new ItemReservationService.ReviewRequest("APPROVE", "")));
        // 审核人可以选择其中一条申请
        assertEquals("CONFIRMED", reservations.review(auth(reviewerLogin), student.id(),
                new ItemReservationService.ReviewRequest("APPROVE", "")).status());
        assertEquals("CONFIRMED", spaces.slots(spaceId, tomorrow).stream()
                .filter(slot -> slot.hour() == 21).findFirst().orElseThrow().status());
        ApiProblem occupied = assertThrows(ApiProblem.class, () -> reservations.review(auth(reviewerLogin), counselor.id(),
                new ItemReservationService.ReviewRequest("APPROVE", "")));
        assertEquals("RESOURCE_CONFLICT", occupied.code());
        assertThrows(ApiProblem.class, () -> reservations.create(auth(studentLogin), request(tomorrow, 21, 22), "space-student-2"));
    }

    @Test
    void editAndRemoveRequirePermissionAndPreserveReservationHistory() {
        assertThrows(ApiProblem.class, () -> spaces.update(auth(studentLogin), spaceId,
                new SpaceService.SpaceInput("无权限", "", 10)));
        var updated = spaces.update(auth(reviewerLogin), spaceId,
                new SpaceService.SpaceInput("更新后的地点", "新介绍", 36));
        assertEquals("更新后的地点", updated.name());
        assertEquals(36, updated.capacity());
        var reservation = reservations.create(auth(studentLogin), request(tomorrow, 9, 10), "space-removal-test");
        ApiProblem busy = assertThrows(ApiProblem.class, () -> spaces.remove(auth(reviewerLogin), spaceId));
        assertEquals("SPACE_IN_USE", busy.code());
        db.update("UPDATE reservations SET status='CANCELLED' WHERE id=?", reservation.id());
        spaces.remove(auth(reviewerLogin), spaceId);
        assertTrue(spaces.list().stream().noneMatch(space -> space.id() == spaceId));
        assertEquals("INACTIVE", spaces.required(spaceId, false).status());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM reservations WHERE id=? AND space_id=?", Integer.class, reservation.id(), spaceId));
    }

    @Test
    void managerCanUploadAndReadPhoto() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        var file = new MockMultipartFile("file", "space.png", "image/png", output.toByteArray());
        assertEquals("/api/v1/spaces/" + spaceId + "/image",
                spaces.uploadImage(auth(reviewerLogin), spaceId, file).imageUrl());
        assertEquals("image/png", spaces.image(spaceId).contentType());
        assertThrows(ApiProblem.class, () -> spaces.create(auth(studentLogin),
                new SpaceService.SpaceInput("不允许", "", 10)));
    }
}
