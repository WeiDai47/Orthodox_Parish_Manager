package com.example.orthodox_prm.service;

import com.example.orthodox_prm.Enum.SubmissionStatus;
import com.example.orthodox_prm.Enum.SubmissionType;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.ParishionerSubmission;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.repository.ParishionerSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionService.class);

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

        // For UPDATE submissions, priest will manually assign target parishioner during review
        // No validation required here

        // Validate email if provided
        if (submission.getEmail() != null && !submission.getEmail().isEmpty()) {
            if (!submission.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                errors.add("Invalid email format");
            }
        }

        return errors;
    }

    /**
     * Validate relationship assignments to prevent invalid configurations
     */
    private void validateRelationshipAssignments(Long parishionerId, Long selectedSpouseId,
            Long selectedGodfatherId, Long selectedGodmotherId, Long selectedSponsorId) {

        // Prevent self-assignment
        if (parishionerId != null) {
            if (selectedSpouseId != null && selectedSpouseId.equals(parishionerId)) {
                throw new IllegalArgumentException("Cannot assign a person as their own spouse");
            }
            if (selectedGodfatherId != null && selectedGodfatherId.equals(parishionerId)) {
                throw new IllegalArgumentException("Cannot assign a person as their own godfather");
            }
            if (selectedGodmotherId != null && selectedGodmotherId.equals(parishionerId)) {
                throw new IllegalArgumentException("Cannot assign a person as their own godmother");
            }
            if (selectedSponsorId != null && selectedSponsorId.equals(parishionerId)) {
                throw new IllegalArgumentException("Cannot assign a person as their own sponsor");
            }
        }

        // Check if selected spouse is already married to someone else
        if (selectedSpouseId != null && selectedSpouseId > 0) {
            Optional<Parishioner> spouseOpt = parishionerRepository.findById(selectedSpouseId);
            if (spouseOpt.isPresent()) {
                Parishioner spouse = spouseOpt.get();
                if (spouse.getSpouse() != null && !spouse.getSpouse().getId().equals(parishionerId)) {
                    throw new IllegalArgumentException("Selected spouse (" + spouse.getFirstName() + " " +
                        spouse.getLastName() + ") is already married to " +
                        spouse.getSpouse().getFirstName() + " " + spouse.getSpouse().getLastName());
                }
            }
        }
    }

    /**
     * Validate date fields for logical consistency
     */
    private void validateDates(ParishionerSubmission submission) {
        LocalDate today = LocalDate.now();

        // Birthday cannot be in the future
        if (submission.getBirthday() != null && submission.getBirthday().isAfter(today)) {
            throw new IllegalArgumentException("Birthday cannot be in the future");
        }

        // Marriage date cannot be before birthday
        if (submission.getMarriageDate() != null && submission.getBirthday() != null) {
            if (submission.getMarriageDate().isBefore(submission.getBirthday())) {
                throw new IllegalArgumentException("Marriage date cannot be before birth date");
            }
        }

        // Marriage date cannot be in the future
        if (submission.getMarriageDate() != null && submission.getMarriageDate().isAfter(today)) {
            throw new IllegalArgumentException("Marriage date cannot be in the future");
        }

        // Baptism date cannot be before birthday
        if (submission.getBaptismDate() != null && submission.getBirthday() != null) {
            if (submission.getBaptismDate().isBefore(submission.getBirthday())) {
                throw new IllegalArgumentException("Baptism date cannot be before birth date");
            }
        }

        // Chrismation cannot be before baptism
        if (submission.getChrismationDate() != null && submission.getBaptismDate() != null) {
            if (submission.getChrismationDate().isBefore(submission.getBaptismDate())) {
                throw new IllegalArgumentException("Chrismation date cannot be before baptism date");
            }
        }
    }

    /**
     * Approve a NEW submission - creates a new Parishioner record
     */
    @Transactional
    public Parishioner approveNewSubmission(
            ParishionerSubmission submission,
            String reviewedBy,
            Long selectedSpouseId,
            Long selectedGodfatherId,
            Long selectedGodmotherId,
            Long selectedSponsorId,
            Long selectedHouseholdId,
            String selectedNewHouseholdName,
            List<Long> childLinkIds,
            List<Integer> childCreateIndexes,
            Long pendingSpouseSubmissionId) {

        logger.info("Starting approval of NEW submission id={} by {}", submission.getId(), reviewedBy);

        if (submission.getSubmissionType() != SubmissionType.NEW) {
            throw new IllegalArgumentException("Only NEW submissions can use approveNewSubmission");
        }

        // Check submission status to prevent concurrent approvals
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new IllegalArgumentException("Submission has already been processed (status: " + submission.getStatus() + ")");
        }

        // Validate dates
        validateDates(submission);

        // Household assignment logic - only create if no existing household selected
        Household household = null;
        if (selectedHouseholdId != null && selectedHouseholdId > 0) {
            // Use existing household - don't create new one
            Optional<Household> householdOpt = householdRepository.findById(selectedHouseholdId);
            if (householdOpt.isPresent()) {
                household = householdOpt.get();
                logger.info("Using existing household id={}", selectedHouseholdId);
            } else {
                logger.warn("Selected household id={} not found, will create from address", selectedHouseholdId);
            }
        } else if (selectedNewHouseholdName != null && !selectedNewHouseholdName.trim().isEmpty()) {
            // Create new household with specified name
            household = new Household();
            household.setFamilyName(selectedNewHouseholdName.trim());
            household.setAddress(submission.getAddress());
            household.setCity(submission.getCity());
            household.setZipCode(submission.getZipCode());
            household = householdRepository.save(household);
            logger.info("Created new household '{}' with id={}", selectedNewHouseholdName.trim(), household.getId());
        } else if ((submission.getAddress() != null && !submission.getAddress().trim().isEmpty()) ||
                   (submission.getCity() != null && !submission.getCity().trim().isEmpty()) ||
                   (submission.getZipCode() != null && !submission.getZipCode().trim().isEmpty())) {
            // Create household from address only if no other household option chosen
            household = new Household();
            household.setAddress(submission.getAddress());
            household.setCity(submission.getCity());
            household.setZipCode(submission.getZipCode());
            household = householdRepository.save(household);
            logger.info("Created household from address with id={}", household.getId());
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
        logger.info("Created parishioner id={} name={} {}", parishioner.getId(), parishioner.getFirstName(), parishioner.getLastName());

        // Validate relationship assignments (self-assignment and marriage conflicts)
        validateRelationshipAssignments(parishioner.getId(), selectedSpouseId, selectedGodfatherId, selectedGodmotherId, selectedSponsorId);

        // Apply relationship assignments
        if (selectedSpouseId != null && selectedSpouseId > 0) {
            Optional<Parishioner> spouseOpt = parishionerRepository.findById(selectedSpouseId);
            if (spouseOpt.isPresent()) {
                parishioner.marry(spouseOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(spouseOpt.get());
                logger.info("Assigned spouse id={} to parishioner id={}", selectedSpouseId, parishioner.getId());
            }
        }

        // Handle pending spouse submission linking
        if (pendingSpouseSubmissionId != null && pendingSpouseSubmissionId > 0) {
            // Store the reference on this submission for when the other is approved
            submission.setPendingSpouseSubmissionId(pendingSpouseSubmissionId);
            logger.info("Set pending spouse submission id={} for submission id={}", pendingSpouseSubmissionId, submission.getId());

            // Check if the referenced submission is already approved
            Optional<ParishionerSubmission> pendingSpouseSubmissionOpt = submissionRepository.findById(pendingSpouseSubmissionId);
            if (pendingSpouseSubmissionOpt.isPresent()) {
                ParishionerSubmission pendingSpouseSubmission = pendingSpouseSubmissionOpt.get();
                if (pendingSpouseSubmission.getStatus() == SubmissionStatus.APPROVED &&
                    pendingSpouseSubmission.getTargetParishioner() != null) {
                    // The other spouse was already approved - create the link now
                    Parishioner otherSpouse = pendingSpouseSubmission.getTargetParishioner();
                    parishioner.marry(otherSpouse);
                    parishionerRepository.save(parishioner);
                    parishionerRepository.save(otherSpouse);
                    logger.info("Linked to already-approved spouse parishioner id={} from submission id={}",
                        otherSpouse.getId(), pendingSpouseSubmissionId);
                }
            }
        }

        // Check if any previously approved submission was waiting to link to THIS submission as spouse
        List<ParishionerSubmission> waitingSubmissions = submissionRepository
            .findByPendingSpouseSubmissionIdAndStatus(submission.getId(), SubmissionStatus.APPROVED);
        for (ParishionerSubmission waitingSubmission : waitingSubmissions) {
            if (waitingSubmission.getTargetParishioner() != null && parishioner.getSpouse() == null) {
                Parishioner waitingParishioner = waitingSubmission.getTargetParishioner();
                parishioner.marry(waitingParishioner);
                parishionerRepository.save(parishioner);
                parishionerRepository.save(waitingParishioner);
                logger.info("Linked parishioner id={} to waiting spouse parishioner id={} (from submission id={})",
                    parishioner.getId(), waitingParishioner.getId(), waitingSubmission.getId());
                break; // Only link to first waiting spouse (shouldn't be multiple)
            }
        }

        if (selectedGodfatherId != null && selectedGodfatherId > 0) {
            Optional<Parishioner> godfatherOpt = parishionerRepository.findById(selectedGodfatherId);
            if (godfatherOpt.isPresent()) {
                parishioner.assignGodfather(godfatherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godfatherOpt.get());
                logger.info("Assigned godfather id={} to parishioner id={}", selectedGodfatherId, parishioner.getId());
            }
        }

        if (selectedGodmotherId != null && selectedGodmotherId > 0) {
            Optional<Parishioner> godmotherOpt = parishionerRepository.findById(selectedGodmotherId);
            if (godmotherOpt.isPresent()) {
                parishioner.assignGodmother(godmotherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godmotherOpt.get());
                logger.info("Assigned godmother id={} to parishioner id={}", selectedGodmotherId, parishioner.getId());
            }
        }

        if (selectedSponsorId != null && selectedSponsorId > 0) {
            Optional<Parishioner> sponsorOpt = parishionerRepository.findById(selectedSponsorId);
            if (sponsorOpt.isPresent()) {
                parishioner.setWeddingSponsor(sponsorOpt.get());
                parishionerRepository.save(parishioner);
                logger.info("Assigned sponsor id={} to parishioner id={}", selectedSponsorId, parishioner.getId());
            }
        }

        // Household already assigned above, just update parishioner reference if needed
        if (household != null && parishioner.getHousehold() == null) {
            parishioner.setHousehold(household);
            parishionerRepository.save(parishioner);
        }

        // Create spouse if spouse details provided (from submission form data, not linked relationship)
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

            // Create bidirectional spouse relationship only if no spouse was already linked
            if (selectedSpouseId == null || selectedSpouseId <= 0) {
                parishioner.setSpouse(spouse);
                spouse.setSpouse(parishioner);
                parishionerRepository.save(parishioner);
                parishionerRepository.save(spouse);
            }
        }

        // Handle children - REPLACE existing children creation logic
        List<ParishionerSubmission.ChildData> children = submission.getChildrenList();
        if (!children.isEmpty() && (childLinkIds != null || childCreateIndexes != null)) {
            logger.info("Processing {} children from submission", children.size());
            for (int i = 0; i < children.size(); i++) {
                ParishionerSubmission.ChildData childData = children.get(i);

                // Check if this child should be linked to existing parishioner
                if (childLinkIds != null && i < childLinkIds.size() && childLinkIds.get(i) != null) {
                    Long childLinkId = childLinkIds.get(i);
                    Optional<Parishioner> existingChild = parishionerRepository.findById(childLinkId);
                    if (existingChild.isPresent()) {
                        Parishioner child = existingChild.get();
                        if (household != null) {
                            child.setHousehold(household);
                            parishionerRepository.save(child);
                            logger.info("Linked existing child id={} to household id={}", childLinkId, household.getId());
                        }
                    } else {
                        logger.warn("Child link id={} not found, skipping", childLinkId);
                    }
                }
                // Check if this child should be created
                else if (childCreateIndexes != null && childCreateIndexes.contains(i)) {
                    if (childData.getName() != null && !childData.getName().trim().isEmpty()) {
                        Parishioner child = new Parishioner();
                        String trimmedName = childData.getName().trim();
                        String[] names = trimmedName.split("\\s+", 2);
                        String firstName = names[0];
                        String lastName = names.length > 1 ? names[1] :
                            (parishioner.getLastName() != null ? parishioner.getLastName() : "");

                        if (firstName.isEmpty()) {
                            logger.warn("Skipping child at index {} - empty first name after trim", i);
                            continue;
                        }

                        child.setFirstName(firstName);
                        child.setLastName(lastName);
                        child.setBirthday(childData.getBirthday());
                        if (household != null) {
                            child.setHousehold(household);
                        }
                        child = parishionerRepository.save(child);
                        logger.info("Created child id={} name={} {}", child.getId(), firstName, lastName);
                    }
                }
            }
        }

        // Update submission status
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedBy(reviewedBy);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setTargetParishioner(parishioner);
        submissionRepository.save(submission);

        logger.info("Successfully approved NEW submission id={}, created parishioner id={}", submission.getId(), parishioner.getId());
        return parishioner;
    }

    /**
     * Approve an UPDATE submission - updates an existing Parishioner record
     * @param submission The submission to approve
     * @param reviewedBy Who is approving
     * @param fieldsToUpdate List of field names to update, or null/empty to update all fields
     */
    @Transactional
    public Parishioner approveUpdateSubmission(
            ParishionerSubmission submission,
            String reviewedBy,
            List<String> fieldsToUpdate,
            Long selectedSpouseId,
            Long selectedGodfatherId,
            Long selectedGodmotherId,
            Long selectedSponsorId,
            Long selectedHouseholdId,
            String selectedNewHouseholdName,
            List<Long> childLinkIds,
            List<Integer> childCreateIndexes) {

        logger.info("Starting approval of UPDATE submission id={} by {}", submission.getId(), reviewedBy);

        if (submission.getSubmissionType() != SubmissionType.UPDATE) {
            throw new IllegalArgumentException("Only UPDATE submissions can use approveUpdateSubmission");
        }

        // Check submission status to prevent concurrent approvals
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new IllegalArgumentException("Submission has already been processed (status: " + submission.getStatus() + ")");
        }

        Parishioner parishioner = submission.getTargetParishioner();
        if (parishioner == null) {
            throw new IllegalArgumentException("Target parishioner not set for update submission");
        }

        // Validate dates
        validateDates(submission);

        // Validate relationship assignments (self-assignment and marriage conflicts)
        validateRelationshipAssignments(parishioner.getId(), selectedSpouseId, selectedGodfatherId, selectedGodmotherId, selectedSponsorId);

        // Helper to check if a field should be updated
        boolean updateAll = fieldsToUpdate == null || fieldsToUpdate.isEmpty();

        // Update basic fields
        if ((updateAll || fieldsToUpdate.contains("firstName")) && submission.getFirstName() != null && !submission.getFirstName().isEmpty()) {
            parishioner.setFirstName(submission.getFirstName().trim());
        }
        if ((updateAll || fieldsToUpdate.contains("lastName")) && submission.getLastName() != null && !submission.getLastName().isEmpty()) {
            parishioner.setLastName(submission.getLastName().trim());
        }
        if ((updateAll || fieldsToUpdate.contains("nameSuffix")) && submission.getNameSuffix() != null) {
            parishioner.setNameSuffix(submission.getNameSuffix());
        }
        if ((updateAll || fieldsToUpdate.contains("birthday")) && submission.getBirthday() != null) {
            parishioner.setBirthday(submission.getBirthday());
        }
        if ((updateAll || fieldsToUpdate.contains("email")) && submission.getEmail() != null) {
            parishioner.setEmail(submission.getEmail().trim());
        }
        if ((updateAll || fieldsToUpdate.contains("phoneNumber")) && submission.getPhoneNumber() != null) {
            parishioner.setPhoneNumber(submission.getPhoneNumber());
        }
        if ((updateAll || fieldsToUpdate.contains("membershipStatus")) && submission.getMembershipStatus() != null) {
            parishioner.setStatus(submission.getMembershipStatus());
        }
        if ((updateAll || fieldsToUpdate.contains("maritalStatus")) && submission.getMaritalStatus() != null) {
            parishioner.setMaritalStatus(submission.getMaritalStatus());
        }
        if ((updateAll || fieldsToUpdate.contains("marriageDate")) && submission.getMarriageDate() != null) {
            parishioner.setMarriageDate(submission.getMarriageDate());
        }

        // Update Orthodox fields
        if (Boolean.TRUE.equals(submission.getIsOrthodox())) {
            if ((updateAll || fieldsToUpdate.contains("baptismalName")) && submission.getBaptismalName() != null) {
                parishioner.setBaptismalName(submission.getBaptismalName());
            }
            if ((updateAll || fieldsToUpdate.contains("patronSaint")) && submission.getPatronSaint() != null) {
                parishioner.setPatronSaint(submission.getPatronSaint());
            }
            if ((updateAll || fieldsToUpdate.contains("baptismDate")) && submission.getBaptismDate() != null) {
                parishioner.setBaptismDate(submission.getBaptismDate());
            }
            if ((updateAll || fieldsToUpdate.contains("chrismationDate")) && submission.getChrismationDate() != null) {
                parishioner.setChrismationDate(submission.getChrismationDate());
            }
        }

        // Update relationship manual fields
        if ((updateAll || fieldsToUpdate.contains("manualSpouseName")) && submission.getManualSpouseName() != null) {
            parishioner.setManualSpouseName(submission.getManualSpouseName());
        }
        if ((updateAll || fieldsToUpdate.contains("manualGodfatherName")) && submission.getManualGodfatherName() != null) {
            parishioner.setManualGodfatherName(submission.getManualGodfatherName());
        }
        if ((updateAll || fieldsToUpdate.contains("manualGodmotherName")) && submission.getManualGodmotherName() != null) {
            parishioner.setManualGodmotherName(submission.getManualGodmotherName());
        }
        if ((updateAll || fieldsToUpdate.contains("manualSponsorName")) && submission.getManualSponsorName() != null) {
            parishioner.setManualSponsorName(submission.getManualSponsorName());
        }

        // Update household address fields if selected
        boolean updateAddress = updateAll || fieldsToUpdate.contains("address");
        boolean updateCity = updateAll || fieldsToUpdate.contains("city");
        boolean updateZipCode = updateAll || fieldsToUpdate.contains("zipCode");

        if (updateAddress || updateCity || updateZipCode) {
            Household household = parishioner.getHousehold();
            // Create household if needed and any address field is being set
            if (household == null) {
                boolean hasAddressData = (updateAddress && submission.getAddress() != null && !submission.getAddress().isEmpty()) ||
                                        (updateCity && submission.getCity() != null && !submission.getCity().isEmpty()) ||
                                        (updateZipCode && submission.getZipCode() != null && !submission.getZipCode().isEmpty());
                if (hasAddressData) {
                    household = new Household();
                    household = householdRepository.save(household);
                    parishioner.setHousehold(household);
                }
            }

            if (household != null) {
                if (updateAddress && submission.getAddress() != null) {
                    household.setAddress(submission.getAddress());
                }
                if (updateCity && submission.getCity() != null) {
                    household.setCity(submission.getCity());
                }
                if (updateZipCode && submission.getZipCode() != null) {
                    household.setZipCode(submission.getZipCode());
                }
                householdRepository.save(household);
            }
        }

        // Save updated parishioner
        parishioner = parishionerRepository.save(parishioner);
        logger.info("Updated parishioner id={} name={} {}", parishioner.getId(), parishioner.getFirstName(), parishioner.getLastName());

        // Apply relationship assignments
        if (selectedSpouseId != null && selectedSpouseId > 0) {
            Optional<Parishioner> spouseOpt = parishionerRepository.findById(selectedSpouseId);
            if (spouseOpt.isPresent()) {
                parishioner.marry(spouseOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(spouseOpt.get());
                logger.info("Assigned spouse id={} to parishioner id={}", selectedSpouseId, parishioner.getId());
            }
        }

        if (selectedGodfatherId != null && selectedGodfatherId > 0) {
            Optional<Parishioner> godfatherOpt = parishionerRepository.findById(selectedGodfatherId);
            if (godfatherOpt.isPresent()) {
                parishioner.assignGodfather(godfatherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godfatherOpt.get());
                logger.info("Assigned godfather id={} to parishioner id={}", selectedGodfatherId, parishioner.getId());
            }
        }

        if (selectedGodmotherId != null && selectedGodmotherId > 0) {
            Optional<Parishioner> godmotherOpt = parishionerRepository.findById(selectedGodmotherId);
            if (godmotherOpt.isPresent()) {
                parishioner.assignGodmother(godmotherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godmotherOpt.get());
                logger.info("Assigned godmother id={} to parishioner id={}", selectedGodmotherId, parishioner.getId());
            }
        }

        if (selectedSponsorId != null && selectedSponsorId > 0) {
            Optional<Parishioner> sponsorOpt = parishionerRepository.findById(selectedSponsorId);
            if (sponsorOpt.isPresent()) {
                parishioner.setWeddingSponsor(sponsorOpt.get());
                parishionerRepository.save(parishioner);
                logger.info("Assigned sponsor id={} to parishioner id={}", selectedSponsorId, parishioner.getId());
            }
        }

        // Handle household assignment
        if (selectedHouseholdId != null && selectedHouseholdId > 0) {
            Optional<Household> householdOpt = householdRepository.findById(selectedHouseholdId);
            if (householdOpt.isPresent()) {
                parishioner.setHousehold(householdOpt.get());
                parishionerRepository.save(parishioner);
                logger.info("Assigned existing household id={} to parishioner id={}", selectedHouseholdId, parishioner.getId());
            } else {
                logger.warn("Selected household id={} not found", selectedHouseholdId);
            }
        } else if (selectedNewHouseholdName != null && !selectedNewHouseholdName.trim().isEmpty()) {
            Household newHousehold = new Household();
            newHousehold.setFamilyName(selectedNewHouseholdName.trim());
            newHousehold.setAddress(submission.getAddress());
            newHousehold.setCity(submission.getCity());
            newHousehold.setZipCode(submission.getZipCode());
            Household household = householdRepository.save(newHousehold);
            parishioner.setHousehold(household);
            parishionerRepository.save(parishioner);
            logger.info("Created new household '{}' with id={} for parishioner id={}", selectedNewHouseholdName.trim(), household.getId(), parishioner.getId());
        }

        // Handle children linking/creation
        List<ParishionerSubmission.ChildData> children = submission.getChildrenList();
        if (!children.isEmpty() && (childLinkIds != null || childCreateIndexes != null)) {
            logger.info("Processing {} children from submission", children.size());
            for (int i = 0; i < children.size(); i++) {
                ParishionerSubmission.ChildData childData = children.get(i);

                // Check if this child should be linked to existing parishioner
                if (childLinkIds != null && i < childLinkIds.size() && childLinkIds.get(i) != null) {
                    Long childLinkId = childLinkIds.get(i);
                    Optional<Parishioner> existingChild = parishionerRepository.findById(childLinkId);
                    if (existingChild.isPresent()) {
                        Parishioner child = existingChild.get();
                        Household household = parishioner.getHousehold();
                        if (household != null) {
                            child.setHousehold(household);
                            parishionerRepository.save(child);
                            logger.info("Linked existing child id={} to household id={}", childLinkId, household.getId());
                        }
                    } else {
                        logger.warn("Child link id={} not found, skipping", childLinkId);
                    }
                }
                // Check if this child should be created
                else if (childCreateIndexes != null && childCreateIndexes.contains(i)) {
                    if (childData.getName() != null && !childData.getName().trim().isEmpty()) {
                        Parishioner child = new Parishioner();
                        String trimmedName = childData.getName().trim();
                        String[] names = trimmedName.split("\\s+", 2);
                        String firstName = names[0];
                        String lastName = names.length > 1 ? names[1] :
                            (parishioner.getLastName() != null ? parishioner.getLastName() : "");

                        if (firstName.isEmpty()) {
                            logger.warn("Skipping child at index {} - empty first name after trim", i);
                            continue;
                        }

                        child.setFirstName(firstName);
                        child.setLastName(lastName);
                        child.setBirthday(childData.getBirthday());
                        Household household = parishioner.getHousehold();
                        if (household != null) {
                            child.setHousehold(household);
                        }
                        child = parishionerRepository.save(child);
                        logger.info("Created child id={} name={} {}", child.getId(), firstName, lastName);
                    }
                }
            }
        }

        // Update submission status
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedBy(reviewedBy);
        submission.setReviewedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        logger.info("Successfully approved UPDATE submission id={} for parishioner id={}", submission.getId(), parishioner.getId());
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
