package edu.college.booking.auth;

import tools.jackson.databind.json.JsonMapper;
import edu.college.booking.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.NullSecurityContextRepository;
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
    @Order(1)
    SecurityFilterChain internalTaskSecurity(HttpSecurity http, JsonMapper mapper,
            @Value("${tasks.service-token:}") String token) throws Exception {
        http.securityMatcher("/api/v1/internal/tasks/**");
        http.securityContext(config -> config.securityContextRepository(new NullSecurityContextRepository()));
        http.sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);
        http.requestCache(AbstractHttpConfigurer::disable);
        http.authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) -> {
            String header = context.getRequest().getHeader("Authorization");
            boolean allowed = token.length() >= 32 && header != null
                    && MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                        header.getBytes(StandardCharsets.UTF_8));
            return new AuthorizationDecision(allowed);
        }));
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, cause) ->
                    writeError(mapper, response, HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_TOKEN", "任务服务认证失败"))
                .accessDeniedHandler((request, response, cause) ->
                    writeError(mapper, response, HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_TOKEN", "任务服务认证失败")));
        return http.build();
    }

    @Bean
    @Order(2)
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
