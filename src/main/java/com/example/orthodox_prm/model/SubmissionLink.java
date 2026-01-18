package com.example.orthodox_prm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "submission_link", indexes = {
    @Index(name = "idx_token", columnList = "token", unique = true)
})
public class SubmissionLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(nullable = false, length = 255)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private Integer accessCount;

    @Column(length = 500)
    private String description;

    // Constructors
    public SubmissionLink() {
    }

    public SubmissionLink(String createdBy, LocalDateTime expiresAt, String description) {
        this.token = generateToken();
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.isActive = true;
        this.accessCount = 0;
        this.description = description;
    }

    // Static helper method for token generation
    public static String generateToken() {
        return UUID.randomUUID().toString();
    }

    // Instance method to check if link is expired
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // Never expires if expiresAt is null
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    // Check if link is valid (active and not expired)
    public boolean isValid() {
        return isActive && !isExpired();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
