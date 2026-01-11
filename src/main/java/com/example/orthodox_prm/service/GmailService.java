package com.example.orthodox_prm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GmailService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate;

    private static final String GMAIL_API_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    public GmailService(OAuth2AuthorizedClientService authorizedClientService, RestTemplate restTemplate) {
        this.authorizedClientService = authorizedClientService;
        this.restTemplate = restTemplate;
    }

    /**
     * Send a group email with all recipients in BCC
     */
    public void sendEmailBCC(String subject, String body, List<String> recipients) throws Exception {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            throw new IllegalStateException("User is not authenticated with Google");
        }

        // Build email with BCC recipients
        String mimeMessage = buildMimeMessageBCC(subject, body, recipients);
        String encodedMessage = base64UrlEncode(mimeMessage);

        sendToGmailAPI(accessToken, encodedMessage);
    }

    /**
     * Send individual emails to each recipient
     */
    public void sendIndividualEmails(String subject, String body, List<String> recipients) throws Exception {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            throw new IllegalStateException("User is not authenticated with Google");
        }

        for (String recipient : recipients) {
            String mimeMessage = buildMimeMessage(recipient, subject, body);
            String encodedMessage = base64UrlEncode(mimeMessage);
            sendToGmailAPI(accessToken, encodedMessage);
        }
    }

    /**
     * Build RFC 2822 MIME message for single recipient
     */
    private String buildMimeMessage(String to, String subject, String body) {
        StringBuilder message = new StringBuilder();
        message.append("To: ").append(to).append("\r\n");
        message.append("Subject: ").append(subject).append("\r\n");
        message.append("Content-Type: text/plain; charset=UTF-8\r\n");
        message.append("\r\n");
        message.append(body);
        return message.toString();
    }

    /**
     * Build RFC 2822 MIME message with BCC recipients
     */
    private String buildMimeMessageBCC(String subject, String body, List<String> bccRecipients) {
        StringBuilder message = new StringBuilder();
        // Don't include To: field - Gmail will use BCC
        message.append("Bcc: ").append(String.join(",", bccRecipients)).append("\r\n");
        message.append("Subject: ").append(subject).append("\r\n");
        message.append("Content-Type: text/plain; charset=UTF-8\r\n");
        message.append("\r\n");
        message.append(body);
        return message.toString();
    }

    /**
     * Base64url encode the message (Gmail API requirement)
     */
    private String base64UrlEncode(String input) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Send to Gmail API
     */
    private void sendToGmailAPI(String accessToken, String encodedMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, String> payload = new HashMap<>();
        payload.put("raw", encodedMessage);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        restTemplate.postForObject(GMAIL_API_URL, entity, String.class);
        log.info("Email sent successfully via Gmail API");
    }

    /**
     * Get Google OAuth access token (reused from GoogleCalendarService pattern)
     */
    private String getAccessToken() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (!(authentication.getPrincipal() instanceof OAuth2User)) {
                return null;
            }

            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    "google",
                    oAuth2User.getName()
            );

            if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
                log.warn("No Google OAuth2 token found for user: {}", oAuth2User.getName());
                return null;
            }

            return authorizedClient.getAccessToken().getTokenValue();
        } catch (Exception e) {
            log.error("Error getting Google access token", e);
            return null;
        }
    }

    /**
     * Check if user is authenticated with Google OAuth
     */
    public boolean isGoogleOAuth2Authenticated() {
        return getAccessToken() != null;
    }
}
