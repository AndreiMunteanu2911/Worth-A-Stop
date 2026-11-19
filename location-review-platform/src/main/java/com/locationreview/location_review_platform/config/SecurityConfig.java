package com.locationreview.location_review_platform.config;

import java.net.URI;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(login -> login.defaultSuccessUrl("/", false))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/v1/")) {
                        writeProblem(response, mapper, HttpStatus.UNAUTHORIZED, "authentication_required",
                                request.getRequestURI());
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                }).accessDeniedHandler((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/v1/")) {
                        writeProblem(response, mapper, HttpStatus.FORBIDDEN, "access_denied",
                                request.getRequestURI());
                    } else {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    }
                }))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // P03 replaces this with database-backed accounts; no temporary account is created.
        return username -> { throw new UsernameNotFoundException("Account unavailable"); };
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper mapper, HttpStatus status,
                                     String code, String path)
            throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setDetail(status == HttpStatus.UNAUTHORIZED ? "Authentication is required." : "Access is denied.");
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
