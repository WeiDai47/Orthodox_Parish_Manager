package com.example.orthodox_prm.model;

import com.example.orthodox_prm.Enum.SacramentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_title", nullable = false)
    private String eventTitle;

    @Column(name = "event_description", columnDefinition = "TEXT")
    private String eventDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "sacrament_type")
    private SacramentType sacramentType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<EventParticipant> participants = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper Methods

    /**
     * Adds a parishioner as a participant to this event
     */
    public void addParticipant(Parishioner parishioner) {
        if (parishioner != null && !participants.stream()
                .anyMatch(p -> p.getParishioner().getId().equals(parishioner.getId()))) {
            EventParticipant participant = new EventParticipant();
            participant.setEvent(this);
            participant.setParishioner(parishioner);
            participants.add(participant);
        }
    }

    /**
     * Returns true if this is an all-day event (no start/end times specified)
     */
    public boolean isAllDayEvent() {
        return startTime == null && endTime == null;
    }

    /**
     * Gets the list of parishioners participating in this event
     */
    public List<Parishioner> getParishioners() {
        return participants.stream()
                .map(EventParticipant::getParishioner)
                .collect(Collectors.toList());
    }
}
