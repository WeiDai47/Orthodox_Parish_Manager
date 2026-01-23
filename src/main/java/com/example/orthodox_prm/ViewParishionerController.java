package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.SacramentType;
import com.example.orthodox_prm.dto.ConflictReport;
import com.example.orthodox_prm.model.*;
import com.example.orthodox_prm.repository.*;
import com.example.orthodox_prm.service.ConflictDetectionService;
import com.example.orthodox_prm.service.GoogleCalendarService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/parishioners/view")
public class ViewParishionerController {

    private final ParishionerRepository parishionerRepository;
    private final NoteRepository noteRepository;
    private final ScheduledEventRepository scheduledEventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final GoogleCalendarService googleCalendarService;
    private final ConflictDetectionService conflictDetectionService;
    private final ObjectMapper objectMapper;

    public ViewParishionerController(
            ParishionerRepository parishionerRepository,
            NoteRepository noteRepository,
            ScheduledEventRepository scheduledEventRepository,
            EventParticipantRepository eventParticipantRepository,
            GoogleCalendarService googleCalendarService,
            ConflictDetectionService conflictDetectionService,
            ObjectMapper objectMapper) {
        this.parishionerRepository = parishionerRepository;
        this.noteRepository = noteRepository;
        this.scheduledEventRepository = scheduledEventRepository;
        this.eventParticipantRepository = eventParticipantRepository;
        this.googleCalendarService = googleCalendarService;
        this.conflictDetectionService = conflictDetectionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRIEST','SECRETARY','VIEWER')")
    public String viewParishioner(@PathVariable Long id, Model model) {
        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        List<Note> parishionerNotes = noteRepository.findByParishioner_IdOrderByCreatedAtDesc(id);

        List<Note> householdNotes = List.of();
        if (p.getHousehold() != null) {
            householdNotes = noteRepository.findByHousehold_IdOrderByCreatedAtDesc(p.getHousehold().getId());
        }

        List<ScheduledEvent> sacraments = scheduledEventRepository.findSacramentsByParticipantId(id);
        List<ScheduledEvent> regularEvents = scheduledEventRepository.findRegularEventsByParticipantId(id);

        // Get all parishioners for participant selector (exclude current parishioner)
        List<Parishioner> allParishioners = parishionerRepository.findAll();
        allParishioners.removeIf(parishioner -> parishioner.getId().equals(id));

        model.addAttribute("parishioner", p);
        model.addAttribute("parishionerNotes", parishionerNotes);
        model.addAttribute("householdNotes", householdNotes);
        model.addAttribute("sacraments", sacraments);
        model.addAttribute("regularEvents", regularEvents);
        model.addAttribute("allSacramentTypes", SacramentType.values());
        model.addAttribute("allParishioners", allParishioners);
        model.addAttribute("isGoogleAuthenticated", googleCalendarService.isGoogleOAuth2Authenticated());

        return "view-parishioner";
    }

    @PostMapping("/{id}/add-note")
    @Transactional
    @PreAuthorize("hasAnyRole('PRIEST','SECRETARY')")
    public String addParishionerNote(
            @PathVariable Long id,
            @RequestParam String noteText) {

        if (noteText == null || noteText.trim().isEmpty()) {
            throw new IllegalArgumentException("Note text cannot be empty");
        }

        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        Note note = new Note();
        note.setNoteText(noteText.trim());
        note.setParishioner(p);
        noteRepository.save(note);

        return "redirect:/parishioners/view/" + id;
    }

    @PostMapping("/{id}/add-household-note")
    @Transactional
    @PreAuthorize("hasAnyRole('PRIEST','SECRETARY')")
    public String addHouseholdNote(
            @PathVariable Long id,
            @RequestParam String noteText) {

        if (noteText == null || noteText.trim().isEmpty()) {
            throw new IllegalArgumentException("Note text cannot be empty");
        }

        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        if (p.getHousehold() == null) {
            throw new IllegalArgumentException("Parishioner has no household");
        }

        Note note = new Note();
        note.setNoteText(noteText.trim());
        note.setHousehold(p.getHousehold());
        noteRepository.save(note);

        return "redirect:/parishioners/view/" + id;
    }

    @PostMapping("/{id}/add-event")
    @Transactional
    @PreAuthorize("hasAnyRole('PRIEST','SECRETARY')")
    public String addScheduledEvent(
            @PathVariable Long id,
            @RequestParam String eventTitle,
            @RequestParam LocalDate eventDate,
            @RequestParam(required = false) String eventDescription,
            @RequestParam(required = false) String sacramentType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String additionalParticipants,
            @RequestParam(required = false) String syncToGoogle) {

        if (eventTitle == null || eventTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Event title cannot be empty");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date is required");
        }

        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        // Parse times if provided
        LocalTime parsedStartTime = null;
        LocalTime parsedEndTime = null;
        if (startTime != null && !startTime.isEmpty()) {
            parsedStartTime = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (endTime != null && !endTime.isEmpty()) {
            parsedEndTime = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"));
        }

        // Build list of participants
        List<Long> participantIds = new ArrayList<>();
        participantIds.add(id); // Primary parishioner
        if (additionalParticipants != null && !additionalParticipants.isEmpty()) {
            String[] ids = additionalParticipants.split(",");
            for (String participantId : ids) {
                try {
                    long pId = Long.parseLong(participantId.trim());
                    if (!participantIds.contains(pId)) {
                        participantIds.add(pId);
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid IDs
                }
            }
        }

        // Create event
        ScheduledEvent event = new ScheduledEvent();
        event.setEventTitle(eventTitle.trim());
        event.setEventDate(eventDate);
        event.setEventDescription(eventDescription != null ? eventDescription.trim() : null);
        event.setStartTime(parsedStartTime);
        event.setEndTime(parsedEndTime);

        if (sacramentType != null && !sacramentType.isEmpty() && !sacramentType.equals("NONE")) {
            event.setSacramentType(SacramentType.valueOf(sacramentType));
        }

        // Add all participants
        for (Long participantId : participantIds) {
            Parishioner participant = parishionerRepository.findById(participantId)
                    .orElse(null);
            if (participant != null) {
                event.addParticipant(participant);
            }
        }

        scheduledEventRepository.save(event);

        // Sync to Google Calendar if enabled and user is authenticated
        if (syncToGoogle != null && syncToGoogle.equals("true") && googleCalendarService.isGoogleOAuth2Authenticated()) {
            List<String> attendeeEmails = new ArrayList<>();
            for (Parishioner participant : event.getParishioners()) {
                // Note: Parishioner model doesn't have email yet, so this is placeholder
                // In future when email field is added, use: attendeeEmails.add(participant.getEmail());
            }
            googleCalendarService.createCalendarEvent(
                    eventTitle.trim(),
                    eventDate,
                    parsedStartTime,
                    parsedEndTime,
                    eventDescription != null ? eventDescription.trim() : null,
                    sacramentType,
                    attendeeEmails
            );
        }

        return "redirect:/parishioners/view/" + id;
    }

    /**
     * AJAX endpoint to check for scheduling conflicts
     */
    @PostMapping("/{id}/check-conflicts")
    @ResponseBody
    @PreAuthorize("hasAnyRole('PRIEST','SECRETARY')")
    public ResponseEntity<Map<String, Object>> checkEventConflicts(
            @PathVariable Long id,
            @RequestParam LocalDate eventDate,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String additionalParticipants) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Parse times if provided
            LocalTime parsedStartTime = null;
            LocalTime parsedEndTime = null;
            if (startTime != null && !startTime.isEmpty()) {
                parsedStartTime = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
            }
            if (endTime != null && !endTime.isEmpty()) {
                parsedEndTime = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"));
            }

            // Build list of participant IDs
            List<Long> participantIds = new ArrayList<>();
            participantIds.add(id);
            if (additionalParticipants != null && !additionalParticipants.isEmpty()) {
                String[] ids = additionalParticipants.split(",");
                for (String participantId : ids) {
                    try {
                        long pId = Long.parseLong(participantId.trim());
                        if (!participantIds.contains(pId)) {
                            participantIds.add(pId);
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid IDs
                    }
                }
            }

            // Check for conflicts
            ConflictReport report = conflictDetectionService.checkConflicts(participantIds, eventDate, parsedStartTime, parsedEndTime);

            response.put("hasConflicts", report.hasConflicts());
            response.put("conflictCount", report.getTotalConflictCount());
            response.put("databaseConflicts", report.getDatabaseConflicts());
            response.put("googleCalendarConflicts", report.getGoogleCalendarConflicts());

        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{parishionerId}/delete-note/{noteId}")
    @Transactional
    @PreAuthorize("hasRole('PRIEST')")
    public String deleteNote(@PathVariable Long parishionerId, @PathVariable Long noteId) {
        noteRepository.deleteById(noteId);
        return "redirect:/parishioners/view/" + parishionerId;
    }

    @PostMapping("/{parishionerId}/delete-event/{eventId}")
    @Transactional
    @PreAuthorize("hasRole('PRIEST')")
    public String deleteEvent(@PathVariable Long parishionerId, @PathVariable Long eventId) {
        scheduledEventRepository.deleteById(eventId);
        return "redirect:/parishioners/view/" + parishionerId;
    }
}
