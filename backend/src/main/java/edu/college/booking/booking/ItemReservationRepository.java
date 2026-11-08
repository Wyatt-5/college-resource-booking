package edu.college.booking.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ItemReservationRepository {
    private static final String SELECT = """
            SELECT r.*, COALESCE(i.name, s.name) AS item_name FROM reservations r
            LEFT JOIN items i ON i.id = r.item_id
            LEFT JOIN bookable_spaces s ON s.id = r.space_id
            """;
    private final JdbcTemplate jdbc;

    public ItemReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredReservation> byRequest(long applicantId, String key) {
        return jdbc.query(SELECT + " WHERE r.applicant_id=? AND r.idempotency_key=?",
                (rs, row) -> new StoredReservation(map(rs), rs.getString("payload_hash")),
                applicantId, key).stream().findFirst();
    }

    public int availableUnits(long itemId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM item_units WHERE item_id=? AND status='AVAILABLE'",
                Integer.class, itemId);
    }

    public long confirmedQuantity(long itemId, Instant start, Instant end) {
        return peak(occupancy(itemId, start, end), start, end, "CONFIRMED");
    }

    public record Occupancy(Instant start, Instant end, int quantity, String status) {}

    public List<Occupancy> occupancy(long itemId, Instant start, Instant end) {
        return jdbc.query("""
                SELECT start_at,end_at,quantity,status FROM reservations WHERE item_id=?
                  AND status IN ('CONFIRMED','PENDING_APPROVAL')
                  AND (handover_status IS NULL OR handover_status<>'COMPLETED')
                  AND start_at<? AND end_at>?
                """, (rs, row) -> new Occupancy(rs.getTimestamp(1).toInstant(),
                rs.getTimestamp(2).toInstant(), rs.getInt(3), rs.getString(4)),
                itemId, Timestamp.from(end), Timestamp.from(start));
    }

    // Peak simultaneous use, not the sum of separate bookings across a multi-day interval.
    public static int peak(List<Occupancy> spans, Instant start, Instant end, String status) {
        var events = new java.util.TreeMap<Instant, Integer>();
        for (var span : spans) {
            if (!status.equals(span.status()) || !span.start().isBefore(end) || !span.end().isAfter(start)) continue;
            events.merge(span.start().isAfter(start) ? span.start() : start, span.quantity(), Integer::sum);
            events.merge(span.end().isBefore(end) ? span.end() : end, -span.quantity(), Integer::sum);
        }
        int current = 0, maximum = 0;
        for (int delta : events.values()) { current += delta; maximum = Math.max(maximum, current); }
        return maximum;
    }

    public ItemReservation create(long applicantId, long itemId, Instant start, Instant end,
            String purpose, int quantity, String status, String key, String hash) {
        long id = jdbc.queryForObject("""
                INSERT INTO reservations
                    (applicant_id, resource_type, item_id, start_at, end_at, purpose,
                     quantity, status, handover_status, idempotency_key, payload_hash)
                VALUES (?, 'ITEM', ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, applicantId, itemId, Timestamp.from(start), Timestamp.from(end),
                purpose, quantity, status, "CONFIRMED".equals(status) ? "NOT_PICKED_UP" : null, key, hash);
        return jdbc.query(SELECT + " WHERE r.id=?", (rs, row) -> map(rs), id).getFirst();
    }

    public ItemReservation createSpace(long applicantId, long spaceId, Instant start, Instant end,
            String purpose, String key, String hash) {
        long id = jdbc.queryForObject("""
                INSERT INTO reservations
                    (applicant_id, resource_type, space_id, start_at, end_at, purpose,
                     quantity, status, idempotency_key, payload_hash)
                VALUES (?, 'SPACE', ?, ?, ?, ?, 1, 'PENDING_APPROVAL', ?, ?) RETURNING id
                """, Long.class, applicantId, spaceId, Timestamp.from(start), Timestamp.from(end),
                purpose, key, hash);
        return byId(id).orElseThrow();
    }

    public boolean confirmedSpaceOverlap(long spaceId, Instant start, Instant end) {
        return jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM reservations WHERE space_id=? AND status='CONFIRMED'
                    AND start_at<? AND end_at>?)
                """, Boolean.class, spaceId, Timestamp.from(end), Timestamp.from(start));
    }

    public boolean higherPrioritySpacePending(ItemReservation request, boolean counselor) {
        return jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM reservations r
                    WHERE r.space_id=? AND r.status='PENDING_APPROVAL' AND r.id<>?
                      AND r.start_at<? AND r.end_at>?
                      AND ((EXISTS(SELECT 1 FROM role_grants g WHERE g.user_id=r.applicant_id
                            AND g.role='COUNSELOR_ADMIN') AND (NOT ? OR r.submission_seq<?))
                           OR (NOT EXISTS(SELECT 1 FROM role_grants g WHERE g.user_id=r.applicant_id
                                AND g.role='COUNSELOR_ADMIN') AND NOT ? AND r.submission_seq<?)))
                """, Boolean.class, request.resourceId(), request.id(), Timestamp.from(request.endAt()),
                Timestamp.from(request.startAt()), counselor, request.submissionSeq(), counselor,
                request.submissionSeq());
    }

    public List<ItemReservation> mine(long applicantId, int size, long offset) {
        return jdbc.query(SELECT + " WHERE r.applicant_id=? ORDER BY r.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> map(rs), applicantId, size, offset);
    }

    public long countMine(long applicantId) {
        return jdbc.queryForObject("SELECT count(*) FROM reservations WHERE applicant_id=?",
                Long.class, applicantId);
    }
    public record BookingLog(long id, String resourceType, long resourceId, String resourceName,
            long applicantId, String applicantName, String loginName, Instant startAt, Instant endAt,
            Instant submittedAt, String purpose, int quantity, String status) {}

    public List<BookingLog> log(Long itemId, int size, long offset) {
        return jdbc.query("""
                SELECT r.*,u.display_name,u.login_name,COALESCE(i.name,s.name) AS resource_name
                FROM reservations r JOIN users u ON u.id=r.applicant_id
                LEFT JOIN items i ON i.id=r.item_id LEFT JOIN bookable_spaces s ON s.id=r.space_id
                WHERE (CAST(? AS BIGINT) IS NULL OR r.item_id=?) ORDER BY r.submitted_at DESC,r.id DESC LIMIT ? OFFSET ?
                """, (rs,row)->new BookingLog(rs.getLong("id"),rs.getString("resource_type"),
                "ITEM".equals(rs.getString("resource_type"))?rs.getLong("item_id"):rs.getLong("space_id"),
                rs.getString("resource_name"),rs.getLong("applicant_id"),rs.getString("display_name"),
                rs.getString("login_name"),rs.getTimestamp("start_at").toInstant(),
                rs.getTimestamp("end_at").toInstant(),rs.getTimestamp("submitted_at").toInstant(),
                rs.getString("purpose"),rs.getInt("quantity"),rs.getString("status")),itemId,itemId,size,offset);
    }
    public long countLog(Long itemId) {
        return jdbc.queryForObject("SELECT count(*) FROM reservations WHERE (CAST(? AS BIGINT) IS NULL OR item_id=?)",
                Long.class,itemId,itemId);
    }
    public ItemReservation cancel(long id) {
        int changed=jdbc.update("""
                UPDATE reservations SET status='CANCELLED',handover_status=NULL
                WHERE id=? AND status IN ('PENDING_APPROVAL','CONFIRMED')
                  AND (handover_status IS NULL OR handover_status='NOT_PICKED_UP')
                  AND start_at>CURRENT_TIMESTAMP
                """,id);
        if(changed!=1)throw new edu.college.booking.common.ApiProblem(org.springframework.http.HttpStatus.CONFLICT,
                "INVALID_STATE","仅能取消尚未开始的待审核或已确认预约");
        return byId(id).orElseThrow();
    }
    public Optional<ItemReservation> byId(long id) {
        return jdbc.query(SELECT + " WHERE r.id=?", (rs, row) -> map(rs), id).stream().findFirst();
    }
    public List<ItemReservation> pending(int size,long offset) {
        return jdbc.query(SELECT + " WHERE r.status='PENDING_APPROVAL' ORDER BY r.submission_seq LIMIT ? OFFSET ?",
                (rs,row)->map(rs),size,offset);
    }
    public long countPending() {
        return jdbc.queryForObject("SELECT count(*) FROM reservations WHERE status='PENDING_APPROVAL'",Long.class);
    }
    public boolean earlierPendingOverlaps(ItemReservation request) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM reservations WHERE item_id=? AND status='PENDING_APPROVAL' AND submission_seq<? AND start_at<? AND end_at>?)",
                Boolean.class,request.resourceId(),request.submissionSeq(),Timestamp.from(request.endAt()),Timestamp.from(request.startAt()));
    }
    public ItemReservation decide(long id,String state) {
        int updated=jdbc.update("""
                UPDATE reservations
                SET status=?, handover_status=CASE
                    WHEN resource_type='ITEM' AND ?='CONFIRMED' THEN 'NOT_PICKED_UP'
                    ELSE NULL END
                WHERE id=? AND status='PENDING_APPROVAL'
                """,state,state,id);
        if(updated!=1)throw new IllegalStateException("预约状态已变化");
        return byId(id).orElseThrow();
    }

    private ItemReservation map(ResultSet rs) throws SQLException {
        return new ItemReservation(rs.getLong("id"), rs.getString("resource_type"),
                rs.getLong("ITEM".equals(rs.getString("resource_type")) ? "item_id" : "space_id"),
                rs.getLong("applicant_id"), rs.getString("item_name"),
                rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
                rs.getString("purpose"), rs.getInt("quantity"), rs.getString("status"),
                rs.getString("handover_status"), rs.getString("last_return_reason"),
                rs.getLong("submission_seq"), rs.getTimestamp("submitted_at").toInstant());
    }

    public record StoredReservation(ItemReservation reservation, String payloadHash) {}
}
