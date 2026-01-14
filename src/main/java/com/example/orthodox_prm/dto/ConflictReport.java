package com.example.orthodox_prm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds conflict detection results from both database and Google Calendar
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictReport {
    private List<ConflictInfo> databaseConflicts = new ArrayList<>();
    private List<ConflictInfo> googleCalendarConflicts = new ArrayList<>();

    public boolean hasConflicts() {
        return !databaseConflicts.isEmpty() || !googleCalendarConflicts.isEmpty();
    }

    public void addDatabaseConflict(ConflictInfo conflict) {
        if (conflict != null) {
            databaseConflicts.add(conflict);
        }
    }

    public void addGoogleCalendarConflict(ConflictInfo conflict) {
        if (conflict != null) {
            googleCalendarConflicts.add(conflict);
        }
    }

    public int getTotalConflictCount() {
        return databaseConflicts.size() + googleCalendarConflicts.size();
    }

    /**
     * Represents a single conflict (event or time slot collision)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictInfo {
        private String parishionerName;
        private String eventTitle;
        private String eventDate;
        private String eventTime; // "time" for timed events, null for all-day
        private String source; // "database" or "google_calendar"
    }
}
