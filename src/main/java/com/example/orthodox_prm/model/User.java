package com.example.orthodox_prm.model;

import com.example.orthodox_prm.Enum.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents an authorized user in the system.
 * Users are whitelisted by email address and assigned a role.
 * Authentication is handled via Google OAuth2.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;  // Google OAuth2 email

    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;  // PRIEST, SECRETARY, VIEWER

    @Column(nullable = false)
    private boolean enabled = true;

    private LocalDateTime createdAt;

    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public User(String email, Role role) {
        this.email = email;
        this.role = role;
        this.enabled = true;
    }

    public User(String email, String displayName, Role role) {
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
    }
}
