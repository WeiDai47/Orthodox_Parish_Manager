package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.model.EventParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    /**
     * Find all events for a parishioner, ordered by event date ascending
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.parishioner.id = :parishionerId ORDER BY ep.event.eventDate ASC")
    List<EventParticipant> findByParishioner_IdOrderByEvent_EventDateAsc(@Param("parishionerId") Long parishionerId);

    /**
     * Find all participants of a specific event
     */
    List<EventParticipant> findByEvent_Id(Long eventId);

    /**
     * Find conflicting events for a parishioner on a given date with optional time overlap detection
     * Returns events on the same date, and if times are specified, only events that overlap
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.parishioner.id = :parishionerId " +
           "AND ep.event.eventDate = :eventDate " +
           "AND (" +
           "  :startTime IS NULL OR :endTime IS NULL OR " +
           "  ep.event.startTime IS NULL OR ep.event.endTime IS NULL OR " +
           "  (ep.event.startTime < :endTime AND ep.event.endTime > :startTime)" +
           ") " +
           "ORDER BY ep.event.eventDate ASC, ep.event.startTime ASC")
    List<EventParticipant> findConflictingEvents(
            @Param("parishionerId") Long parishionerId,
            @Param("eventDate") LocalDate eventDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);
}
