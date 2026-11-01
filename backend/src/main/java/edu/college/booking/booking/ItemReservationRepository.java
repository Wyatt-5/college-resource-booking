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
            SELECT r.*, i.name AS item_name FROM reservations r
            JOIN items i ON i.id = r.item_id
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
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(quantity), 0) FROM reservations
                WHERE item_id=? AND status='CONFIRMED'
                  AND start_at < ? AND end_at > ?
                """, Long.class, itemId, Timestamp.from(end), Timestamp.from(start));
    }

    public ItemReservation create(long applicantId, long itemId, Instant start, Instant end,
            String purpose, int quantity, String status, String key, String hash) {
        long id = jdbc.queryForObject("""
                INSERT INTO reservations
                    (applicant_id, resource_type, item_id, start_at, end_at, purpose,
                     quantity, status, idempotency_key, payload_hash)
                VALUES (?, 'ITEM', ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, applicantId, itemId, Timestamp.from(start), Timestamp.from(end),
                purpose, quantity, status, key, hash);
        return jdbc.query(SELECT + " WHERE r.id=?", (rs, row) -> map(rs), id).getFirst();
    }

    public List<ItemReservation> mine(long applicantId, int size, long offset) {
        return jdbc.query(SELECT + " WHERE r.applicant_id=? ORDER BY r.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> map(rs), applicantId, size, offset);
    }

    public long countMine(long applicantId) {
        return jdbc.queryForObject("SELECT count(*) FROM reservations WHERE applicant_id=?",
                Long.class, applicantId);
    }

    private ItemReservation map(ResultSet rs) throws SQLException {
        return new ItemReservation(rs.getLong("id"), rs.getString("resource_type"),
                rs.getLong("item_id"), rs.getLong("applicant_id"), rs.getString("item_name"),
                rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
                rs.getString("purpose"), rs.getInt("quantity"), rs.getString("status"),
                rs.getLong("submission_seq"), rs.getTimestamp("submitted_at").toInstant());
    }

    public record StoredReservation(ItemReservation reservation, String payloadHash) {}
}
