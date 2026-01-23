package com.example.orthodox_prm;

import com.example.orthodox_prm.service.CustomOidcUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.client.RestTemplate;

import org.springframework.context.annotation.Lazy;
import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Slf4j
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;

    public SecurityConfig(@Lazy CustomOidcUserService customOidcUserService) {
        this.customOidcUserService = customOidcUserService;
        log.info("=== SecurityConfig created with CustomOidcUserService ===");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/login", "/login/oauth2/**", "/error", "/access-denied", "/debug/**").permitAll()
                        .requestMatchers("/public/submit/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("PRIEST")
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/public/submit/**")
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)
                        )
                        .failureHandler((request, response, exception) -> {
                            log.error("OAuth2 authentication failed: {}", exception.getMessage());
                            String errorMessage = exception.getMessage();
                            if (errorMessage != null && errorMessage.contains("not authorized")) {
                                response.sendRedirect("/login?error=not_authorized");
                            } else if (errorMessage != null && errorMessage.contains("disabled")) {
                                response.sendRedirect("/login?error=disabled");
                            } else {
                                response.sendRedirect("/login?error=true");
                            }
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .permitAll()
                        .logoutSuccessUrl("/login?logout=true")
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' cdn.jsdelivr.net www.google.com www.gstatic.com; style-src 'self' 'unsafe-inline' cdn.jsdelivr.net; font-src 'self' cdn.jsdelivr.net www.gstatic.com; img-src 'self' data: https:; frame-src 'self' www.google.com; connect-src 'self' apis.google.com www.googleapis.com www.google.com tenor.com"))
                );

        return http.build();
    }

    /**
     * Custom access denied handler that logs unauthorized access attempts
     * and redirects to a custom error page.
     */
    private static class CustomAccessDeniedHandler implements AccessDeniedHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                          AccessDeniedException accessDeniedException) throws IOException {
            log.warn("Access denied for user {} attempting to access {}",
                    request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
                    request.getRequestURI());

            response.sendRedirect("/access-denied");
        }
    }
}
