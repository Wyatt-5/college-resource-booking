package edu.college.booking.audit;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Moves committed catalog events into the shared, append-only audit log. */
@Component
@EnableScheduling
public class CatalogAuditBridge {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CatalogAuditBridge(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${CATALOG_AUDIT_DRAIN_MS:5000}")
    @Transactional
    public void drain() {
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT id, actor, action, object_id, before_state, after_state "
                        + "FROM catalog_audit_outbox WHERE delivered_at IS NULL "
                        + "ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED");
        for (Map<String, Object> event : events) {
            long eventId = ((Number) event.get("id")).longValue();
            String actorLogin = (String) event.get("actor");
            String action = (String) event.get("action");
            Long actorId = jdbc.query("SELECT id FROM users WHERE login_name = ?",
                    (rs, row) -> rs.getLong(1), actorLogin).stream().findFirst().orElse(null);
            String before = (String) event.get("before_state");
            String after = (String) event.get("after_state");
            audit.record(actorId, action, targetType(action), ((Number) event.get("object_id")).longValue(),
                    before.isEmpty() ? null : Map.of("snapshot", before),
                    Map.of("actorLogin", actorLogin, "snapshot", after));
            jdbc.update("UPDATE catalog_audit_outbox SET delivered_at = CURRENT_TIMESTAMP WHERE id = ?", eventId);
        }
    }

    private String targetType(String action) {
        if (action.endsWith("_IMAGE")) return "ITEM_IMAGE";
        if (action.endsWith("_UNIT")) return "ITEM_UNIT";
        if (action.equals("VERIFY_RETURN")) return "RESERVATION";
        return "ITEM";
    }
}
