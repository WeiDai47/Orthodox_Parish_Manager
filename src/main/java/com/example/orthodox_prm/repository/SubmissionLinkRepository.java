package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.model.SubmissionLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionLinkRepository extends JpaRepository<SubmissionLink, Long> {
    Optional<SubmissionLink> findByToken(String token);

    List<SubmissionLink> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<SubmissionLink> findByIsActiveTrueOrderByCreatedAtDesc();
}
