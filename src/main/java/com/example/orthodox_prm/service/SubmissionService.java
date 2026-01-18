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
            List<Integer> childCreateIndexes) {
        if (submission.getSubmissionType() != SubmissionType.NEW) {
            throw new IllegalArgumentException("Only NEW submissions can use approveNewSubmission");
        }

        // Create household if address provided
        Household household = null;
        if ((submission.getAddress() != null && !submission.getAddress().isEmpty()) ||
            (submission.getCity() != null && !submission.getCity().isEmpty()) ||
            (submission.getZipCode() != null && !submission.getZipCode().isEmpty())) {
            household = new Household();
            household.setAddress(submission.getAddress());
            household.setCity(submission.getCity());
            household.setZipCode(submission.getZipCode());
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

        // Apply relationship assignments
        if (selectedSpouseId != null && selectedSpouseId > 0) {
            Optional<Parishioner> spouseOpt = parishionerRepository.findById(selectedSpouseId);
            if (spouseOpt.isPresent()) {
                parishioner.marry(spouseOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(spouseOpt.get());
            }
        }

        if (selectedGodfatherId != null && selectedGodfatherId > 0) {
            Optional<Parishioner> godfatherOpt = parishionerRepository.findById(selectedGodfatherId);
            if (godfatherOpt.isPresent()) {
                parishioner.assignGodfather(godfatherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godfatherOpt.get());
            }
        }

        if (selectedGodmotherId != null && selectedGodmotherId > 0) {
            Optional<Parishioner> godmotherOpt = parishionerRepository.findById(selectedGodmotherId);
            if (godmotherOpt.isPresent()) {
                parishioner.assignGodmother(godmotherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godmotherOpt.get());
            }
        }

        if (selectedSponsorId != null && selectedSponsorId > 0) {
            Optional<Parishioner> sponsorOpt = parishionerRepository.findById(selectedSponsorId);
            if (sponsorOpt.isPresent()) {
                parishioner.setWeddingSponsor(sponsorOpt.get());
                parishionerRepository.save(parishioner);
            }
        }

        // Handle household assignment
        if (selectedHouseholdId != null && selectedHouseholdId > 0) {
            Optional<Household> householdOpt = householdRepository.findById(selectedHouseholdId);
            if (householdOpt.isPresent()) {
                parishioner.setHousehold(householdOpt.get());
                parishionerRepository.save(parishioner);
                household = householdOpt.get();
            }
        } else if (selectedNewHouseholdName != null && !selectedNewHouseholdName.isEmpty()) {
            Household newHousehold = new Household();
            newHousehold.setFamilyName(selectedNewHouseholdName);
            newHousehold.setAddress(submission.getAddress());
            newHousehold.setCity(submission.getCity());
            newHousehold.setZipCode(submission.getZipCode());
            household = householdRepository.save(newHousehold);
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
            for (int i = 0; i < children.size(); i++) {
                ParishionerSubmission.ChildData childData = children.get(i);

                // Check if this child should be linked to existing parishioner
                if (childLinkIds != null && i < childLinkIds.size() && childLinkIds.get(i) != null) {
                    Optional<Parishioner> existingChild = parishionerRepository.findById(childLinkIds.get(i));
                    if (existingChild.isPresent()) {
                        Parishioner child = existingChild.get();
                        child.setHousehold(household);
                        parishionerRepository.save(child);
                    }
                }
                // Check if this child should be created
                else if (childCreateIndexes != null && childCreateIndexes.contains(i)) {
                    if (childData.getName() != null && !childData.getName().isEmpty()) {
                        Parishioner child = new Parishioner();
                        String[] names = childData.getName().split(" ", 2);
                        child.setFirstName(names[0].trim());
                        child.setLastName(names.length > 1 ? names[1].trim() : parishioner.getLastName());
                        child.setBirthday(childData.getBirthday());
                        child.setHousehold(household);
                        parishionerRepository.save(child);
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
        if (submission.getSubmissionType() != SubmissionType.UPDATE) {
            throw new IllegalArgumentException("Only UPDATE submissions can use approveUpdateSubmission");
        }

        Parishioner parishioner = submission.getTargetParishioner();
        if (parishioner == null) {
            throw new IllegalArgumentException("Target parishioner not set for update submission");
        }

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

        // Apply relationship assignments
        if (selectedSpouseId != null && selectedSpouseId > 0) {
            Optional<Parishioner> spouseOpt = parishionerRepository.findById(selectedSpouseId);
            if (spouseOpt.isPresent()) {
                parishioner.marry(spouseOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(spouseOpt.get());
            }
        }

        if (selectedGodfatherId != null && selectedGodfatherId > 0) {
            Optional<Parishioner> godfatherOpt = parishionerRepository.findById(selectedGodfatherId);
            if (godfatherOpt.isPresent()) {
                parishioner.assignGodfather(godfatherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godfatherOpt.get());
            }
        }

        if (selectedGodmotherId != null && selectedGodmotherId > 0) {
            Optional<Parishioner> godmotherOpt = parishionerRepository.findById(selectedGodmotherId);
            if (godmotherOpt.isPresent()) {
                parishioner.assignGodmother(godmotherOpt.get());
                parishionerRepository.save(parishioner);
                parishionerRepository.save(godmotherOpt.get());
            }
        }

        if (selectedSponsorId != null && selectedSponsorId > 0) {
            Optional<Parishioner> sponsorOpt = parishionerRepository.findById(selectedSponsorId);
            if (sponsorOpt.isPresent()) {
                parishioner.setWeddingSponsor(sponsorOpt.get());
                parishionerRepository.save(parishioner);
            }
        }

        // Handle household assignment
        if (selectedHouseholdId != null && selectedHouseholdId > 0) {
            Optional<Household> householdOpt = householdRepository.findById(selectedHouseholdId);
            if (householdOpt.isPresent()) {
                parishioner.setHousehold(householdOpt.get());
                parishionerRepository.save(parishioner);
            }
        } else if (selectedNewHouseholdName != null && !selectedNewHouseholdName.isEmpty()) {
            Household newHousehold = new Household();
            newHousehold.setFamilyName(selectedNewHouseholdName);
            newHousehold.setAddress(submission.getAddress());
            newHousehold.setCity(submission.getCity());
            newHousehold.setZipCode(submission.getZipCode());
            Household household = householdRepository.save(newHousehold);
            parishioner.setHousehold(household);
            parishionerRepository.save(parishioner);
        }

        // Handle children linking/creation
        List<ParishionerSubmission.ChildData> children = submission.getChildrenList();
        if (!children.isEmpty() && (childLinkIds != null || childCreateIndexes != null)) {
            for (int i = 0; i < children.size(); i++) {
                ParishionerSubmission.ChildData childData = children.get(i);

                // Check if this child should be linked to existing parishioner
                if (childLinkIds != null && i < childLinkIds.size() && childLinkIds.get(i) != null) {
                    Optional<Parishioner> existingChild = parishionerRepository.findById(childLinkIds.get(i));
                    if (existingChild.isPresent()) {
                        Parishioner child = existingChild.get();
                        Household household = parishioner.getHousehold();
                        if (household != null) {
                            child.setHousehold(household);
                            parishionerRepository.save(child);
                        }
                    }
                }
                // Check if this child should be created
                else if (childCreateIndexes != null && childCreateIndexes.contains(i)) {
                    if (childData.getName() != null && !childData.getName().isEmpty()) {
                        Parishioner child = new Parishioner();
                        String[] names = childData.getName().split(" ", 2);
                        child.setFirstName(names[0].trim());
                        child.setLastName(names.length > 1 ? names[1].trim() : parishioner.getLastName());
                        child.setBirthday(childData.getBirthday());
                        Household household = parishioner.getHousehold();
                        if (household != null) {
                            child.setHousehold(household);
                        }
                        parishionerRepository.save(child);
                    }
                }
            }
        }

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
