package com.locationreview.location_review_platform.config;

import com.locationreview.location_review_platform.repository.UserRepository;
import com.locationreview.location_review_platform.service.AccountService;
import java.net.URI;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return login -> {
            String normalized = AccountService.normalize(login);
            var account = (normalized != null && normalized.contains("@") ? users.findByEmail(normalized) : users.findByUsername(normalized))
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return org.springframework.security.core.userdetails.User.withUsername(account.getId().toString())
                    .password(account.getPasswordHash()).authorities("USER").build();
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/users/*/follow", "/reviews/*/like", "/reviews/*/comments", "/comments/*/delete").authenticated()
                .requestMatchers("/api/v1/bookmarks", "/api/v1/bookmarks/**", "/saved", "/saved/**").authenticated()
                .requestMatchers("/api/v1/users/me", "/settings/profile", "/reviews/new", "/reviews/*/edit", "/reviews/*/delete", "/collections/new", "/collections/*/edit", "/collections/*/delete", "/collections/*/places/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                .requestMatchers("/api/v1/**").authenticated().anyRequest().permitAll())
            .formLogin(login -> login.loginPage("/login").successHandler((request, response, authentication) -> {
                var cache = new HttpSessionRequestCache();
                var saved = cache.getRequest(request, response);
                String target = request.getContextPath() + "/explore";
                if (saved != null) {
                    URI uri = URI.create(saved.getRedirectUrl());
                    String path = uri.getRawPath();
                    if (path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\"))
                        target = path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                    cache.removeRequest(request, response);
                }
                response.sendRedirect(response.encodeRedirectURL(target));
            }).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                if (request.getRequestURI().startsWith("/api/v1/")) writeProblem(response, mapper, HttpStatus.UNAUTHORIZED, "authentication_required", request.getRequestURI());
                else response.sendRedirect(request.getContextPath() + "/login");
            }).accessDeniedHandler((request, response, exception) -> {
                if (request.getRequestURI().startsWith("/api/v1/")) writeProblem(response, mapper, HttpStatus.FORBIDDEN, "access_denied", request.getRequestURI());
                else response.sendError(HttpServletResponse.SC_FORBIDDEN);
            })).build();
    }
    private static void writeProblem(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String code, String path) throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setDetail(status == HttpStatus.UNAUTHORIZED ? "Authentication is required." : "Access is denied.");
        problem.setInstance(URI.create(path)); problem.setProperty("code", code);
        response.setStatus(status.value()); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE); response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
