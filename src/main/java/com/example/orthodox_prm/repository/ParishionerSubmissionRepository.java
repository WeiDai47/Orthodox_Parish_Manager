package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.Enum.SubmissionStatus;
import com.example.orthodox_prm.model.ParishionerSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParishionerSubmissionRepository extends JpaRepository<ParishionerSubmission, Long> {
    List<ParishionerSubmission> findByStatusOrderBySubmittedAtDesc(SubmissionStatus status);

    List<ParishionerSubmission> findBySubmissionLink_IdOrderBySubmittedAtDesc(Long linkId);

    long countByStatus(SubmissionStatus status);

    /**
     * Find approved submissions that reference a given submission ID as their pending spouse.
     * Used when approving a submission to check if any previously approved submission
     * is waiting to be linked to this one as spouse.
     */
    List<ParishionerSubmission> findByPendingSpouseSubmissionIdAndStatus(Long pendingSpouseSubmissionId, SubmissionStatus status);
}
