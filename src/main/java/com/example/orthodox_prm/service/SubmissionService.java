package com.example.orthodox_prm.service;

import com.example.orthodox_prm.Enum.SubmissionStatus;
import com.example.orthodox_prm.Enum.SubmissionType;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.ParishionerSubmission;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.repository.ParishionerSubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SubmissionService {

    @Autowired
    private ParishionerSubmissionRepository submissionRepository;

    @Autowired
    private ParishionerRepository parishionerRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    /**
     * Get all pending submissions
     */
    public List<ParishionerSubmission> getPendingSubmissions() {
        return submissionRepository.findByStatusOrderBySubmittedAtDesc(SubmissionStatus.PENDING);
    }

    /**
     * Get submissions filtered by status
     */
    public List<ParishionerSubmission> getSubmissionsByStatus(SubmissionStatus status) {
        return submissionRepository.findByStatusOrderBySubmittedAtDesc(status);
    }

    /**
     * Get all submissions for a specific link
     */
    public List<ParishionerSubmission> getSubmissionsForLink(Long linkId) {
        return submissionRepository.findBySubmissionLink_IdOrderBySubmittedAtDesc(linkId);
    }

    /**
     * Get count of pending submissions
     */
    public long getPendingSubmissionCount() {
        return submissionRepository.countByStatus(SubmissionStatus.PENDING);
    }

    /**
     * Get a specific submission by ID
     */
    public Optional<ParishionerSubmission> getSubmissionById(Long id) {
        return submissionRepository.findById(id);
    }

    /**
     * Validate submission data - check required fields based on submission type and Orthodox flag
     */
    public List<String> validateSubmission(ParishionerSubmission submission) {
        List<String> errors = new ArrayList<>();

        // Check required basic fields
        if (submission.getFirstName() == null || submission.getFirstName().trim().isEmpty()) {
            errors.add("First name is required");
        }
        if (submission.getLastName() == null || submission.getLastName().trim().isEmpty()) {
            errors.add("Last name is required");
        }

        // For UPDATE submissions, require target parishioner
        if (submission.getSubmissionType() == SubmissionType.UPDATE && submission.getTargetParishioner() == null) {
            errors.add("Target parishioner is required for update submissions");
        }

        // Validate email if provided
        if (submission.getEmail() != null && !submission.getEmail().isEmpty()) {
            if (!submission.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                errors.add("Invalid email format");
            }
        }

        return errors;
    }

    /**
     * Approve a NEW submission - creates a new Parishioner record
     */
    @Transactional
    public Parishioner approveNewSubmission(ParishionerSubmission submission, String reviewedBy) {
        if (submission.getSubmissionType() != SubmissionType.NEW) {
            throw new IllegalArgumentException("Only NEW submissions can use approveNewSubmission");
        }

        // Create household if address provided
        Household household = null;
        if ((submission.getAddress() != null && !submission.getAddress().isEmpty()) ||
            (submission.getCity() != null && !submission.getCity().isEmpty())) {
            household = new Household();
            household.setAddress(submission.getAddress());
            household.setCity(submission.getCity());
            household = householdRepository.save(household);
        }

        // Create main parishioner
        Parishioner parishioner = new Parishioner();
        parishioner.setFirstName(submission.getFirstName().trim());
        parishioner.setLastName(submission.getLastName().trim());
        parishioner.setNameSuffix(submission.getNameSuffix());
        parishioner.setBirthday(submission.getBirthday());
        parishioner.setEmail(submission.getEmail() != null ? submission.getEmail().trim() : null);
        parishioner.setPhoneNumber(submission.getPhoneNumber());
        parishioner.setStatus(submission.getMembershipStatus());
        parishioner.setMaritalStatus(submission.getMaritalStatus());
        parishioner.setMarriageDate(submission.getMarriageDate());

        // Orthodox fields
        if (Boolean.TRUE.equals(submission.getIsOrthodox())) {
            parishioner.setBaptismalName(submission.getBaptismalName());
            parishioner.setPatronSaint(submission.getPatronSaint());
            parishioner.setBaptismDate(submission.getBaptismDate());
            parishioner.setChrismationDate(submission.getChrismationDate());
        }

        // Household
        if (household != null) {
            parishioner.setHousehold(household);
        }

        // Manual relationship fields
        parishioner.setManualSpouseName(submission.getManualSpouseName());
        parishioner.setManualGodfatherName(submission.getManualGodfatherName());
        parishioner.setManualGodmotherName(submission.getManualGodmotherName());
        parishioner.setManualSponsorName(submission.getManualSponsorName());

        // Save main parishioner
        parishioner = parishionerRepository.save(parishioner);

        // Create spouse if spouse details provided
        if ((submission.getSpouseFirstName() != null && !submission.getSpouseFirstName().isEmpty()) ||
            (submission.getSpouseLastName() != null && !submission.getSpouseLastName().isEmpty())) {
            Parishioner spouse = new Parishioner();
            spouse.setFirstName(submission.getSpouseFirstName() != null ? submission.getSpouseFirstName().trim() : "");
            spouse.setLastName(submission.getSpouseLastName() != null ? submission.getSpouseLastName().trim() : "");
            spouse.setEmail(submission.getSpouseEmail() != null ? submission.getSpouseEmail().trim() : null);
            spouse.setPhoneNumber(submission.getSpousePhoneNumber());
            spouse.setHousehold(household);
            spouse.setMaritalStatus(submission.getMaritalStatus());

            spouse = parishionerRepository.save(spouse);

            // Create bidirectional spouse relationship
            parishioner.setSpouse(spouse);
            spouse.setSpouse(parishioner);
            parishionerRepository.save(parishioner);
            parishionerRepository.save(spouse);
        }

        // Create children if provided
        List<ParishionerSubmission.ChildData> children = submission.getChildrenList();
        if (!children.isEmpty()) {
            for (ParishionerSubmission.ChildData childData : children) {
                if (childData.getName() != null && !childData.getName().isEmpty()) {
                    Parishioner child = new Parishioner();
                    String[] names = childData.getName().split(" ", 2);
                    child.setFirstName(names[0].trim());
                    child.setLastName(names.length > 1 ? names[1].trim() : "");
                    child.setBirthday(childData.getBirthday());
                    child.setHousehold(household);

                    parishionerRepository.save(child);
                }
            }
        }

        // Update submission status
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedBy(reviewedBy);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setTargetParishioner(parishioner);
        submissionRepository.save(submission);

        return parishioner;
    }

    /**
     * Approve an UPDATE submission - updates an existing Parishioner record
     */
    @Transactional
    public Parishioner approveUpdateSubmission(ParishionerSubmission submission, String reviewedBy) {
        if (submission.getSubmissionType() != SubmissionType.UPDATE) {
            throw new IllegalArgumentException("Only UPDATE submissions can use approveUpdateSubmission");
        }

        Parishioner parishioner = submission.getTargetParishioner();
        if (parishioner == null) {
            throw new IllegalArgumentException("Target parishioner not set for update submission");
        }

        // Update basic fields
        if (submission.getFirstName() != null && !submission.getFirstName().isEmpty()) {
            parishioner.setFirstName(submission.getFirstName().trim());
        }
        if (submission.getLastName() != null && !submission.getLastName().isEmpty()) {
            parishioner.setLastName(submission.getLastName().trim());
        }
        if (submission.getNameSuffix() != null) {
            parishioner.setNameSuffix(submission.getNameSuffix());
        }
        if (submission.getBirthday() != null) {
            parishioner.setBirthday(submission.getBirthday());
        }
        if (submission.getEmail() != null) {
            parishioner.setEmail(submission.getEmail().trim());
        }
        if (submission.getPhoneNumber() != null) {
            parishioner.setPhoneNumber(submission.getPhoneNumber());
        }
        if (submission.getMembershipStatus() != null) {
            parishioner.setStatus(submission.getMembershipStatus());
        }
        if (submission.getMaritalStatus() != null) {
            parishioner.setMaritalStatus(submission.getMaritalStatus());
        }
        if (submission.getMarriageDate() != null) {
            parishioner.setMarriageDate(submission.getMarriageDate());
        }

        // Update Orthodox fields
        if (Boolean.TRUE.equals(submission.getIsOrthodox())) {
            if (submission.getBaptismalName() != null) {
                parishioner.setBaptismalName(submission.getBaptismalName());
            }
            if (submission.getPatronSaint() != null) {
                parishioner.setPatronSaint(submission.getPatronSaint());
            }
            if (submission.getBaptismDate() != null) {
                parishioner.setBaptismDate(submission.getBaptismDate());
            }
            if (submission.getChrismationDate() != null) {
                parishioner.setChrismationDate(submission.getChrismationDate());
            }
        }

        // Update relationship manual fields
        if (submission.getManualSpouseName() != null) {
            parishioner.setManualSpouseName(submission.getManualSpouseName());
        }
        if (submission.getManualGodfatherName() != null) {
            parishioner.setManualGodfatherName(submission.getManualGodfatherName());
        }
        if (submission.getManualGodmotherName() != null) {
            parishioner.setManualGodmotherName(submission.getManualGodmotherName());
        }
        if (submission.getManualSponsorName() != null) {
            parishioner.setManualSponsorName(submission.getManualSponsorName());
        }

        // Save updated parishioner
        parishioner = parishionerRepository.save(parishioner);

        // Update submission status
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedBy(reviewedBy);
        submission.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        return parishioner;
    }

    /**
     * Reject a submission
     */
    @Transactional
    public void rejectSubmission(Long submissionId, String rejectionNotes, String reviewedBy) {
        Optional<ParishionerSubmission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isPresent()) {
            ParishionerSubmission submission = submissionOpt.get();
            submission.setStatus(SubmissionStatus.REJECTED);
            submission.setReviewNotes(rejectionNotes);
            submission.setReviewedBy(reviewedBy);
            submission.setReviewedAt(LocalDateTime.now());
            submissionRepository.save(submission);
        }
    }

    /**
     * Search for a parishioner by name to match with UPDATE submissions
     */
    public List<Parishioner> searchParishionersByName(String firstName, String lastName) {
        return parishionerRepository.findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(
            firstName, lastName
        );
    }

    /**
     * Save a submission to the database
     */
    @Transactional
    public ParishionerSubmission saveSubmission(ParishionerSubmission submission) {
        return submissionRepository.save(submission);
    }
}
