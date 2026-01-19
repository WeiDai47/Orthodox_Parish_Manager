package com.example.orthodox_prm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for rate limiting submissions to prevent spam and DOS attacks.
 * Uses in-memory storage with automatic cleanup of old entries.
 */
@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    // Rate limit configurations
    private static final int MAX_SUBMISSIONS_PER_IP_PER_HOUR = 5;
    private static final int MAX_SUBMISSIONS_PER_TOKEN_PER_DAY = 10;
    private static final int IP_BLOCK_DURATION_MINUTES = 30;

    // Storage for tracking submissions
    private final Map<String, SubmissionTracker> ipSubmissions = new ConcurrentHashMap<>();
    private final Map<String, SubmissionTracker> tokenSubmissions = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> blockedIps = new ConcurrentHashMap<>();

    /**
     * Check if an IP address is allowed to submit
     * @param ipAddress The client's IP address
     * @return true if allowed, false if rate limited
     */
    public boolean isIpAllowed(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            logger.warn("Empty IP address in rate limit check");
            return false;
        }

        // Check if IP is blocked
        LocalDateTime blockedUntil = blockedIps.get(ipAddress);
        if (blockedUntil != null) {
            if (LocalDateTime.now().isBefore(blockedUntil)) {
                logger.warn("Blocked IP {} attempted submission, blocked until {}", ipAddress, blockedUntil);
                return false;
            } else {
                // Block expired, remove it
                blockedIps.remove(ipAddress);
            }
        }

        // Check submission count
        SubmissionTracker tracker = ipSubmissions.get(ipAddress);
        if (tracker == null) {
            return true;
        }

        // Clean up old entries and check
        tracker.cleanupOldEntries(1, ChronoUnit.HOURS);
        return tracker.getCount() < MAX_SUBMISSIONS_PER_IP_PER_HOUR;
    }

    /**
     * Check if a token is allowed to accept more submissions
     * @param token The submission link token
     * @return true if allowed, false if rate limited
     */
    public boolean isTokenAllowed(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        SubmissionTracker tracker = tokenSubmissions.get(token);
        if (tracker == null) {
            return true;
        }

        // Clean up entries older than 24 hours
        tracker.cleanupOldEntries(24, ChronoUnit.HOURS);
        return tracker.getCount() < MAX_SUBMISSIONS_PER_TOKEN_PER_DAY;
    }

    /**
     * Record a submission from an IP address
     * @param ipAddress The client's IP address
     */
    public void recordIpSubmission(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return;
        }

        SubmissionTracker tracker = ipSubmissions.computeIfAbsent(ipAddress, k -> new SubmissionTracker());
        tracker.recordSubmission();

        // If they've exceeded the limit, block them
        if (tracker.getCount() >= MAX_SUBMISSIONS_PER_IP_PER_HOUR) {
            LocalDateTime blockUntil = LocalDateTime.now().plusMinutes(IP_BLOCK_DURATION_MINUTES);
            blockedIps.put(ipAddress, blockUntil);
            logger.warn("IP {} blocked until {} for exceeding rate limit", ipAddress, blockUntil);
        }

        logger.debug("Recorded submission from IP {}, count: {}", ipAddress, tracker.getCount());
    }

    /**
     * Record a submission for a token
     * @param token The submission link token
     */
    public void recordTokenSubmission(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        SubmissionTracker tracker = tokenSubmissions.computeIfAbsent(token, k -> new SubmissionTracker());
        tracker.recordSubmission();

        logger.debug("Recorded submission for token {}, count: {}",
            token.substring(0, Math.min(8, token.length())) + "...", tracker.getCount());
    }

    /**
     * Get remaining submissions allowed for an IP
     * @param ipAddress The client's IP address
     * @return Number of remaining submissions allowed
     */
    public int getRemainingIpSubmissions(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return 0;
        }

        if (blockedIps.containsKey(ipAddress)) {
            return 0;
        }

        SubmissionTracker tracker = ipSubmissions.get(ipAddress);
        if (tracker == null) {
            return MAX_SUBMISSIONS_PER_IP_PER_HOUR;
        }

        tracker.cleanupOldEntries(1, ChronoUnit.HOURS);
        return Math.max(0, MAX_SUBMISSIONS_PER_IP_PER_HOUR - tracker.getCount());
    }

    /**
     * Get remaining submissions allowed for a token
     * @param token The submission link token
     * @return Number of remaining submissions allowed
     */
    public int getRemainingTokenSubmissions(String token) {
        if (token == null || token.isEmpty()) {
            return 0;
        }

        SubmissionTracker tracker = tokenSubmissions.get(token);
        if (tracker == null) {
            return MAX_SUBMISSIONS_PER_TOKEN_PER_DAY;
        }

        tracker.cleanupOldEntries(24, ChronoUnit.HOURS);
        return Math.max(0, MAX_SUBMISSIONS_PER_TOKEN_PER_DAY - tracker.getCount());
    }

    /**
     * Clean up old tracking data (should be called periodically)
     */
    public void cleanupOldData() {
        LocalDateTime now = LocalDateTime.now();

        // Remove expired IP blocks
        blockedIps.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));

        // Clean up IP trackers with no recent activity
        ipSubmissions.entrySet().removeIf(entry -> {
            entry.getValue().cleanupOldEntries(2, ChronoUnit.HOURS);
            return entry.getValue().getCount() == 0;
        });

        // Clean up token trackers with no recent activity
        tokenSubmissions.entrySet().removeIf(entry -> {
            entry.getValue().cleanupOldEntries(48, ChronoUnit.HOURS);
            return entry.getValue().getCount() == 0;
        });

        logger.debug("Rate limit cleanup complete. Active IP trackers: {}, Token trackers: {}, Blocked IPs: {}",
            ipSubmissions.size(), tokenSubmissions.size(), blockedIps.size());
    }

    /**
     * Inner class to track submission timestamps
     */
    private static class SubmissionTracker {
        private final Map<LocalDateTime, Integer> submissions = new ConcurrentHashMap<>();

        public void recordSubmission() {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            submissions.merge(now, 1, Integer::sum);
        }

        public int getCount() {
            return submissions.values().stream().mapToInt(Integer::intValue).sum();
        }

        public void cleanupOldEntries(long amount, ChronoUnit unit) {
            LocalDateTime cutoff = LocalDateTime.now().minus(amount, unit);
            submissions.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        }
    }
}
