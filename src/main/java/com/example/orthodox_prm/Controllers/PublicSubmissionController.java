package com.example.orthodox_prm.Controllers;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.Enum.SubmissionType;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.ParishionerSubmission;
import com.example.orthodox_prm.model.SubmissionLink;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.SubmissionLinkService;
import com.example.orthodox_prm.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/public/submit")
public class PublicSubmissionController {

    @Autowired
    private SubmissionLinkService submissionLinkService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ParishionerRepository parishionerRepository;

    /**
     * Display the public submission form for a given link token
     */
    @GetMapping("/{token}")
    public String showSubmissionForm(@PathVariable String token, Model model) {
        // Validate the link
        Optional<SubmissionLink> linkOpt = submissionLinkService.findByToken(token);

        if (linkOpt.isEmpty()) {
            model.addAttribute("errorMessage", "This link is invalid.");
            return "public/submit-error";
        }

        SubmissionLink link = linkOpt.get();

        // Check if link is active
        if (!link.getIsActive()) {
            model.addAttribute("errorMessage", "This link is no longer active.");
            return "public/submit-error";
        }

        // Check if link is expired
        if (link.isExpired()) {
            model.addAttribute("errorMessage", "This link has expired.");
            return "public/submit-error";
        }

        // Increment access count
        submissionLinkService.incrementAccessCount(link.getId());

        // Pass enums to template
        ParishionerSubmission submission = new ParishionerSubmission();
        submission.setIsOrthodox(false);

        model.addAttribute("submission", submission);
        model.addAttribute("token", token);
        model.addAttribute("membershipStatuses", MembershipStatus.values());
        model.addAttribute("maritalStatuses", MaritalStatus.values());
        model.addAttribute("submissionTypes", SubmissionType.values());

        return "public/submit-form";
    }

    /**
     * Process the public submission form
     */
    @PostMapping("/{token}")
    public String processSubmission(
            @PathVariable String token,
            @RequestParam String submissionType,
            @RequestParam String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String nameSuffix,
            @RequestParam(required = false) String birthday,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String membershipStatusStr,
            @RequestParam(required = false) String maritalStatusStr,
            @RequestParam(required = false) String marriageDate,
            @RequestParam(required = false) String baptismalName,
            @RequestParam(required = false) String patronSaint,
            @RequestParam(required = false) String baptismDate,
            @RequestParam(required = false) String chrismationDate,
            @RequestParam(required = false) String manualSpouseName,
            @RequestParam(required = false) String spouseFirstName,
            @RequestParam(required = false) String spouseLastName,
            @RequestParam(required = false) String spouseEmail,
            @RequestParam(required = false) String spousePhoneNumber,
            @RequestParam(required = false) String manualGodfatherName,
            @RequestParam(required = false) String manualGodmotherName,
            @RequestParam(required = false) String manualSponsorName,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String[] childNames,
            @RequestParam(required = false) String[] childBirthdays,
            @RequestParam(required = false, defaultValue = "false") boolean isOrthodox,
            Model model) {

        // Validate the link again
        Optional<SubmissionLink> linkOpt = submissionLinkService.findByToken(token);

        if (linkOpt.isEmpty() || !linkOpt.get().getIsActive() || linkOpt.get().isExpired()) {
            model.addAttribute("errorMessage", "This link is invalid or has expired.");
            return "public/submit-error";
        }

        SubmissionLink link = linkOpt.get();

        // Validate form data
        if (firstName == null || firstName.trim().isEmpty()) {
            model.addAttribute("error", "First name is required");
            model.addAttribute("token", token);
            return "redirect:/public/submit/" + token + "?error=First name is required";
        }

        // Create submission
        SubmissionType type = SubmissionType.valueOf(submissionType);
        ParishionerSubmission submission = new ParishionerSubmission(type, link);

        // Set basic fields
        submission.setFirstName(firstName.trim());
        submission.setLastName(lastName != null ? lastName.trim() : "");
        submission.setNameSuffix(nameSuffix);
        submission.setEmail(email != null ? email.trim() : null);
        submission.setPhoneNumber(phoneNumber);
        submission.setIsOrthodox(isOrthodox);
        submission.setAddress(address);
        submission.setCity(city);

        // Parse dates
        if (birthday != null && !birthday.isEmpty()) {
            try {
                submission.setBirthday(LocalDate.parse(birthday));
            } catch (Exception e) {
                // Ignore invalid dates
            }
        }

        if (marriageDate != null && !marriageDate.isEmpty()) {
            try {
                submission.setMarriageDate(LocalDate.parse(marriageDate));
            } catch (Exception e) {
                // Ignore invalid dates
            }
        }

        // Set membership and marital status
        if (membershipStatusStr != null && !membershipStatusStr.isEmpty()) {
            try {
                submission.setMembershipStatus(MembershipStatus.valueOf(membershipStatusStr));
            } catch (IllegalArgumentException e) {
                submission.setMembershipStatus(MembershipStatus.VISITOR);
            }
        }

        if (maritalStatusStr != null && !maritalStatusStr.isEmpty()) {
            try {
                submission.setMaritalStatus(MaritalStatus.valueOf(maritalStatusStr));
            } catch (IllegalArgumentException e) {
                // Don't set if invalid
            }
        }

        // Orthodox fields
        if (isOrthodox) {
            submission.setBaptismalName(baptismalName);
            submission.setPatronSaint(patronSaint);

            if (baptismDate != null && !baptismDate.isEmpty()) {
                try {
                    submission.setBaptismDate(LocalDate.parse(baptismDate));
                } catch (Exception e) {
                    // Ignore invalid dates
                }
            }

            if (chrismationDate != null && !chrismationDate.isEmpty()) {
                try {
                    submission.setChrismationDate(LocalDate.parse(chrismationDate));
                } catch (Exception e) {
                    // Ignore invalid dates
                }
            }
        }

        // Relationship fields
        submission.setManualSpouseName(manualSpouseName);
        submission.setManualGodfatherName(manualGodfatherName);
        submission.setManualGodmotherName(manualGodmotherName);
        submission.setManualSponsorName(manualSponsorName);

        // Spouse creation fields
        submission.setSpouseFirstName(spouseFirstName);
        submission.setSpouseLastName(spouseLastName);
        submission.setSpouseEmail(spouseEmail);
        submission.setSpousePhoneNumber(spousePhoneNumber);

        // Process children
        if (childNames != null && childNames.length > 0) {
            List<ParishionerSubmission.ChildData> children = new ArrayList<>();
            for (int i = 0; i < childNames.length; i++) {
                if (childNames[i] != null && !childNames[i].isEmpty()) {
                    LocalDate childBirthday = null;
                    if (childBirthdays != null && i < childBirthdays.length && childBirthdays[i] != null && !childBirthdays[i].isEmpty()) {
                        try {
                            childBirthday = LocalDate.parse(childBirthdays[i]);
                        } catch (Exception e) {
                            // Ignore invalid dates
                        }
                    }
                    children.add(new ParishionerSubmission.ChildData(childNames[i], childBirthday));
                }
            }
            submission.setChildrenList(children);
        }

        // For UPDATE submissions, priest will manually assign the target parishioner during review
        // No automatic search - submission is saved with updateName field only

        // Validate submission
        List<String> errors = submissionService.validateSubmission(submission);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("token", token);
            return "public/submit-error";
        }

        // Save submission
        try {
            submissionService.saveSubmission(submission);
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error processing submission: " + e.getMessage());
            return "public/submit-error";
        }

        return "redirect:/public/submit/success";
    }

    /**
     * Display success confirmation page
     */
    @GetMapping("/success")
    public String showSuccessPage() {
        return "public/submit-success";
    }

    /**
     * Display error page
     */
    @GetMapping("/error")
    public String showErrorPage(Model model) {
        if (!model.containsAttribute("errorMessage")) {
            model.addAttribute("errorMessage", "An error occurred. Please try again.");
        }
        return "public/submit-error";
    }
}
