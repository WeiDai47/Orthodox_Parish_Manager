package com.example.orthodox_prm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class GoogleCalendarService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String GOOGLE_CALENDAR_API_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

    public GoogleCalendarService(
            OAuth2AuthorizedClientService authorizedClientService,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.authorizedClientService = authorizedClientService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an event in the user's Google Calendar
     * Only works if the user authenticated via Google OAuth2
     */
    public void createCalendarEvent(String eventTitle, LocalDate eventDate, String description, String sacramentType) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.warn("User is not authenticated with Google. Calendar sync skipped.");
                return;
            }

            String eventJson = buildEventJson(eventTitle, eventDate, description, sacramentType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<String> entity = new HttpEntity<>(eventJson, headers);

            restTemplate.postForObject(GOOGLE_CALENDAR_API_URL, entity, String.class);
            log.info("Event created in Google Calendar: {}", eventTitle);

        } catch (Exception e) {
            log.error("Failed to create event in Google Calendar", e);
        }
    }

    /**
     * Check if current user has Google OAuth2 token
     */
    public boolean isGoogleOAuth2Authenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (!(authentication.getPrincipal() instanceof OAuth2User)) {
                return false;
            }

            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    "google",
                    oAuth2User.getName()
            );

            return authorizedClient != null && authorizedClient.getAccessToken() != null;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the access token for the currently authenticated Google OAuth2 user
     */
    private String getAccessToken() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Check if user is authenticated via Google OAuth2
            if (!(authentication.getPrincipal() instanceof OAuth2User)) {
                return null;
            }

            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String clientRegistrationId = "google";

            // Get the OAuth2 authorized client (contains the access token)
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    clientRegistrationId,
                    oAuth2User.getName()
            );

            if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
                log.warn("No Google OAuth2 token found for user: {}", oAuth2User.getName());
                return null;
            }

            return authorizedClient.getAccessToken().getTokenValue();

        } catch (Exception e) {
            log.error("Error getting Google Calendar access token", e);
            return null;
        }
    }

    /**
     * Builds the event JSON payload for Google Calendar API
     */
    private String buildEventJson(String title, LocalDate date, String description, String sacramentType) {
        Map<String, Object> event = new HashMap<>();

        event.put("summary", title);
        event.put("description", buildDescription(description, sacramentType));

        // Set date (all-day event)
        String dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String nextDayString = date.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, String> start = new HashMap<>();
        start.put("date", dateString);
        start.put("timeZone", "America/New_York");

        Map<String, String> end = new HashMap<>();
        end.put("date", nextDayString);
        end.put("timeZone", "America/New_York");

        event.put("start", start);
        event.put("end", end);

        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Error building event JSON", e);
            return "{}";
        }
    }

    /**
     * Builds the description with sacrament type if applicable
     */
    private String buildDescription(String description, String sacramentType) {
        StringBuilder desc = new StringBuilder();

        if (sacramentType != null && !sacramentType.isEmpty() && !sacramentType.equals("NONE")) {
            desc.append("Sacrament: ").append(sacramentType).append("\n");
        }

        if (description != null && !description.isEmpty()) {
            desc.append("Notes: ").append(description);
        }

        if (desc.length() == 0) {
            return "Event created in Orthodox Parish Manager";
        }

        return desc.toString();
    }
}
