package com.example.orthodox_prm.Controllers;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.Enum.SubmissionType;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.ParishionerSubmission;
import com.example.orthodox_prm.model.SubmissionLink;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.RateLimitingService;
import com.example.orthodox_prm.service.RecaptchaService;
import com.example.orthodox_prm.service.SubmissionLinkService;
import com.example.orthodox_prm.service.SubmissionService;
import com.example.orthodox_prm.util.InputSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/public/submit")
public class PublicSubmissionController {

    private static final Logger logger = LoggerFactory.getLogger(PublicSubmissionController.class);

    // Time-based validation constants (in seconds)
    private static final int MIN_FORM_COMPLETION_TIME = 5;
    private static final int MAX_FORM_COMPLETION_TIME = 1800; // 30 minutes

    @Autowired
    private SubmissionLinkService submissionLinkService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ParishionerRepository parishionerRepository;

    @Autowired
    private RateLimitingService rateLimitingService;

    @Autowired
    private RecaptchaService recaptchaService;

    @Autowired
    private InputSanitizer inputSanitizer;

    @Value("${recaptcha.site-key:}")
    private String recaptchaSiteKey;

    /**
     * Get client IP address, handling proxies
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Display the public submission form for a given link token
     */
    @GetMapping("/{token}")
    public String showSubmissionForm(@PathVariable String token, Model model, HttpServletRequest request) {
        String clientIp = getClientIp(request);

        // Check IP rate limit before showing form
        if (!rateLimitingService.isIpAllowed(clientIp)) {
            logger.warn("Rate limited IP {} attempted to access form", clientIp);
            model.addAttribute("errorMessage", "Too many requests. Please try again later.");
            return "public/submit-error";
        }

        // Validate the link
        Optional<SubmissionLink> linkOpt = submissionLinkService.findByToken(token);

        if (linkOpt.isEmpty()) {
            logger.warn("Invalid token attempted: {}", token.substring(0, Math.min(8, token.length())));
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

        // Check if link has reached submission limit
        if (link.hasReachedLimit()) {
            model.addAttribute("errorMessage", "This link has reached its maximum number of submissions.");
            return "public/submit-error";
        }

        // Check token rate limit
        if (!rateLimitingService.isTokenAllowed(token)) {
            logger.warn("Token {} rate limited", token.substring(0, Math.min(8, token.length())));
            model.addAttribute("errorMessage", "Too many submissions on this link. Please try again later.");
            return "public/submit-error";
        }

        // Increment access count
        submissionLinkService.incrementAccessCount(link.getId());

        // Generate form timestamp for time-based validation
        long formLoadTime = Instant.now().getEpochSecond();

        // Pass data to template
        ParishionerSubmission submission = new ParishionerSubmission();
        submission.setIsOrthodox(false);

        model.addAttribute("submission", submission);
        model.addAttribute("token", token);
        model.addAttribute("formLoadTime", formLoadTime);
        model.addAttribute("membershipStatuses", MembershipStatus.values());
        model.addAttribute("maritalStatuses", MaritalStatus.values());
        model.addAttribute("submissionTypes", SubmissionType.values());

        // Add reCAPTCHA site key if configured
        if (recaptchaService.isEnabled() && recaptchaSiteKey != null && !recaptchaSiteKey.isEmpty()) {
            model.addAttribute("recaptchaSiteKey", recaptchaSiteKey);
        }

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
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false) String[] childNames,
            @RequestParam(required = false) String[] childBirthdays,
            @RequestParam(required = false, defaultValue = "false") boolean isOrthodox,
            // Security fields
            @RequestParam(required = false) String website,  // Honeypot field
            @RequestParam(required = false) Long formLoadTime,  // Time-based validation
            @RequestParam(required = false, name = "g-recaptcha-response") String recaptchaToken,
            HttpServletRequest request,
            Model model) {

        String clientIp = getClientIp(request);

        // ==================== SECURITY CHECKS ====================

        // 1. Honeypot check - if filled, it's a bot
        if (website != null && !website.trim().isEmpty()) {
            logger.warn("Honeypot triggered from IP {}", clientIp);
            // Return success to fool the bot, but don't actually save
            return "redirect:/public/submit/success";
        }

        // 2. Time-based validation
        if (formLoadTime != null) {
            long currentTime = Instant.now().getEpochSecond();
            long timeTaken = currentTime - formLoadTime;

            if (timeTaken < MIN_FORM_COMPLETION_TIME) {
                logger.warn("Form submitted too quickly ({} seconds) from IP {}", timeTaken, clientIp);
                model.addAttribute("errorMessage", "Form submitted too quickly. Please take your time filling out the form.");
                return "public/submit-error";
            }

            if (timeTaken > MAX_FORM_COMPLETION_TIME) {
                logger.warn("Form submission expired ({} seconds) from IP {}", timeTaken, clientIp);
                model.addAttribute("errorMessage", "Your session has expired. Please reload the form and try again.");
                return "public/submit-error";
            }
        }

        // 3. Rate limiting - IP check
        if (!rateLimitingService.isIpAllowed(clientIp)) {
            logger.warn("Rate limited IP {} attempted submission", clientIp);
            model.addAttribute("errorMessage", "Too many submissions from your location. Please try again later.");
            return "public/submit-error";
        }

        // 4. Rate limiting - Token check
        if (!rateLimitingService.isTokenAllowed(token)) {
            logger.warn("Rate limited token {} attempted submission", token.substring(0, Math.min(8, token.length())));
            model.addAttribute("errorMessage", "Too many submissions on this link. Please try again later.");
            return "public/submit-error";
        }

        // 5. reCAPTCHA verification
        if (recaptchaService.isEnabled()) {
            if (!recaptchaService.verifyToken(recaptchaToken, clientIp)) {
                logger.warn("reCAPTCHA verification failed from IP {}", clientIp);
                model.addAttribute("errorMessage", "Security verification failed. Please try again.");
                return "public/submit-error";
            }
        }

        // 6. Check for suspicious content in inputs
        if (inputSanitizer.containsSuspiciousContent(firstName) ||
            inputSanitizer.containsSuspiciousContent(lastName) ||
            inputSanitizer.containsSuspiciousContent(email) ||
            inputSanitizer.containsSuspiciousContent(address)) {
            logger.warn("Suspicious content detected from IP {}", clientIp);
            model.addAttribute("errorMessage", "Invalid characters detected in submission.");
            return "public/submit-error";
        }

        // ==================== LINK VALIDATION ====================

        Optional<SubmissionLink> linkOpt = submissionLinkService.findByToken(token);

        if (linkOpt.isEmpty()) {
            model.addAttribute("errorMessage", "This link is invalid.");
            return "public/submit-error";
        }

        SubmissionLink link = linkOpt.get();

        if (!link.isValid()) {
            String reason = !link.getIsActive() ? "no longer active" :
                           link.isExpired() ? "expired" :
                           link.hasReachedLimit() ? "reached its submission limit" : "invalid";
            model.addAttribute("errorMessage", "This link is " + reason + ".");
            return "public/submit-error";
        }

        // ==================== INPUT SANITIZATION ====================

        String sanitizedFirstName = inputSanitizer.sanitizeName(firstName, InputSanitizer.MAX_NAME_LENGTH);
        String sanitizedLastName = inputSanitizer.sanitizeName(lastName, InputSanitizer.MAX_NAME_LENGTH);
        String sanitizedEmail = inputSanitizer.sanitizeEmail(email);
        String sanitizedPhone = inputSanitizer.sanitizePhone(phoneNumber);
        String sanitizedAddress = inputSanitizer.sanitizeAddress(address);
        String sanitizedCity = inputSanitizer.sanitizeCity(city);
        String sanitizedZipCode = inputSanitizer.sanitizeZipCode(zipCode);

        // Validate required fields after sanitization
        if (sanitizedFirstName == null || sanitizedFirstName.isEmpty()) {
            model.addAttribute("errorMessage", "First name is required and must contain valid characters.");
            return "public/submit-error";
        }

        // Validate email format if provided
        if (email != null && !email.trim().isEmpty() && sanitizedEmail == null) {
            model.addAttribute("errorMessage", "Please enter a valid email address.");
            return "public/submit-error";
        }

        // ==================== CREATE SUBMISSION ====================

        SubmissionType type;
        try {
            type = SubmissionType.valueOf(submissionType);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid submission type: {} from IP {}", submissionType, clientIp);
            model.addAttribute("errorMessage", "Invalid submission type.");
            return "public/submit-error";
        }

        ParishionerSubmission submission = new ParishionerSubmission(type, link);

        // Set sanitized basic fields
        submission.setFirstName(sanitizedFirstName);
        submission.setLastName(sanitizedLastName != null ? sanitizedLastName : "");
        submission.setNameSuffix(inputSanitizer.sanitize(nameSuffix));
        submission.setEmail(sanitizedEmail);
        submission.setPhoneNumber(sanitizedPhone);
        submission.setIsOrthodox(isOrthodox);
        submission.setAddress(sanitizedAddress);
        submission.setCity(sanitizedCity);
        submission.setZipCode(sanitizedZipCode);

        // Parse and validate dates
        if (birthday != null && !birthday.isEmpty()) {
            try {
                LocalDate parsedBirthday = LocalDate.parse(birthday);
                // Validate birthday is not in the future
                if (parsedBirthday.isAfter(LocalDate.now())) {
                    model.addAttribute("errorMessage", "Birthday cannot be in the future.");
                    return "public/submit-error";
                }
                submission.setBirthday(parsedBirthday);
            } catch (Exception e) {
                logger.debug("Invalid birthday format: {}", birthday);
            }
        }

        if (marriageDate != null && !marriageDate.isEmpty()) {
            try {
                LocalDate parsedMarriageDate = LocalDate.parse(marriageDate);
                if (parsedMarriageDate.isAfter(LocalDate.now())) {
                    model.addAttribute("errorMessage", "Marriage date cannot be in the future.");
                    return "public/submit-error";
                }
                submission.setMarriageDate(parsedMarriageDate);
            } catch (Exception e) {
                logger.debug("Invalid marriage date format: {}", marriageDate);
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

        // Orthodox fields (sanitized)
        if (isOrthodox) {
            submission.setBaptismalName(inputSanitizer.sanitizeName(baptismalName, InputSanitizer.MAX_NAME_LENGTH));
            submission.setPatronSaint(inputSanitizer.sanitizeName(patronSaint, InputSanitizer.MAX_GENERAL_LENGTH));

            if (baptismDate != null && !baptismDate.isEmpty()) {
                try {
                    submission.setBaptismDate(LocalDate.parse(baptismDate));
                } catch (Exception e) {
                    logger.debug("Invalid baptism date format: {}", baptismDate);
                }
            }

            if (chrismationDate != null && !chrismationDate.isEmpty()) {
                try {
                    submission.setChrismationDate(LocalDate.parse(chrismationDate));
                } catch (Exception e) {
                    logger.debug("Invalid chrismation date format: {}", chrismationDate);
                }
            }
        }

        // Relationship fields (sanitized)
        submission.setManualSpouseName(inputSanitizer.sanitizeName(manualSpouseName, InputSanitizer.MAX_NAME_LENGTH));
        submission.setManualGodfatherName(inputSanitizer.sanitizeName(manualGodfatherName, InputSanitizer.MAX_NAME_LENGTH));
        submission.setManualGodmotherName(inputSanitizer.sanitizeName(manualGodmotherName, InputSanitizer.MAX_NAME_LENGTH));
        submission.setManualSponsorName(inputSanitizer.sanitizeName(manualSponsorName, InputSanitizer.MAX_NAME_LENGTH));

        // Spouse creation fields (sanitized)
        submission.setSpouseFirstName(inputSanitizer.sanitizeName(spouseFirstName, InputSanitizer.MAX_NAME_LENGTH));
        submission.setSpouseLastName(inputSanitizer.sanitizeName(spouseLastName, InputSanitizer.MAX_NAME_LENGTH));
        submission.setSpouseEmail(inputSanitizer.sanitizeEmail(spouseEmail));
        submission.setSpousePhoneNumber(inputSanitizer.sanitizePhone(spousePhoneNumber));

        // Process children (sanitized)
        if (childNames != null && childNames.length > 0) {
            List<ParishionerSubmission.ChildData> children = new ArrayList<>();
            for (int i = 0; i < childNames.length && i < 20; i++) { // Limit to 20 children
                String sanitizedChildName = inputSanitizer.sanitizeName(childNames[i], InputSanitizer.MAX_NAME_LENGTH);
                if (sanitizedChildName != null && !sanitizedChildName.isEmpty()) {
                    LocalDate childBirthday = null;
                    if (childBirthdays != null && i < childBirthdays.length && childBirthdays[i] != null && !childBirthdays[i].isEmpty()) {
                        try {
                            childBirthday = LocalDate.parse(childBirthdays[i]);
                        } catch (Exception e) {
                            logger.debug("Invalid child birthday format: {}", childBirthdays[i]);
                        }
                    }
                    children.add(new ParishionerSubmission.ChildData(sanitizedChildName, childBirthday));
                }
            }
            submission.setChildrenList(children);
        }

        // Validate submission
        List<String> errors = submissionService.validateSubmission(submission);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("token", token);
            return "public/submit-error";
        }

        // ==================== SAVE SUBMISSION ====================

        try {
            submissionService.saveSubmission(submission);

            // Record successful submission for rate limiting
            rateLimitingService.recordIpSubmission(clientIp);
            rateLimitingService.recordTokenSubmission(token);

            // Increment link submission count
            link.incrementSubmissionCount();
            submissionLinkService.saveLink(link);

            logger.info("Successful submission from IP {} on token {}", clientIp,
                token.substring(0, Math.min(8, token.length())));

        } catch (Exception e) {
            logger.error("Error saving submission from IP {}: {}", clientIp, e.getMessage());
            model.addAttribute("errorMessage", "Error processing submission. Please try again.");
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
