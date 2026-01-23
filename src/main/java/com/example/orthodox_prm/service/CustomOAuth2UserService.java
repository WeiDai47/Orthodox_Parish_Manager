package com.example.orthodox_prm.service;

import com.example.orthodox_prm.model.User;
import com.example.orthodox_prm.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom OAuth2 User Service that validates users against the whitelist.
 * Only users with whitelisted email addresses can authenticate.
 */
@Service
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        log.info("=== CustomOAuth2UserService BEAN CREATED ===");
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("=== CustomOAuth2UserService.loadUser() CALLED ===");
        OAuth2User oauth2User = super.loadUser(userRequest);

        String email = oauth2User.getAttribute("email");
        log.info("=== OAuth2 email from Google: {} ===", email);
        if (email == null) {
            log.warn("OAuth2 login attempt without email attribute");
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"),
                    "Email not provided by OAuth2 provider"
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
        String name = oauth2User.getAttribute("name");
        if (name != null && (user.getDisplayName() == null || !user.getDisplayName().equals(name))) {
            user.setDisplayName(name);
        }
        userRepository.save(user);

        log.info("User {} logged in with role {}", email, user.getRole());

        // Create authority based on user's role
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        // Build attributes map including role
        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("role", user.getRole().name());
        attributes.put("userId", user.getId());

        // Return OAuth2User with proper role authority
        return new DefaultOAuth2User(
                Collections.singletonList(authority),
                attributes,
                "email"  // Use email as the name attribute key
        );
    }
}
