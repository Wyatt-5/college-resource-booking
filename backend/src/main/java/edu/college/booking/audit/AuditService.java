package edu.college.booking.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import edu.college.booking.common.ApiProblem;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    public AuditService(JdbcTemplate jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(Long actorId, String action, String targetType, Long targetId,
            Object before, Object after) {
        jdbc.update("INSERT INTO audit_logs (actor_user_id, action, outcome, target_type, target_id, before_data, after_data) "
                        + "VALUES (?, ?, 'SUCCESS', ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))",
                actorId, action, targetType, targetId, json(before), json(after));
    }

    public List<Map<String, Object>> recent(int limit) {
        return jdbc.queryForList("SELECT id, actor_user_id AS \"actorUserId\", action, outcome, "
                + "target_type AS \"targetType\", target_id AS \"targetId\", "
                + "created_at AS \"createdAt\" FROM audit_logs ORDER BY id DESC LIMIT ?", limit);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiProblem(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_SERIALIZATION_FAILED", "审计记录写入失败");
        }
    }
}
