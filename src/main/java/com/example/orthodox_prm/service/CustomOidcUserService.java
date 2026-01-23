package com.example.orthodox_prm.service;

import com.example.orthodox_prm.model.User;
import com.example.orthodox_prm.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Custom OIDC User Service that validates users against the whitelist.
 * Google uses OIDC (OpenID Connect), so we need to extend OidcUserService.
 */
@Service
@Slf4j
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    public CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        log.info("=== CustomOidcUserService BEAN CREATED ===");
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("=== CustomOidcUserService.loadUser() CALLED ===");
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        log.info("=== OIDC email from Google: {} ===", email);

        if (email == null) {
            log.warn("OIDC login attempt without email attribute");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"),
                    "Email not provided by OIDC provider"
            );
        }

        // Check if user is whitelisted
        log.info("Looking up user with email: {}", email);
        log.info("Total users in database: {}", userRepository.count());

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        log.info("User lookup result: {}", user != null ? "FOUND - Role: " + user.getRole() : "NOT FOUND");

        if (user == null) {
            log.warn("Unauthorized login attempt from non-whitelisted email: {}", email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("access_denied"),
                    "Your email address (" + email + ") is not authorized to access this application. " +
                    "Please contact the parish priest to request access."
            );
        }

        if (!user.isEnabled()) {
            log.warn("Login attempt from disabled account: {}", email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_disabled"),
                    "Your account has been disabled. Please contact the parish priest."
            );
        }

        // Update last login time and display name
        user.setLastLogin(LocalDateTime.now());
        String name = oidcUser.getFullName();
        if (name != null && (user.getDisplayName() == null || !user.getDisplayName().equals(name))) {
            user.setDisplayName(name);
        }
        userRepository.save(user);

        log.info("User {} logged in with role {}", email, user.getRole());

        // Create authority based on user's role
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        // Return OidcUser with proper role authority
        return new DefaultOidcUser(
                Collections.singletonList(authority),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email"  // Use email as the name attribute key
        );
    }
}
