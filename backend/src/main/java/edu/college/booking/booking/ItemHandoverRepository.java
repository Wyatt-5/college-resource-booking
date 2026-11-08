package edu.college.booking.booking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ItemHandoverRepository {
    private final JdbcTemplate jdbc;

    public ItemHandoverRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LockedReservation(long id, long applicantId, long itemId, String itemName,
            long locationId, int quantity, String status, String handoverStatus,
            Instant startAt, Instant endAt, String lastReturnReason) {}
    public record UnitRow(long id, String code, String status) {}
    public record HandoverSnapshot(long id, String handoverStatus, List<String> unitCodes,
            int photoCount, String lastReturnReason) {}
    public record Submission(long id, long reservationId, int attemptNo, String remark,
            String status, Instant submittedAt) {}

    public Optional<LockedReservation> lock(long reservationId) {
        return jdbc.query("""
                SELECT r.id,r.applicant_id,r.item_id,i.name AS item_name,i.location_id,
                       r.quantity,r.status,r.handover_status,r.start_at,r.end_at,
                       r.last_return_reason
                FROM reservations r JOIN items i ON i.id=r.item_id
                WHERE r.id=? AND r.resource_type='ITEM'
                FOR UPDATE OF r
                """, (rs, row) -> new LockedReservation(rs.getLong("id"),
                rs.getLong("applicant_id"), rs.getLong("item_id"), rs.getString("item_name"),
                rs.getLong("location_id"), rs.getInt("quantity"), rs.getString("status"),
                rs.getString("handover_status"), rs.getTimestamp("start_at").toInstant(),
                rs.getTimestamp("end_at").toInstant(), rs.getString("last_return_reason")),
                reservationId).stream().findFirst();
    }

    public List<UnitRow> lockUnits(long itemId, List<String> codes) {
        if (codes.isEmpty()) return List.of();
        String marks = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(itemId);
        parameters.addAll(codes);
        return jdbc.query("SELECT id,unit_code,status FROM item_units WHERE item_id=? AND unit_code IN ("
                        + marks + ") ORDER BY id FOR UPDATE",
                (rs, row) -> new UnitRow(rs.getLong("id"), rs.getString("unit_code"),
                        rs.getString("status")), parameters.toArray());
    }

    public List<UnitRow> boundUnits(long reservationId) {
        return jdbc.query("""
                SELECT u.id,u.unit_code,u.status
                FROM reservation_units ru JOIN item_units u ON u.id=ru.unit_id
                WHERE ru.reservation_id=? ORDER BY u.id
                """, (rs, row) -> new UnitRow(rs.getLong("id"), rs.getString("unit_code"),
                rs.getString("status")), reservationId);
    }

    public void completePickup(long reservationId, List<UnitRow> units) {
        for (var unit : units) {
            if (jdbc.update("UPDATE item_units SET status='CHECKED_OUT' WHERE id=? AND status='AVAILABLE'",
                    unit.id()) != 1) {
                throw new IllegalStateException("实物状态已变化");
            }
            jdbc.update("INSERT INTO reservation_units(reservation_id,unit_id) VALUES (?,?)",
                    reservationId, unit.id());
        }
        int updated = jdbc.update("""
                UPDATE reservations
                SET handover_status='CHECKED_OUT',picked_up_at=CURRENT_TIMESTAMP,last_return_reason=NULL
                WHERE id=? AND status='CONFIRMED' AND handover_status='NOT_PICKED_UP'
                """, reservationId);
        if (updated != 1) throw new IllegalStateException("预约交接状态已变化");
    }

    public int nextAttempt(long reservationId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(max(attempt_no),0)+1 FROM return_submissions WHERE reservation_id=?
                """, Integer.class, reservationId);
    }

    public long createSubmission(long reservationId, int attempt, String remark) {
        return jdbc.queryForObject("""
                INSERT INTO return_submissions(reservation_id,attempt_no,remark,status)
                VALUES (?,?,?,'RETURN_SUBMITTED') RETURNING id
                """, Long.class, reservationId, attempt, remark);
    }

    public void markReturnSubmitted(long reservationId, String previousStatus, List<UnitRow> units) {
        if ("CHECKED_OUT".equals(previousStatus)) {
            for (var unit : units) {
                if (jdbc.update("""
                        UPDATE item_units SET status='RETURN_SUBMITTED'
                        WHERE id=? AND status='CHECKED_OUT'
                        """, unit.id()) != 1) {
                    throw new IllegalStateException("实物状态已变化");
                }
            }
        } else {
            for (var unit : units) {
                if (!"RETURN_SUBMITTED".equals(unit.status())) {
                    throw new IllegalStateException("驳回后的实物状态不一致");
                }
            }
        }
        int updated = jdbc.update("""
                UPDATE reservations
                SET handover_status='RETURN_SUBMITTED',last_return_reason=NULL
                WHERE id=? AND handover_status=?
                """, reservationId, previousStatus);
        if (updated != 1) throw new IllegalStateException("预约交接状态已变化");
    }

    public Optional<Submission> submission(long id) {
        return jdbc.query("""
                SELECT id,reservation_id,attempt_no,remark,status,submitted_at
                FROM return_submissions WHERE id=?
                """, (rs, row) -> new Submission(rs.getLong("id"), rs.getLong("reservation_id"),
                rs.getInt("attempt_no"), rs.getString("remark"), rs.getString("status"),
                rs.getTimestamp("submitted_at").toInstant()), id).stream().findFirst();
    }

    public HandoverSnapshot snapshot(long reservationId) {
        var row = jdbc.queryForMap("""
                SELECT id,handover_status,last_return_reason FROM reservations WHERE id=?
                """, reservationId);
        List<String> codes = boundUnits(reservationId).stream().map(UnitRow::code).toList();
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM return_photos p
                JOIN return_submissions s ON s.id=p.submission_id
                WHERE s.id=(SELECT id FROM return_submissions WHERE reservation_id=?
                            ORDER BY attempt_no DESC LIMIT 1)
                """, Integer.class, reservationId);
        return new HandoverSnapshot(((Number) row.get("id")).longValue(),
                (String) row.get("handover_status"), codes, count == null ? 0 : count,
                (String) row.get("last_return_reason"));
    }
}
