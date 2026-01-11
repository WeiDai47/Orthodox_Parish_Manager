package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.model.SentEmail;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    // Find all emails sent by a specific user
    List<SentEmail> findBySentBy(String sentBy, Sort sort);

    // Find emails sent in a date range
    List<SentEmail> findBySentAtBetween(LocalDateTime start, LocalDateTime end, Sort sort);

    // Find failed emails
    List<SentEmail> findBySuccessFalse(Sort sort);

    // Find emails by send mode
    List<SentEmail> findBySendMode(String sendMode, Sort sort);
}
