package edu.college.booking.communication;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="CATALOG_DB_TEST",matches="true")
class CommunicationDatabaseTest {
    @Autowired edu.college.booking.auth.AccountUserDetailsService loginDetails;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CommunicationService service;
    String reviewer, alice, bob;
    @BeforeEach void setup(){
        String suffix=UUID.randomUUID().toString().substring(0,10);
        reviewer="notice-admin-"+suffix;alice="notice-alice-"+suffix;bob="notice-bob-"+suffix;
        long id=createUser(reviewer);createUser(alice);createUser(bob);
        db.update("INSERT INTO reviewer_memberships(user_id) VALUES (?)",id);
    }
    long createUser(String login){return db.queryForObject("INSERT INTO users(login_name,display_name,password_hash) VALUES (?,?,'hash') RETURNING id",Long.class,login,login);}
    @Test void publishedAnnouncementsArePublicAndOnlyStaffCanEditAndWithdraw() throws Exception {
        String body="{\"title\":\"学院通知\",\"content\":\"活动安排\"}";
        mvc.perform(post("/api/v1/announcements").with(user(loginDetails.loadUserByUsername(alice))).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        String created=mvc.perform(post("/api/v1/announcements").with(user(loginDetails.loadUserByUsername(reviewer))).with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id=json.readTree(created).get("id").asLong();
        assertTrue(service.announcements(null,false,0,100).content().stream().anyMatch(a->a.id()==id));
        mvc.perform(put("/api/v1/announcements/"+id).with(user(loginDetails.loadUserByUsername(reviewer))).with(csrf()).contentType("application/json").content("{\"title\":\"修改通知\",\"content\":\"更新内容\",\"status\":\"PUBLISHED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("修改通知"));
        mvc.perform(put("/api/v1/announcements/"+id).with(user(loginDetails.loadUserByUsername(reviewer))).with(csrf()).contentType("application/json").content("{\"title\":\"修改通知\",\"content\":\"更新内容\",\"status\":\"WITHDRAWN\"}")).andExpect(status().isOk());
        assertFalse(service.announcements(null,false,0,100).content().stream().anyMatch(a->a.id()==id));
        assertTrue(service.announcements(new UsernamePasswordAuthenticationToken(reviewer,"",java.util.List.of()),true,0,100).content().stream().anyMatch(a->a.id()==id));
    }
    @Test void feedbackRemainsPrivateAndUserCanSeeManagementReply() throws Exception {
        mvc.perform(post("/api/v1/feedback").with(csrf()).contentType("application/json").content("{\"content\":\"测试反馈\"}")).andExpect(status().isUnauthorized());
        String created=mvc.perform(post("/api/v1/feedback").with(user(loginDetails.loadUserByUsername(alice))).with(csrf()).contentType("application/json").content("{\"content\":\"灯光需要维修\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN")).andReturn().getResponse().getContentAsString();
        long id=json.readTree(created).get("id").asLong();
        mvc.perform(get("/api/v1/feedback").with(user(loginDetails.loadUserByUsername(bob)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/feedback").with(user(loginDetails.loadUserByUsername(bob))).param("managed","true")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/feedback/"+id).with(user(loginDetails.loadUserByUsername(bob))).with(csrf()).contentType("application/json").content("{\"status\":\"RESOLVED\",\"response\":\"完成\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/feedback/"+id).with(user(loginDetails.loadUserByUsername(reviewer))).with(csrf()).contentType("application/json").content("{\"status\":\"RESOLVED\",\"response\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/feedback/"+id).with(user(loginDetails.loadUserByUsername(reviewer))).with(csrf()).contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"response\":\"已经联系维修\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.handlerName").value(reviewer));
        mvc.perform(put("/api/v1/feedback/"+id).with(user(loginDetails.loadUserByUsername(reviewer))).with(csrf()).contentType("application/json").content("{\"status\":\"RESOLVED\",\"response\":\"已修好\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/api/v1/feedback").with(user(loginDetails.loadUserByUsername(alice)))).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].response").value("已修好"));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='FEEDBACK_HANDLED' AND target_id=?",Integer.class,id));
    }

    private UsernamePasswordAuthenticationToken actor(String login) {
        return new UsernamePasswordAuthenticationToken(login, "", java.util.List.of());
    }

    @Test void announcementReadStateBelongsToEachAccountAndSurvivesANewSession() throws Exception {
        var announcement = service.createAnnouncement(actor(reviewer),
                new CommunicationService.AnnouncementInput("新公告", "下周活动安排", "PUBLISHED"));
        var version = new CommunicationService.AnnouncementVersion(announcement.id(), announcement.updatedAt());
        String body = json.writeValueAsString(new CommunicationService.AnnouncementReadInput(java.util.List.of(version)));

        assertTrue(service.unreadAnnouncements(actor(alice), 0, 100).content().stream()
                .anyMatch(row -> row.id() == announcement.id()));
        mvc.perform(post("/api/v1/announcements/read")
                .with(user(loginDetails.loadUserByUsername(alice))).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isNoContent());
        // A repeated acknowledgement is safe, and the next authenticated session sees the same state.
        mvc.perform(post("/api/v1/announcements/read")
                .with(user(loginDetails.loadUserByUsername(alice))).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isNoContent());
        assertFalse(service.unreadAnnouncements(actor(alice), 0, 100).content().stream()
                .anyMatch(row -> row.id() == announcement.id()));
        assertTrue(service.unreadAnnouncements(actor(bob), 0, 100).content().stream()
                .anyMatch(row -> row.id() == announcement.id()));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM announcement_reads WHERE announcement_id=?",
                Integer.class, announcement.id()));
        mvc.perform(get("/api/v1/announcements/unread").with(user(loginDetails.loadUserByUsername(alice))))
                .andExpect(status().isOk());
    }

    @Test void updatedAndRepublishedNoticesRemainUnreadWhenAnOldVersionIsAcknowledged() {
        var announcement = service.createAnnouncement(actor(reviewer),
                new CommunicationService.AnnouncementInput("原公告", "原内容", "PUBLISHED"));
        var oldVersion = new CommunicationService.AnnouncementReadInput(java.util.List.of(
                new CommunicationService.AnnouncementVersion(announcement.id(), announcement.updatedAt())));
        service.markAnnouncementsRead(actor(alice), oldVersion);
        db.update("UPDATE announcements SET content='新内容', updated_at=updated_at + INTERVAL '1 second' WHERE id=?",
                announcement.id());
        service.markAnnouncementsRead(actor(alice), oldVersion);
        assertTrue(service.unreadAnnouncements(actor(alice), 0, 100).content().stream()
                .anyMatch(row -> row.id() == announcement.id()));

        db.update("UPDATE announcements SET status='WITHDRAWN' WHERE id=?", announcement.id());
        assertFalse(service.unreadAnnouncements(actor(alice), 0, 100).content().stream()
                .anyMatch(row -> row.id() == announcement.id()));
        db.update("UPDATE announcements SET status='PUBLISHED', updated_at=updated_at + INTERVAL '1 second' WHERE id=?",
                announcement.id());
        assertTrue(service.unreadAnnouncements(actor(alice), 0, 100).content().stream()
                .anyMatch(row -> row.id() == announcement.id()));
    }

    @Test void unreadAndReadEndpointsRequireAuthenticationAndWritesRequireCsrf() throws Exception {
        mvc.perform(get("/api/v1/announcements/unread")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/announcements/read").with(csrf())
                .contentType("application/json").content("{\"announcements\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/announcements/read").with(user(loginDetails.loadUserByUsername(alice)))
                .contentType("application/json").content("{\"announcements\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/announcements/read").with(user(loginDetails.loadUserByUsername(alice))).with(csrf())
                .contentType("application/json").content("{\"announcements\":[{\"id\":1}]}"))
                .andExpect(status().isBadRequest());
    }
}
