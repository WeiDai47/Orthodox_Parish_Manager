package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.SacramentType;
import com.example.orthodox_prm.model.*;
import com.example.orthodox_prm.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/parishioners/view")
public class ViewParishionerController {

    private final ParishionerRepository parishionerRepository;
    private final NoteRepository noteRepository;
    private final ScheduledEventRepository scheduledEventRepository;

    public ViewParishionerController(
            ParishionerRepository parishionerRepository,
            NoteRepository noteRepository,
            ScheduledEventRepository scheduledEventRepository) {
        this.parishionerRepository = parishionerRepository;
        this.noteRepository = noteRepository;
        this.scheduledEventRepository = scheduledEventRepository;
    }

    @GetMapping("/{id}")
    public String viewParishioner(@PathVariable Long id, Model model) {
        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        List<Note> parishionerNotes = noteRepository.findByParishioner_IdOrderByCreatedAtDesc(id);

        List<Note> householdNotes = List.of();
        if (p.getHousehold() != null) {
            householdNotes = noteRepository.findByHousehold_IdOrderByCreatedAtDesc(p.getHousehold().getId());
        }

        List<ScheduledEvent> allEvents = scheduledEventRepository.findByParishioner_IdOrderByEventDateAsc(id);

        List<ScheduledEvent> sacraments = allEvents.stream()
                .filter(e -> e.getSacramentType() != null)
                .toList();
        List<ScheduledEvent> regularEvents = allEvents.stream()
                .filter(e -> e.getSacramentType() == null)
                .toList();

        model.addAttribute("parishioner", p);
        model.addAttribute("parishionerNotes", parishionerNotes);
        model.addAttribute("householdNotes", householdNotes);
        model.addAttribute("sacraments", sacraments);
        model.addAttribute("regularEvents", regularEvents);
        model.addAttribute("allSacramentTypes", SacramentType.values());

        return "view-parishioner";
    }

    @PostMapping("/{id}/add-note")
    @Transactional
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
    public String addScheduledEvent(
            @PathVariable Long id,
            @RequestParam String eventTitle,
            @RequestParam LocalDate eventDate,
            @RequestParam(required = false) String eventDescription,
            @RequestParam(required = false) String sacramentType) {

        if (eventTitle == null || eventTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("Event title cannot be empty");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date is required");
        }

        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        ScheduledEvent event = new ScheduledEvent();
        event.setEventTitle(eventTitle.trim());
        event.setEventDate(eventDate);
        event.setEventDescription(eventDescription != null ? eventDescription.trim() : null);
        event.setParishioner(p);

        if (sacramentType != null && !sacramentType.isEmpty() && !sacramentType.equals("NONE")) {
            event.setSacramentType(SacramentType.valueOf(sacramentType));
        }

        scheduledEventRepository.save(event);

        return "redirect:/parishioners/view/" + id;
    }

    @GetMapping("/{parishionerId}/delete-note/{noteId}")
    @Transactional
    public String deleteNote(@PathVariable Long parishionerId, @PathVariable Long noteId) {
        noteRepository.deleteById(noteId);
        return "redirect:/parishioners/view/" + parishionerId;
    }

    @GetMapping("/{parishionerId}/delete-event/{eventId}")
    @Transactional
    public String deleteEvent(@PathVariable Long parishionerId, @PathVariable Long eventId) {
        scheduledEventRepository.deleteById(eventId);
        return "redirect:/parishioners/view/" + parishionerId;
    }
}
