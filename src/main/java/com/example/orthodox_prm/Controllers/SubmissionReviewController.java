package com.example.orthodox_prm.Controllers;

import com.example.orthodox_prm.Enum.SubmissionStatus;
import com.example.orthodox_prm.Enum.SubmissionType;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.ParishionerSubmission;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/submissions/review")
public class SubmissionReviewController {

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ParishionerRepository parishionerRepository;

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
            Model model) {
        Optional<ParishionerSubmission> submissionOpt = submissionService.getSubmissionById(id);

        if (submissionOpt.isEmpty()) {
            return "redirect:/submissions/review";
        }

        ParishionerSubmission submission = submissionOpt.get();
        String currentUser = getCurrentUserEmail();

        try {
            if (submission.getSubmissionType() == SubmissionType.NEW) {
                submissionService.approveNewSubmission(submission, currentUser);
            } else if (submission.getSubmissionType() == SubmissionType.UPDATE) {
                // For UPDATE, set the target parishioner if one was selected
                if (targetParishionerId != null && targetParishionerId > 0) {
                    Optional<Parishioner> targetParish = parishionerRepository.findById(targetParishionerId);
                    if (targetParish.isPresent()) {
                        submission.setTargetParishioner(targetParish.get());
                    }
                }
                // Pass the list of fields to update (null means update all)
                submissionService.approveUpdateSubmission(submission, currentUser, fieldsToUpdate);
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
