package com.example.orthodox_prm.service;

import com.example.orthodox_prm.model.SubmissionLink;
import com.example.orthodox_prm.repository.SubmissionLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SubmissionLinkService {

    @Autowired
    private SubmissionLinkRepository submissionLinkRepository;

    /**
     * Create a new submission link
     */
    @Transactional
    public SubmissionLink createLink(String createdBy, LocalDateTime expiresAt, String description) {
        SubmissionLink link = new SubmissionLink(createdBy, expiresAt, description);
        return submissionLinkRepository.save(link);
    }

    /**
     * Create a new submission link with max submissions limit
     */
    @Transactional
    public SubmissionLink createLink(String createdBy, LocalDateTime expiresAt, String description, Integer maxSubmissions) {
        SubmissionLink link = new SubmissionLink(createdBy, expiresAt, description, maxSubmissions);
        return submissionLinkRepository.save(link);
    }

    /**
     * Save an existing link (for updating submission count, etc.)
     */
    @Transactional
    public SubmissionLink saveLink(SubmissionLink link) {
        return submissionLinkRepository.save(link);
    }

    /**
     * Find a link by token
     */
    public Optional<SubmissionLink> findByToken(String token) {
        return submissionLinkRepository.findByToken(token);
    }

    /**
     * Validate that a link exists, is active, and is not expired
     */
    public boolean isValidLink(String token) {
        Optional<SubmissionLink> link = findByToken(token);
        return link.isPresent() && link.get().isValid();
    }

    /**
     * Increment access count for a link
     */
    @Transactional
    public void incrementAccessCount(Long linkId) {
        Optional<SubmissionLink> linkOpt = submissionLinkRepository.findById(linkId);
        if (linkOpt.isPresent()) {
            SubmissionLink link = linkOpt.get();
            link.setAccessCount(link.getAccessCount() + 1);
            submissionLinkRepository.save(link);
        }
    }

    /**
     * Deactivate a link
     */
    @Transactional
    public void deactivateLink(Long linkId) {
        Optional<SubmissionLink> linkOpt = submissionLinkRepository.findById(linkId);
        if (linkOpt.isPresent()) {
            SubmissionLink link = linkOpt.get();
            link.setIsActive(false);
            submissionLinkRepository.save(link);
        }
    }

    /**
     * Get all links created by a specific user (priest)
     */
    public List<SubmissionLink> getLinksByCreator(String createdBy) {
        return submissionLinkRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
    }

    /**
     * Get all active links (ordered by creation date descending)
     */
    public List<SubmissionLink> getAllActiveLinks() {
        return submissionLinkRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    /**
     * Get specific link by ID
     */
    public Optional<SubmissionLink> getLinkById(Long linkId) {
        return submissionLinkRepository.findById(linkId);
    }
}
