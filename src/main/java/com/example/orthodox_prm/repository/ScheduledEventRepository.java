package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.Enum.SacramentType;
import com.example.orthodox_prm.model.ScheduledEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduledEventRepository extends JpaRepository<ScheduledEvent, Long> {

    List<ScheduledEvent> findByParishioner_IdOrderByEventDateAsc(Long parishionerId);

    List<ScheduledEvent> findByParishioner_IdAndSacramentTypeIsNotNullOrderByEventDateAsc(Long parishionerId);

    List<ScheduledEvent> findByParishioner_IdAndSacramentTypeIsNullOrderByEventDateAsc(Long parishionerId);

    @Query("SELECT e FROM ScheduledEvent e WHERE e.eventDate >= :today ORDER BY e.eventDate ASC")
    List<ScheduledEvent> findUpcomingEvents(@Param("today") LocalDate today);

    @Query("SELECT e FROM ScheduledEvent e WHERE e.eventDate >= :today AND e.sacramentType IS NOT NULL ORDER BY e.eventDate ASC")
    List<ScheduledEvent> findUpcomingSacraments(@Param("today") LocalDate today);
}
