package edu.college.booking.booking;

import edu.college.booking.common.ApiProblem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReservationActionOperations {
    private final JdbcTemplate jdbc;

    public ReservationActionOperations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long once(long actorId, String action, long reservationId, String key,
            String payload, LongSupplier operation) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Idempotency-Key 必填且最长200字符");
        }
        String hash = sha256(payload);
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1, actorId + ":" + action + ":" + key);
                statement.execute();
            }
            return null;
        });
        var previous = jdbc.queryForList("""
                SELECT reservation_id,payload_hash,result_id
                FROM reservation_action_idempotency
                WHERE actor_user_id=? AND action=? AND request_key=?
                """, actorId, action, key);
        if (!previous.isEmpty()) {
            var row = previous.getFirst();
            if (((Number) row.get("reservation_id")).longValue() != reservationId
                    || !hash.equals(row.get("payload_hash"))) {
                throw new ApiProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "同一请求键不能用于不同操作内容");
            }
            return ((Number) row.get("result_id")).longValue();
        }
        long result = operation.getAsLong();
        jdbc.update("""
                INSERT INTO reservation_action_idempotency
                    (actor_user_id,action,reservation_id,request_key,payload_hash,result_id)
                VALUES (?,?,?,?,?,?)
                """, actorId, action, reservationId, key, hash, result);
        return result;
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
