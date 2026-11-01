package edu.college.booking.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CATALOG_DB_TEST", matches = "true")
class CatalogAuditBridgeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogAuditBridge bridge;

    @Test
    void committedCatalogEventIsTransferredOnce() {
        String actor = "audit-test-" + UUID.randomUUID();
        long userId = jdbc.queryForObject("INSERT INTO users(login_name, display_name, password_hash) "
                + "VALUES (?, 'Audit test', 'unused') RETURNING id", Long.class, actor);
        long eventId = jdbc.queryForObject("INSERT INTO catalog_audit_outbox(actor, action, object_id, after_state) "
                + "VALUES (?, 'CREATE_ITEM', 12345, 'camera') RETURNING id", Long.class, actor);
        bridge.drain();
        bridge.drain();
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM audit_logs "
                + "WHERE actor_user_id = ? AND action = 'CREATE_ITEM' AND target_id = 12345", Long.class, userId));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM catalog_audit_outbox "
                + "WHERE id = ? AND delivered_at IS NOT NULL", Long.class, eventId));
    }
}
