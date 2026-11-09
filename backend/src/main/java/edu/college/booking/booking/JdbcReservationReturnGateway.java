package edu.college.booking.booking;

import edu.college.booking.catalog.CatalogAccess;
import edu.college.booking.catalog.ReservationReturnGateway;
import edu.college.booking.common.ApiProblem;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcReservationReturnGateway implements ReservationReturnGateway {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcReservationReturnGateway(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    @Override
    public ReturnRecord lock(long reservationId) {
        return load(reservationId, true);
    }

    @Override
    public List<ReturnRecord> queue(CatalogAccess.Actor actor, int page, int size) {
        if (!actor.allLocations() && actor.locations().isEmpty()) return List.of();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("size", size);
        parameters.put("offset", (long) page * size);
        String scope = "";
        if (!actor.allLocations()) {
            scope = " AND i.location_id IN (:locations)";
            parameters.put("locations", actor.locations());
        }
        List<Long> ids = named.queryForList("""
                SELECT r.id
                FROM reservations r
                JOIN items i ON i.id=r.item_id
                JOIN return_submissions s ON s.id=(
                    SELECT s2.id FROM return_submissions s2
                    WHERE s2.reservation_id=r.id ORDER BY s2.attempt_no DESC LIMIT 1)
                WHERE r.resource_type='ITEM' AND r.handover_status='RETURN_SUBMITTED'
                  AND s.status='RETURN_SUBMITTED'
                """ + scope + " ORDER BY s.submitted_at,r.id LIMIT :size OFFSET :offset",
                parameters, Long.class);
        List<ReturnRecord> result = new ArrayList<>();
        for (long id : ids) {
            ReturnRecord record = load(id, false);
            if (record != null) result.add(record);
        }
        return result;
    }

    @Override
    public void finish(long reservationId, String handoverStatus, String actor, String reason) {
        Long actorId = jdbc.query("SELECT id FROM users WHERE login_name=?",
                (rs, row) -> rs.getLong(1), actor).stream().findFirst().orElse(null);
        if (actorId == null) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号不存在");
        }
        int submission = jdbc.update("""
                UPDATE return_submissions
                SET status=?,reviewed_by=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP
                WHERE id=(SELECT id FROM return_submissions WHERE reservation_id=?
                          ORDER BY attempt_no DESC LIMIT 1)
                  AND status='RETURN_SUBMITTED'
                """, handoverStatus, actorId, reason, reservationId);
        if (submission != 1) throw conflict();
        int reservation = jdbc.update("""
                UPDATE reservations SET handover_status=?,last_return_reason=?
                WHERE id=? AND handover_status='RETURN_SUBMITTED'
                """, handoverStatus, "RETURN_REJECTED".equals(handoverStatus) ? reason : null,
                reservationId);
        if (reservation != 1) throw conflict();
    }

    private ReturnRecord load(long reservationId, boolean lock) {
        String sql = """
                SELECT r.id,r.item_id,i.name AS item_name,i.location_id,u.display_name,
                       s.id AS submission_id,s.submitted_at,s.remark,r.handover_status
                FROM reservations r
                JOIN items i ON i.id=r.item_id
                JOIN users u ON u.id=r.applicant_id
                JOIN return_submissions s ON s.id=(
                    SELECT s2.id FROM return_submissions s2
                    WHERE s2.reservation_id=r.id ORDER BY s2.attempt_no DESC LIMIT 1)
                WHERE r.id=? AND r.resource_type='ITEM'
                """ + (lock ? " FOR UPDATE OF r" : "");
        var rows = jdbc.queryForList(sql, reservationId);
        if (rows.isEmpty()) return null;
        var row = rows.getFirst();
        long submissionId = ((Number) row.get("submission_id")).longValue();
        List<Map<String, Object>> units = jdbc.queryForList("""
                SELECT u.id,u.unit_code FROM reservation_units ru
                JOIN item_units u ON u.id=ru.unit_id
                WHERE ru.reservation_id=? ORDER BY u.id
                """, reservationId);
        List<Long> unitIds = units.stream()
                .map(unit -> ((Number) unit.get("id")).longValue()).toList();
        List<String> unitCodes = units.stream()
                .map(unit -> (String) unit.get("unit_code")).toList();
        List<ReturnPhoto> photos = jdbc.query("""
                SELECT id,file_name FROM return_photos WHERE submission_id=? ORDER BY id
                """, (rs, index) -> new ReturnPhoto(rs.getLong("id"), rs.getString("file_name"),
                "/api/v1/return-photos/" + rs.getLong("id")), submissionId);
        return new ReturnRecord(((Number) row.get("id")).longValue(),
                ((Number) row.get("item_id")).longValue(), (String) row.get("item_name"),
                ((Number) row.get("location_id")).longValue(), unitIds, unitCodes,
                (String) row.get("display_name"), ((Timestamp) row.get("submitted_at")).toInstant(),
                (String) row.get("remark"), photos, (String) row.get("handover_status"));
    }

    private static ApiProblem conflict() {
        return new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE", "该归还已被处理");
    }
}
