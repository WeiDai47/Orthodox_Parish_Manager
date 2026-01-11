package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.Enum.SacramentType;
import com.example.orthodox_prm.model.ScheduledEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ScheduledEventRepository extends JpaRepository<ScheduledEvent, Long> {

    /**
     * Find all events for a parishioner via the EventParticipant join table
     */
    @Query("SELECT DISTINCT e FROM ScheduledEvent e " +
           "JOIN e.participants ep WHERE ep.parishioner.id = :parishionerId " +
           "ORDER BY e.eventDate ASC")
    List<ScheduledEvent> findByParticipantId(@Param("parishionerId") Long parishionerId);

    /**
     * Find all sacrament events for a parishioner
     */
    @Query("SELECT DISTINCT e FROM ScheduledEvent e " +
           "JOIN e.participants ep WHERE ep.parishioner.id = :parishionerId " +
           "AND e.sacramentType IS NOT NULL " +
           "ORDER BY e.eventDate ASC")
    List<ScheduledEvent> findSacramentsByParticipantId(@Param("parishionerId") Long parishionerId);

    /**
     * Find all regular (non-sacrament) events for a parishioner
     */
    @Query("SELECT DISTINCT e FROM ScheduledEvent e " +
           "JOIN e.participants ep WHERE ep.parishioner.id = :parishionerId " +
           "AND e.sacramentType IS NULL " +
           "ORDER BY e.eventDate ASC")
    List<ScheduledEvent> findRegularEventsByParticipantId(@Param("parishionerId") Long parishionerId);

    /**
     * Find upcoming events from today onwards
     */
    @Query("SELECT e FROM ScheduledEvent e WHERE e.eventDate >= :today ORDER BY e.eventDate ASC")
    List<ScheduledEvent> findUpcomingEvents(@Param("today") LocalDate today);

    /**
     * Find upcoming sacraments from today onwards
     */
    @Query("SELECT e FROM ScheduledEvent e WHERE e.eventDate >= :today AND e.sacramentType IS NOT NULL ORDER BY e.eventDate ASC")
    List<ScheduledEvent> findUpcomingSacraments(@Param("today") LocalDate today);

    /**
     * Find events on a specific date with optional time overlap detection
     */
    @Query("SELECT e FROM ScheduledEvent e WHERE e.eventDate = :eventDate " +
           "AND (" +
           "  :startTime IS NULL OR :endTime IS NULL OR " +
           "  e.startTime IS NULL OR e.endTime IS NULL OR " +
           "  (e.startTime < :endTime AND e.endTime > :startTime)" +
           ") " +
           "ORDER BY e.startTime ASC")
    List<ScheduledEvent> findEventsOnDateWithTimeOverlap(
            @Param("eventDate") LocalDate eventDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);
}
