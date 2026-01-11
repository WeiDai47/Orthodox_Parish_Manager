package com.example.orthodox_prm.service;

import com.example.orthodox_prm.dto.ConflictReport;
import com.example.orthodox_prm.dto.GoogleCalendarEvent;
import com.example.orthodox_prm.repository.UserPreferencesRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleCalendarService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserPreferencesRepository userPreferencesRepository;

    private static final String GOOGLE_CALENDAR_API_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

    public GoogleCalendarService(
            OAuth2AuthorizedClientService authorizedClientService,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            UserPreferencesRepository userPreferencesRepository) {
        this.authorizedClientService = authorizedClientService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userPreferencesRepository = userPreferencesRepository;
    }

    /**
     * Creates an event in the user's Google Calendar
     * Only works if the user authenticated via Google OAuth2
     * @param eventTitle Title of the event
     * @param eventDate Date of the event
     * @param startTime Optional start time (null = all-day event)
     * @param endTime Optional end time (null = all-day event)
     * @param description Optional event description
     * @param sacramentType Optional sacrament type
     * @param attendeeEmails Optional list of attendee email addresses
     */
    public void createCalendarEvent(String eventTitle, LocalDate eventDate, LocalTime startTime, LocalTime endTime,
                                    String description, String sacramentType, List<String> attendeeEmails) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.warn("User is not authenticated with Google. Calendar sync skipped.");
                return;
            }

            String eventJson = buildEventJson(eventTitle, eventDate, startTime, endTime, description, sacramentType, attendeeEmails);

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
     * Overload for backward compatibility - creates all-day events
     */
    public void createCalendarEvent(String eventTitle, LocalDate eventDate, String description, String sacramentType) {
        createCalendarEvent(eventTitle, eventDate, null, null, description, sacramentType, new ArrayList<>());
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
     * Find conflicting events in Google Calendar for a given date and time range
     * @param eventDate The date to check
     * @param startTime Optional start time (null = all-day check)
     * @param endTime Optional end time (null = all-day check)
     * @return List of conflicts found in Google Calendar
     */
    public List<ConflictReport.ConflictInfo> findConflictsForDate(LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        List<ConflictReport.ConflictInfo> conflicts = new ArrayList<>();
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.debug("User is not authenticated with Google. Skipping Google Calendar conflict check.");
                return conflicts;
            }

            // Build query for events on the given date
            String query = buildCalendarQuery(eventDate, startTime, endTime);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            String url = GOOGLE_CALENDAR_API_URL + "?q=" + query + "&maxResults=100";
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String response = restTemplate.getForObject(url, String.class);
            parseCalendarResponse(response, conflicts, eventDate, startTime, endTime);

        } catch (Exception e) {
            log.error("Error checking Google Calendar conflicts", e);
        }
        return conflicts;
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
     * @param title Event title
     * @param date Event date
     * @param startTime Optional start time (null = all-day event)
     * @param endTime Optional end time (null = all-day event)
     * @param description Optional description
     * @param sacramentType Optional sacrament type
     * @param attendeeEmails Optional list of attendee emails
     */
    private String buildEventJson(String title, LocalDate date, LocalTime startTime, LocalTime endTime,
                                   String description, String sacramentType, List<String> attendeeEmails) {
        Map<String, Object> event = new HashMap<>();

        event.put("summary", title);
        event.put("description", buildDescription(description, sacramentType));

        // Get user's timezone preference
        String userTimezone = getUserTimezone();

        Map<String, Object> start = new HashMap<>();
        Map<String, Object> end = new HashMap<>();

        if (startTime == null || endTime == null) {
            // All-day event
            String dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String nextDayString = date.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            start.put("date", dateString);
            end.put("date", nextDayString);
        } else {
            // Timed event - use user's timezone
            ZoneId zoneId = ZoneId.of(userTimezone);
            LocalDateTime startDateTime = LocalDateTime.of(date, startTime);
            LocalDateTime endDateTime = LocalDateTime.of(date, endTime);
            ZonedDateTime startZoned = startDateTime.atZone(zoneId);
            ZonedDateTime endZoned = endDateTime.atZone(zoneId);

            start.put("dateTime", startZoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            start.put("timeZone", userTimezone);
            end.put("dateTime", endZoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            end.put("timeZone", userTimezone);
        }

        event.put("start", start);
        event.put("end", end);

        // Add attendees if provided
        if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
            List<Map<String, String>> attendees = new ArrayList<>();
            for (String email : attendeeEmails) {
                if (email != null && !email.trim().isEmpty()) {
                    Map<String, String> attendee = new HashMap<>();
                    attendee.put("email", email.trim());
                    attendees.add(attendee);
                }
            }
            if (!attendees.isEmpty()) {
                event.put("attendees", attendees);
            }
        }

        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Error building event JSON", e);
            return "{}";
        }
    }

    /**
     * Get the current user's timezone preference
     * Defaults to America/New_York if not set
     */
    private String getUserTimezone() {
        try {
            String username = getCurrentUsername();
            return userPreferencesRepository.findByUsername(username)
                    .map(prefs -> prefs.getTimezone())
                    .orElse("America/New_York");
        } catch (Exception e) {
            log.warn("Could not fetch user timezone, using default", e);
            return "America/New_York";
        }
    }

    /**
     * Get the current authenticated username
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OAuth2User) {
            OAuth2User oAuth2User = (OAuth2User) auth.getPrincipal();
            Object email = oAuth2User.getAttribute("email");
            return email != null ? email.toString() : oAuth2User.getName();
        }

        return auth != null ? auth.getName() : "anonymous";
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

    /**
     * Build a calendar query for finding events on a specific date
     */
    private String buildCalendarQuery(LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        // Simple query - just check events on the same date
        // Google Calendar API doesn't have a direct time range query, so we'll check date
        return eventDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Parse Google Calendar API response and extract conflicts
     */
    private void parseCalendarResponse(String response, List<ConflictReport.ConflictInfo> conflicts,
                                      LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        try {
            // Parse the response as JSON to extract events
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            Object items = responseMap.get("items");

            if (items instanceof List) {
                List<?> eventList = (List<?>) items;
                for (Object item : eventList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> eventMap = (Map<String, Object>) item;

                        // Extract event details
                        String summary = (String) eventMap.get("summary");
                        Map<String, Object> startMap = (Map<String, Object>) eventMap.get("start");
                        Map<String, Object> endMap = (Map<String, Object>) eventMap.get("end");

                        // Check if this event overlaps with the proposed time
                        if (summary != null && doesEventOverlap(startMap, endMap, eventDate, startTime, endTime)) {
                            ConflictReport.ConflictInfo conflict = new ConflictReport.ConflictInfo();
                            conflict.setEventTitle(summary);
                            conflict.setEventDate(eventDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
                            if (startMap != null && startMap.containsKey("dateTime")) {
                                conflict.setEventTime((String) startMap.get("dateTime"));
                            }
                            conflict.setSource("google_calendar");
                            conflicts.add(conflict);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Google Calendar response", e);
        }
    }

    /**
     * Check if a Google Calendar event overlaps with the proposed time
     */
    private boolean doesEventOverlap(Map<String, Object> startMap, Map<String, Object> endMap,
                                    LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        // If times are null (all-day event), any event on that date is a conflict
        if (startTime == null || endTime == null) {
            return true;
        }

        // Extract time from startMap and endMap if they contain dateTime
        // This is a simplified check - in production, you'd parse the dateTime properly
        if (startMap == null || endMap == null) {
            return true; // Assume conflict if we can't determine time
        }

        return true; // Conservative approach: flag as conflict to warn user
    }
}
