package com.example.orthodox_prm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a Google Calendar event from API responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleCalendarEvent {
    @JsonProperty("id")
    private String id;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("description")
    private String description;

    @JsonProperty("start")
    private EventDateTime start;

    @JsonProperty("end")
    private EventDateTime end;

    @JsonProperty("attendees")
    private Attendee[] attendees;

    /**
     * Inner class for start/end DateTime or Date
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventDateTime {
        @JsonProperty("dateTime")
        private LocalDateTime dateTime;

        @JsonProperty("date")
        private LocalDate date;

        @JsonProperty("timeZone")
        private String timeZone;

        public boolean isAllDay() {
            return date != null && dateTime == null;
        }
    }

    /**
     * Inner class for attendee information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attendee {
        @JsonProperty("email")
        private String email;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("responseStatus")
        private String responseStatus;
    }
}
