package edu.college.booking.auth;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthHttpTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean AccountUserDetailsService users;
    @MockitoBean AuthorizationService authorization;

    @Test
    void csrfTokenIsAvailableBeforeLogin() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void loginRequiresCsrfAndMeRequiresSession() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"demo\",\"password\":\"password123456\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void loginAndMeShareSession() throws Exception {
        given(users.loadUserByUsername("demo")).willReturn(User.withUsername("demo")
                .password(passwords.encode("password123456"))
                .authorities("ROLE_USER").build());
        given(authorization.currentProfile(org.mockito.ArgumentMatchers.any()))
                .willReturn(new UserProfile(1L, "demo", "测试", "ACTIVE", List.of(), false));
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"demo\",\"password\":\"password123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginName").value("demo"))
                .andReturn();
        mvc.perform(get("/api/v1/auth/me").session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
