package com.example.orthodox_prm.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Service for verifying Google reCAPTCHA v3 tokens.
 * Provides invisible bot detection without user friction.
 */
@Service
public class RecaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(RecaptchaService.class);

    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final double MINIMUM_SCORE = 0.5; // Scores below this are considered bots

    @Value("${recaptcha.secret-key:}")
    private String secretKey;

    @Value("${recaptcha.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;

    public RecaptchaService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Check if reCAPTCHA is enabled
     * @return true if reCAPTCHA verification is enabled
     */
    public boolean isEnabled() {
        return enabled && secretKey != null && !secretKey.isEmpty();
    }

    /**
     * Verify a reCAPTCHA token
     * @param token The reCAPTCHA response token from the client
     * @param remoteIp The client's IP address (optional)
     * @return true if verification passed, false if failed or suspicious
     */
    public boolean verifyToken(String token, String remoteIp) {
        if (!isEnabled()) {
            logger.debug("reCAPTCHA verification skipped - not enabled");
            return true; // Skip verification if not configured
        }

        if (token == null || token.isEmpty()) {
            logger.warn("reCAPTCHA token is empty or null");
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", secretKey);
            params.add("response", token);
            if (remoteIp != null && !remoteIp.isEmpty()) {
                params.add("remoteip", remoteIp);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<RecaptchaResponse> response = restTemplate.postForEntity(
                RECAPTCHA_VERIFY_URL,
                request,
                RecaptchaResponse.class
            );

            RecaptchaResponse body = response.getBody();
            if (body == null) {
                logger.error("reCAPTCHA verification returned null response");
                return false;
            }

            if (!body.isSuccess()) {
                logger.warn("reCAPTCHA verification failed. Error codes: {}", body.getErrorCodes());
                return false;
            }

            // Check the score (v3 specific)
            if (body.getScore() < MINIMUM_SCORE) {
                logger.warn("reCAPTCHA score {} is below minimum threshold {}", body.getScore(), MINIMUM_SCORE);
                return false;
            }

            logger.debug("reCAPTCHA verification passed. Score: {}, Action: {}", body.getScore(), body.getAction());
            return true;

        } catch (Exception e) {
            logger.error("reCAPTCHA verification error: {}", e.getMessage());
            // In case of error, we might want to allow the submission to prevent blocking legitimate users
            // But for security, we'll reject it
            return false;
        }
    }

    /**
     * Get the minimum score threshold
     * @return The minimum acceptable reCAPTCHA score
     */
    public double getMinimumScore() {
        return MINIMUM_SCORE;
    }

    /**
     * Response object for reCAPTCHA API
     */
    public static class RecaptchaResponse {
        private boolean success;
        private double score;
        private String action;

        @JsonProperty("challenge_ts")
        private String challengeTs;

        private String hostname;

        @JsonProperty("error-codes")
        private String[] errorCodes;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getChallengeTs() {
            return challengeTs;
        }

        public void setChallengeTs(String challengeTs) {
            this.challengeTs = challengeTs;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public String[] getErrorCodes() {
            return errorCodes;
        }

        public void setErrorCodes(String[] errorCodes) {
            this.errorCodes = errorCodes;
        }
    }
}
