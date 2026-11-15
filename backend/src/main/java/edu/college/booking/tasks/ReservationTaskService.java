package edu.college.booking.tasks;

import edu.college.booking.audit.AuditService;
import edu.college.booking.auth.AuthorizationService;
import edu.college.booking.common.ApiProblem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTaskService {
    public record Task(long id, String type, long reservationId, String leaseToken, Instant leaseUntil,
            String resourceName, String status, String handoverStatus, Instant startAt, Instant endAt) {}
    public record ClaimResult(Instant serverTime, List<Task> tasks) {}
    public record Completion(String leaseToken, String outcome, String message) {}
    public record Failure(String leaseToken, String reason) {}
    public record Result(long id, String state) {}
    public record ReturnReminder(long id, long reservationId, String resourceName, long applicantId,
            String applicantName, Instant endAt, String handoverStatus, String unitCodes,
            String message, Instant createdAt) {}
    public record ReminderPage(List<ReturnReminder> content, int page, int size, long totalElements) {}
    private record LockedTask(long id, String type, long reservationId, String state,
            int attempts, String leaseToken, Instant leaseUntil) {}
    private record Reservation(String status, String resourceType, String handoverStatus,
            Instant startAt, Instant endAt) {}

    private final JdbcTemplate db;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public ReservationTaskService(JdbcTemplate db, AuthorizationService authorization, AuditService audit) {
        this.db = db;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional
    public ClaimResult claim(int limit) {
        if (limit < 1 || limit > 20) throw invalid("每批任务数量须为1至20");
        Instant now = Instant.now();
        Timestamp time = Timestamp.from(now);
        db.update("""
            INSERT INTO background_tasks(type,reservation_id,available_at)
            SELECT 'EXPIRE_PENDING',id,? FROM reservations
            WHERE status='PENDING_APPROVAL' AND start_at<=? ORDER BY id
            ON CONFLICT(type,reservation_id) DO NOTHING
            """, time, time);
        db.update("""
            INSERT INTO background_tasks(type,reservation_id,available_at)
            SELECT 'REMIND_OVERDUE',id,? FROM reservations WHERE resource_type='ITEM'
              AND status='CONFIRMED' AND handover_status IN ('CHECKED_OUT','RETURN_REJECTED')
              AND end_at<=? ORDER BY id
            ON CONFLICT(type,reservation_id) DO UPDATE
              SET state='PENDING',attempts=0,available_at=EXCLUDED.available_at,
                  lease_token=NULL,lease_until=NULL,completed_at=NULL,last_error=NULL
              WHERE background_tasks.state='SKIPPED'
            """, time, time);
        db.update("""
            UPDATE background_tasks SET state=CASE WHEN attempts>=5 THEN 'FAILED' ELSE 'PENDING' END,
                available_at=?, lease_token=NULL, lease_until=NULL, last_error='任务租约超时'
            WHERE state='LEASED' AND lease_until<=?
            """, time, time);
        var ids = db.queryForList("""
            SELECT id FROM background_tasks WHERE state='PENDING' AND available_at<=?
            ORDER BY available_at,id LIMIT ? FOR UPDATE SKIP LOCKED
            """, Long.class, time, limit);
        List<Task> tasks = new ArrayList<>();
        for (long id : ids) {
            String token = UUID.randomUUID().toString();
            Instant until = now.plusSeconds(180);
            db.update("""
                UPDATE background_tasks SET state='LEASED', attempts=attempts+1, lease_token=?, lease_until=?
                WHERE id=?
                """, token, Timestamp.from(until), id);
            tasks.add(db.queryForObject("""
                SELECT t.*,COALESCE(i.name,s.name) AS resource_name,r.status,r.handover_status,r.start_at,r.end_at
                FROM background_tasks t JOIN reservations r ON r.id=t.reservation_id
                LEFT JOIN items i ON i.id=r.item_id LEFT JOIN bookable_spaces s ON s.id=r.space_id WHERE t.id=?
                """, (rs, row) -> new Task(id, rs.getString("type"), rs.getLong("reservation_id"), token,
                    until, rs.getString("resource_name"), rs.getString("status"), rs.getString("handover_status"),
                    instant(rs, "start_at"), instant(rs, "end_at")), id));
        }
        return new ClaimResult(now, tasks);
    }

    @Transactional
    public Result complete(long id, Completion input) {
        if (input == null || input.outcome() == null
                || !List.of("EXPIRE", "REMIND", "SKIP").contains(input.outcome())) throw invalid("任务结果无效");
        var task = locked(id);
        requireToken(task, input.leaseToken());
        if (List.of("SUCCEEDED", "SKIPPED").contains(task.state())) return new Result(id, task.state());
        requireLease(task);
        String expected = "EXPIRE_PENDING".equals(task.type()) ? "EXPIRE" : "REMIND";
        if (!expected.equals(input.outcome()) && !"SKIP".equals(input.outcome())) throw invalid("任务类型与结果不匹配");
        var reservation = db.queryForObject("""
            SELECT status,resource_type,handover_status,start_at,end_at FROM reservations WHERE id=? FOR UPDATE
            """, (rs, row) -> new Reservation(rs.getString("status"), rs.getString("resource_type"),
                rs.getString("handover_status"), instant(rs, "start_at"), instant(rs, "end_at")), task.reservationId());
        Instant now = Instant.now();
        boolean due = "EXPIRE_PENDING".equals(task.type())
                ? "PENDING_APPROVAL".equals(reservation.status()) && !reservation.startAt().isAfter(now)
                : "ITEM".equals(reservation.resourceType()) && "CONFIRMED".equals(reservation.status())
                    && List.of("CHECKED_OUT", "RETURN_REJECTED").contains(String.valueOf(reservation.handoverStatus()))
                    && !reservation.endAt().isAfter(now);
        // A worker cannot skip an eligible task or apply a stale result after a user/admin changed the booking.
        if (due && "SKIP".equals(input.outcome())) throw invalid("符合条件的任务不能跳过");
        String state = due ? "SUCCEEDED" : "SKIPPED";
        if (due && "EXPIRE_PENDING".equals(task.type())) {
            String reason = "预约开始前未完成审核，申请自动过期";
            db.update("""
                UPDATE reservations SET status='EXPIRED',decision='EXPIRED',decision_reason=?,
                    reviewed_by=NULL,reviewed_at=? WHERE id=? AND status='PENDING_APPROVAL'
                """, reason, Timestamp.from(now), task.reservationId());
            audit.record(null, "RESERVATION_EXPIRED", "RESERVATION", task.reservationId(),
                    Map.of("status", "PENDING_APPROVAL"), Map.of("status", "EXPIRED", "taskId", id, "reason", reason));
        } else if (due) {
            String message = input.message() == null ? "" : input.message().trim();
            if (message.isEmpty() || message.codePointCount(0, message.length()) > 500) throw invalid("提醒内容须为1至500字");
            int inserted = db.update("""
                INSERT INTO return_reminders(reservation_id,message) VALUES (?,?)
                ON CONFLICT(reservation_id) DO NOTHING
                """, task.reservationId(), message);
            if (inserted == 1) audit.record(null, "RETURN_OVERDUE_REMINDER", "RESERVATION", task.reservationId(),
                    null, Map.of("taskId", id, "message", message));
        }
        db.update("UPDATE background_tasks SET state=?,completed_at=?,lease_until=NULL WHERE id=?",
                state, Timestamp.from(now), id);
        return new Result(id, state);
    }

    @Transactional
    public Result fail(long id, Failure input) {
        if (input == null) throw invalid("请提供任务租约");
        var task = locked(id);
        requireToken(task, input.leaseToken());
        if (List.of("PENDING", "FAILED").contains(task.state())) return new Result(id, task.state());
        requireLease(task);
        String state = task.attempts() >= 5 ? "FAILED" : "PENDING";
        long delay = Math.min(300, 5L << Math.min(task.attempts(), 6));
        String reason = input.reason() == null ? "任务执行失败" : input.reason();
        if (reason.length() > 500) reason = reason.substring(0, 500);
        db.update("""
            UPDATE background_tasks SET state=?,available_at=?,lease_until=NULL,last_error=? WHERE id=?
            """, state, Timestamp.from(Instant.now().plusSeconds(delay)), reason, id);
        return new Result(id, state);
    }

    private LockedTask locked(long id) {
        return db.query("SELECT * FROM background_tasks WHERE id=? FOR UPDATE", (rs, row) -> new LockedTask(id,
                rs.getString("type"), rs.getLong("reservation_id"), rs.getString("state"), rs.getInt("attempts"),
                rs.getString("lease_token"), instant(rs, "lease_until")), id).stream().findFirst()
                .orElseThrow(() -> new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "任务不存在"));
    }

    private static void requireToken(LockedTask task, String token) {
        if (token == null || !token.equals(task.leaseToken())) throw conflict("任务租约不匹配");
    }

    private static void requireLease(LockedTask task) {
        if (!"LEASED".equals(task.state()) || task.leaseUntil() == null || !task.leaseUntil().isAfter(Instant.now()))
            throw conflict("任务租约已失效，请重新领取");
    }

    @Transactional(readOnly = true)
    public ReminderPage reminders(Authentication authentication, boolean managed, int page, int size) {
        var actor = managed ? authorization.requireSpaceManager(authentication) : authorization.requireActive(authentication);
        if (page < 0 || size < 1 || size > 100) throw invalid("分页参数无效");
        String from = """
            FROM return_reminders n JOIN reservations r ON r.id=n.reservation_id
            JOIN items i ON i.id=r.item_id JOIN users u ON u.id=r.applicant_id
            WHERE r.status='CONFIRMED' AND r.handover_status IN ('CHECKED_OUT','RETURN_REJECTED')
              AND r.end_at<=CURRENT_TIMESTAMP
            """ + (managed ? "" : " AND r.applicant_id=? ");
        Object[] countArgs = managed ? new Object[]{} : new Object[]{actor.id()};
        Object[] args = managed ? new Object[]{size, (long) page * size} : new Object[]{actor.id(), size, (long) page * size};
        long total = db.queryForObject("SELECT count(*) " + from, Long.class, countArgs);
        var rows = db.query("""
            SELECT n.*,i.name AS resource_name,r.applicant_id,u.display_name,r.end_at,r.handover_status,
                (SELECT string_agg(iu.unit_code,'、' ORDER BY iu.id) FROM reservation_units ru
                    JOIN item_units iu ON iu.id=ru.unit_id WHERE ru.reservation_id=r.id) AS unit_codes
            """ + from + " ORDER BY r.end_at,n.id LIMIT ? OFFSET ?", (rs, row) -> new ReturnReminder(rs.getLong("id"),
                rs.getLong("reservation_id"), rs.getString("resource_name"), rs.getLong("applicant_id"),
                rs.getString("display_name"), instant(rs, "end_at"), rs.getString("handover_status"),
                rs.getString("unit_codes"), rs.getString("message"), instant(rs, "created_at")), args);
        return new ReminderPage(rows, page, size, total);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static ApiProblem invalid(String message) { return new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message); }
    private static ApiProblem conflict(String message) { return new ApiProblem(HttpStatus.CONFLICT, "LEASE_EXPIRED", message); }
}
