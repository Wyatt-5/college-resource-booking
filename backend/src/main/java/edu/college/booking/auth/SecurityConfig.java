package edu.college.booking.auth;

import tools.jackson.databind.json.JsonMapper;
import edu.college.booking.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            JsonMapper mapper) throws Exception {
        http.securityContext(config -> config.securityContextRepository(contexts));
        http.csrf(Customizer.withDefaults());
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/items/*/availability").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/announcements", "/api/v1/locations", "/api/v1/locations/*/image", "/api/v1/items", "/api/v1/items/*", "/api/v1/items/*/images", "/api/v1/items/*/images/*", "/api/v1/spaces", "/api/v1/spaces/*/slots", "/api/v1/spaces/*/image", "/api/v1/auth/csrf")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, cause) ->
                        writeError(mapper, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录"))
                .accessDeniedHandler((request, response, cause) ->
                        writeError(mapper, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "没有权限或 CSRF 令牌无效")));
        return http.build();
    }

    private static void writeError(JsonMapper mapper, HttpServletResponse response, HttpStatus status,
            String code, String message) throws IOException {
        String requestId = UUID.randomUUID().toString();
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Request-Id", requestId);
        mapper.writeValue(response.getWriter(), new ApiError(code, message, Map.of(), requestId));
    }
}
