package com.example.orthodox_prm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Stores user preferences (timezone, etc.)
 * Keyed by username from Spring Security (email for OAuth2, username for form login)
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "timezone", nullable = false)
    private String timezone; // e.g., "America/New_York", "America/Chicago"

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (timezone == null) {
            timezone = "America/New_York"; // Default to Eastern Time
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
