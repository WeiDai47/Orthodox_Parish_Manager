package com.example.orthodox_prm.service;

import com.example.orthodox_prm.dto.ConflictReport;
import com.example.orthodox_prm.model.EventParticipant;
import com.example.orthodox_prm.model.ScheduledEvent;
import com.example.orthodox_prm.repository.EventParticipantRepository;
import com.example.orthodox_prm.repository.ScheduledEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class ConflictDetectionService {

    private final EventParticipantRepository eventParticipantRepository;
    private final ScheduledEventRepository scheduledEventRepository;
    private final GoogleCalendarService googleCalendarService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public ConflictDetectionService(
            EventParticipantRepository eventParticipantRepository,
            ScheduledEventRepository scheduledEventRepository,
            GoogleCalendarService googleCalendarService) {
        this.eventParticipantRepository = eventParticipantRepository;
        this.scheduledEventRepository = scheduledEventRepository;
        this.googleCalendarService = googleCalendarService;
    }

    /**
     * Check for scheduling conflicts for a list of parishioners on a specific date/time
     * @param parishionerIds List of parishioner IDs to check
     * @param eventDate The date of the proposed event
     * @param startTime Start time (nullable for all-day events)
     * @param endTime End time (nullable for all-day events)
     * @return ConflictReport with all detected conflicts from both sources
     */
    public ConflictReport checkConflicts(List<Long> parishionerIds, LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        ConflictReport report = new ConflictReport();

        if (parishionerIds == null || parishionerIds.isEmpty()) {
            return report;
        }

        // Check database conflicts for each parishioner
        for (Long parishionerId : parishionerIds) {
            checkDatabaseConflicts(report, parishionerId, eventDate, startTime, endTime);
        }

        // Check Google Calendar conflicts if user is authenticated
        if (googleCalendarService.isGoogleOAuth2Authenticated()) {
            checkGoogleCalendarConflicts(report, eventDate, startTime, endTime);
        }

        return report;
    }

    /**
     * Check for conflicts in the application database
     */
    private void checkDatabaseConflicts(ConflictReport report, Long parishionerId, LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        try {
            List<EventParticipant> conflicts = eventParticipantRepository.findConflictingEvents(parishionerId, eventDate, startTime, endTime);

            for (EventParticipant participant : conflicts) {
                ScheduledEvent event = participant.getEvent();
                ConflictReport.ConflictInfo conflict = new ConflictReport.ConflictInfo();
                conflict.setParishionerName(participant.getParishioner().getFirstName() + " " + participant.getParishioner().getLastName());
                conflict.setEventTitle(event.getEventTitle());
                conflict.setEventDate(event.getEventDate().format(DATE_FORMATTER));
                if (!event.isAllDayEvent()) {
                    conflict.setEventTime(event.getStartTime().format(TIME_FORMATTER) + " - " + event.getEndTime().format(TIME_FORMATTER));
                }
                conflict.setSource("database");
                report.addDatabaseConflict(conflict);
            }
        } catch (Exception e) {
            log.error("Error checking database conflicts for parishioner {}", parishionerId, e);
        }
    }

    /**
     * Check for conflicts in Google Calendar
     */
    private void checkGoogleCalendarConflicts(ConflictReport report, LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        try {
            List<ConflictReport.ConflictInfo> googleConflicts = googleCalendarService.findConflictsForDate(eventDate, startTime, endTime);
            for (ConflictReport.ConflictInfo conflict : googleConflicts) {
                report.addGoogleCalendarConflict(conflict);
            }
        } catch (Exception e) {
            log.error("Error checking Google Calendar conflicts", e);
        }
    }
}
