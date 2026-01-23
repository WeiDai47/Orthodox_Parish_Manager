package com.example.orthodox_prm.Controllers;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.Enum.SubmissionStatus;
import com.example.orthodox_prm.Enum.SubmissionType;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.ParishionerSubmission;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/submissions/review")
@PreAuthorize("hasAnyRole('PRIEST','SECRETARY')")
public class SubmissionReviewController {

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ParishionerRepository parishionerRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    /**
     * Get the current user's email/username from authentication context
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "admin";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            return oauth2User.getAttribute("email");
        }
        return authentication.getName();
    }

    /**
     * Display list of pending submissions for review
     */
    @GetMapping
    public String listPendingSubmissions(
            @RequestParam(required = false) String status,
            Model model) {

        List<ParishionerSubmission> submissions;

        if (status != null && !status.isEmpty()) {
            try {
                SubmissionStatus statusEnum = SubmissionStatus.valueOf(status.toUpperCase());
                submissions = submissionService.getSubmissionsByStatus(statusEnum);
            } catch (IllegalArgumentException e) {
                submissions = submissionService.getPendingSubmissions();
            }
        } else {
            submissions = submissionService.getPendingSubmissions();
        }

        long pendingCount = submissionService.getPendingSubmissionCount();

        model.addAttribute("submissions", submissions);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("currentStatus", status != null ? status : "PENDING");

        return "submissions/review-list";
    }

    /**
     * Display detailed review page for a single submission
     */
    @GetMapping("/{id}")
    public String reviewSubmission(@PathVariable Long id, Model model) {
        Optional<ParishionerSubmission> submissionOpt = submissionService.getSubmissionById(id);

        if (submissionOpt.isEmpty()) {
            return "redirect:/submissions/review";
        }

        ParishionerSubmission submission = submissionOpt.get();
        Parishioner currentParishioner = null;

        // For UPDATE submissions, load the current parishioner for comparison
        if (submission.getSubmissionType() == SubmissionType.UPDATE && submission.getTargetParishioner() != null) {
            currentParishioner = submission.getTargetParishioner();
        }

        model.addAttribute("submission", submission);
        model.addAttribute("currentParishioner", currentParishioner);
        model.addAttribute("isUpdate", submission.getSubmissionType() == SubmissionType.UPDATE);
        model.addAttribute("allParishioners", parishionerRepository.findAll());
        model.addAttribute("allHouseholds", householdRepository.findAll());

        // Get other pending submissions for spouse linking (exclude current submission)
        List<ParishionerSubmission> pendingSubmissions = submissionService.getPendingSubmissions();
        pendingSubmissions.removeIf(s -> s.getId().equals(submission.getId()));
        model.addAttribute("pendingSubmissions", pendingSubmissions);

        return "submissions/review-detail";
    }

    /**
     * Approve a submission
     */
    @PostMapping("/{id}/approve")
    public String approveSubmission(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetParishionerId,
            @RequestParam(required = false) List<String> fieldsToUpdate,
            // NEW: Add edited field parameters
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String nameSuffix,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthday,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false) String maritalStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate marriageDate,
            @RequestParam(required = false) String baptismalName,
            @RequestParam(required = false) String patronSaint,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baptismDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate chrismationDate,
            @RequestParam(required = false) String membershipStatus,
            // NEW: Relationship assignment parameters
            @RequestParam(required = false) Long selectedSpouseId,
            @RequestParam(required = false) String manualSpouseName,
            @RequestParam(required = false) Long selectedGodfatherId,
            @RequestParam(required = false) String manualGodfatherName,
            @RequestParam(required = false) Long selectedGodmotherId,
            @RequestParam(required = false) String manualGodmotherName,
            @RequestParam(required = false) Long selectedSponsorId,
            @RequestParam(required = false) String manualSponsorName,
            @RequestParam(required = false) Long selectedHouseholdId,
            @RequestParam(required = false) String selectedNewHouseholdName,
            // NEW: Children linking parameters
            @RequestParam(required = false) List<Long> childLinkIds,
            @RequestParam(required = false) List<Integer> childCreateIndexes,
            // NEW: Pending spouse submission linking
            @RequestParam(required = false) Long pendingSpouseSubmissionId,
            Model model) {
        Optional<ParishionerSubmission> submissionOpt = submissionService.getSubmissionById(id);

        if (submissionOpt.isEmpty()) {
            return "redirect:/submissions/review";
        }

        ParishionerSubmission submission = submissionOpt.get();

        // Apply edited fields to submission before approval
        if (firstName != null && !firstName.trim().isEmpty()) {
            submission.setFirstName(firstName.trim());
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            submission.setLastName(lastName.trim());
        }
        submission.setNameSuffix(nameSuffix != null ? nameSuffix.trim() : null);
        submission.setBirthday(birthday);
        if (email != null) {
            submission.setEmail(email.trim().isEmpty() ? null : email.trim());
        }
        submission.setPhoneNumber(phoneNumber);
        submission.setAddress(address);
        submission.setCity(city);
        submission.setZipCode(zipCode);
        if (maritalStatus != null && !maritalStatus.isEmpty()) {
            try {
                submission.setMaritalStatus(MaritalStatus.valueOf(maritalStatus.toUpperCase()));
            } catch (IllegalArgumentException e) {
                model.addAttribute("errorMessage", "Invalid marital status: " + maritalStatus);
                return "redirect:/submissions/review/" + id;
            }
        }
        submission.setMarriageDate(marriageDate);
        submission.setBaptismalName(baptismalName);
        submission.setPatronSaint(patronSaint);
        submission.setBaptismDate(baptismDate);
        submission.setChrismationDate(chrismationDate);
        if (membershipStatus != null && !membershipStatus.isEmpty()) {
            try {
                submission.setMembershipStatus(MembershipStatus.valueOf(membershipStatus.toUpperCase()));
            } catch (IllegalArgumentException e) {
                model.addAttribute("errorMessage", "Invalid membership status: " + membershipStatus);
                return "redirect:/submissions/review/" + id;
            }
        }

        // Update manual relationship names if provided
        if (manualSpouseName != null) submission.setManualSpouseName(manualSpouseName);
        if (manualGodfatherName != null) submission.setManualGodfatherName(manualGodfatherName);
        if (manualGodmotherName != null) submission.setManualGodmotherName(manualGodmotherName);
        if (manualSponsorName != null) submission.setManualSponsorName(manualSponsorName);

        String currentUser = getCurrentUserEmail();

        try {
            if (submission.getSubmissionType() == SubmissionType.NEW) {
                // Pass relationship parameters to service
                submissionService.approveNewSubmission(
                    submission, currentUser,
                    selectedSpouseId, selectedGodfatherId, selectedGodmotherId, selectedSponsorId,
                    selectedHouseholdId, selectedNewHouseholdName,
                    childLinkIds, childCreateIndexes,
                    pendingSpouseSubmissionId
                );
            } else if (submission.getSubmissionType() == SubmissionType.UPDATE) {
                if (targetParishionerId != null && targetParishionerId > 0) {
                    Optional<Parishioner> targetParish = parishionerRepository.findById(targetParishionerId);
                    if (targetParish.isPresent()) {
                        submission.setTargetParishioner(targetParish.get());
                    }
                }
                submissionService.approveUpdateSubmission(
                    submission, currentUser, fieldsToUpdate,
                    selectedSpouseId, selectedGodfatherId, selectedGodmotherId, selectedSponsorId,
                    selectedHouseholdId, selectedNewHouseholdName,
                    childLinkIds, childCreateIndexes
                );
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error approving submission: " + e.getMessage());
            return "redirect:/submissions/review/" + id;
        }

        return "redirect:/submissions/review";
    }

    /**
     * Load and display a parishioner for comparison
     */
    @PostMapping("/{id}/load-parishioner")
    public String loadParishionerForComparison(@PathVariable Long id, @RequestParam Long targetParishionerId) {
        Optional<ParishionerSubmission> submissionOpt = submissionService.getSubmissionById(id);

        if (submissionOpt.isPresent()) {
            ParishionerSubmission submission = submissionOpt.get();
            Optional<Parishioner> parishioner = parishionerRepository.findById(targetParishionerId);

            if (parishioner.isPresent()) {
                submission.setTargetParishioner(parishioner.get());
                submissionService.saveSubmission(submission);
            }
        }

        return "redirect:/submissions/review/" + id;
    }

    /**
     * Reject a submission
     */
    @PostMapping("/{id}/reject")
    public String rejectSubmission(
            @PathVariable Long id,
            @RequestParam(required = false) String rejectionNotes,
            Model model) {

        Optional<ParishionerSubmission> submissionOpt = submissionService.getSubmissionById(id);

        if (submissionOpt.isEmpty()) {
            return "redirect:/submissions/review";
        }

        String currentUser = getCurrentUserEmail();

        try {
            submissionService.rejectSubmission(id, rejectionNotes, currentUser);
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error rejecting submission: " + e.getMessage());
            return "redirect:/submissions/review/" + id;
        }

        return "redirect:/submissions/review";
    }

    /**
     * View submission history (approved and rejected)
     */
    @GetMapping("/history")
    public String viewHistory(Model model) {
        List<ParishionerSubmission> approved = submissionService.getSubmissionsByStatus(SubmissionStatus.APPROVED);
        List<ParishionerSubmission> rejected = submissionService.getSubmissionsByStatus(SubmissionStatus.REJECTED);

        model.addAttribute("approvedSubmissions", approved);
        model.addAttribute("rejectedSubmissions", rejected);

        return "submissions/review-history";
    }
}
