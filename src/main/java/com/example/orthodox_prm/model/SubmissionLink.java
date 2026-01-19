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

    // Maximum number of submissions allowed (null = unlimited)
    @Column(nullable = true)
    private Integer maxSubmissions;

    // Count of successful submissions made through this link
    @Column(nullable = true)
    private Integer submissionCount = 0;

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
        this.submissionCount = 0;
        this.maxSubmissions = null; // Unlimited by default
        this.description = description;
    }

    public SubmissionLink(String createdBy, LocalDateTime expiresAt, String description, Integer maxSubmissions) {
        this(createdBy, expiresAt, description);
        this.maxSubmissions = maxSubmissions;
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

    // Check if link has reached its submission limit
    public boolean hasReachedLimit() {
        if (maxSubmissions == null) {
            return false; // No limit set
        }
        return submissionCount >= maxSubmissions;
    }

    // Check if link is valid (active, not expired, and not at limit)
    public boolean isValid() {
        return isActive && !isExpired() && !hasReachedLimit();
    }

    // Increment submission count
    public void incrementSubmissionCount() {
        this.submissionCount = (this.submissionCount == null ? 0 : this.submissionCount) + 1;
    }

    // Get remaining submissions (null if unlimited)
    public Integer getRemainingSubmissions() {
        if (maxSubmissions == null) {
            return null; // Unlimited
        }
        return Math.max(0, maxSubmissions - (submissionCount == null ? 0 : submissionCount));
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

    public Integer getMaxSubmissions() {
        return maxSubmissions;
    }

    public void setMaxSubmissions(Integer maxSubmissions) {
        this.maxSubmissions = maxSubmissions;
    }

    public Integer getSubmissionCount() {
        return submissionCount != null ? submissionCount : 0;
    }

    public void setSubmissionCount(Integer submissionCount) {
        this.submissionCount = submissionCount;
    }
}
