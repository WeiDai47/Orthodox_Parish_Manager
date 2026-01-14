package com.example.orthodox_prm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents an upcoming event (sacrament, event, name day, baptism day, etc.)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingEvent implements Comparable<UpcomingEvent> {
    private String title;
    private String parishionerName;
    private LocalDate date;
    private LocalTime time; // null for all-day events
    private String type; // "NAME_DAY", "BAPTISM_DAY", "SACRAMENT", "EVENT"
    private String description;
    private Long eventId; // ID of the actual event (if applicable)
    private Long parishionerId; // ID of the parishioner

    @Override
    public int compareTo(UpcomingEvent other) {
        // Sort by date first
        int dateComparison = this.date.compareTo(other.date);
        if (dateComparison != 0) {
            return dateComparison;
        }

        // If same date, sort by time (all-day events first)
        if (this.time == null && other.time == null) {
            return 0;
        }
        if (this.time == null) {
            return -1; // All-day events come first
        }
        if (other.time == null) {
            return 1;
        }

        // Same date, both have times - sort by time
        return this.time.compareTo(other.time);
    }
}
