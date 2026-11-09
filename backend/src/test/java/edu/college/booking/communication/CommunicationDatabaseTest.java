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
}
