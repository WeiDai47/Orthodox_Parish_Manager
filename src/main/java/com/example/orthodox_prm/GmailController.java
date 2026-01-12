package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.dto.EmailPreviewDTO;
import com.example.orthodox_prm.dto.RecipientDTO;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.model.SentEmail;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.EmailHistoryService;
import com.example.orthodox_prm.service.GmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/gmail")
@Slf4j
public class GmailController {

    private final GmailService gmailService;
    private final EmailHistoryService emailHistoryService;
    private final ParishionerRepository parishionerRepository;

    public GmailController(GmailService gmailService,
                          EmailHistoryService emailHistoryService,
                          ParishionerRepository parishionerRepository) {
        this.gmailService = gmailService;
        this.emailHistoryService = emailHistoryService;
        this.parishionerRepository = parishionerRepository;
    }

    /**
     * Show email composition page
     */
    @GetMapping
    public String showGmailPage(Model model) {
        // Check OAuth authentication
        if (!gmailService.isGoogleOAuth2Authenticated()) {
            model.addAttribute("error", "You must authenticate with Google to send emails. Please go to Settings.");
            return "gmail";
        }

        model.addAttribute("membershipStatuses", MembershipStatus.values());
        return "gmail";
    }

    /**
     * AJAX endpoint to get all non-departed parishioners with emails
     * Returns JSON for client-side search/filtering
     */
    @GetMapping("/recipients")
    @ResponseBody
    public List<RecipientDTO> getRecipients() {
        List<Parishioner> allParishioners = parishionerRepository.findAll();

        return allParishioners.stream()
                .filter(p -> !isDeparted(p))
                .map(p -> {
                    RecipientDTO dto = new RecipientDTO();
                    dto.setParishionerId(p.getId());
                    String fullName = p.getFirstName() + " " + p.getLastName();
                    if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                        fullName += " " + p.getNameSuffix();
                    }
                    dto.setFullName(fullName);
                    dto.setStatus(p.getStatus() != null ? p.getStatus().toString() : "");

                    // Use individual email first, fall back to household
                    String email = null;
                    if (p.getEmail() != null && !p.getEmail().trim().isEmpty()) {
                        email = p.getEmail();
                    } else if (p.getHousehold() != null && p.getHousehold().getEmail() != null && !p.getHousehold().getEmail().trim().isEmpty()) {
                        email = p.getHousehold().getEmail();
                    }
                    dto.setEmail(email);

                    Household household = p.getHousehold();
                    if (household != null) {
                        dto.setHouseholdName(household.getFamilyName());
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Generate email preview
     */
    @PostMapping("/preview")
    public String preview(@RequestParam String subject,
                         @RequestParam String body,
                         @RequestParam(required = false) String individualRecipients,
                         @RequestParam(required = false) List<String> groupStatuses,
                         @RequestParam String sendMode,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        // Validate subject and body
        if (subject == null || subject.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Email subject cannot be empty.");
            return "redirect:/gmail";
        }
        if (body == null || body.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Email body cannot be empty.");
            return "redirect:/gmail";
        }

        // Trim values
        subject = subject.trim();
        body = body.trim();

        // Collect recipients
        Set<Parishioner> parishioners = new HashSet<>();
        StringBuilder filterCriteria = new StringBuilder();

        // Add individually selected parishioners
        if (individualRecipients != null && !individualRecipients.trim().isEmpty()) {
            String[] ids = individualRecipients.split(",");
            List<Long> idList = new ArrayList<>();
            for (String id : ids) {
                try {
                    idList.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid parishioner ID: {}", id);
                }
            }
            parishioners.addAll(parishionerRepository.findAllById(idList));
            filterCriteria.append("Individual Selection");
        }

        // Add group selections by status
        if (groupStatuses != null && !groupStatuses.isEmpty()) {
            // Filter out empty/null status strings
            List<String> validStatuses = groupStatuses.stream()
                    .filter(s -> s != null && !s.trim().isEmpty())
                    .collect(Collectors.toList());

            if (!validStatuses.isEmpty()) {
                for (String statusStr : validStatuses) {
                    try {
                        MembershipStatus status = MembershipStatus.valueOf(statusStr.trim());
                        List<Parishioner> statusGroup = parishionerRepository.findAll()
                                .stream()
                                .filter(p -> p.getStatus() == status && !isDeparted(p))
                                .collect(Collectors.toList());
                        parishioners.addAll(statusGroup);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid membership status: {}", statusStr);
                    }
                }
                if (filterCriteria.length() > 0) filterCriteria.append(" + ");
                filterCriteria.append(String.join(",", validStatuses));
            }
        }

        // Extract emails and track missing
        Map<String, String> emailToName = new HashMap<>();
        List<String> missingEmails = new ArrayList<>();

        for (Parishioner p : parishioners) {
            String fullName = p.getFirstName() + " " + p.getLastName();
            if (p.getNameSuffix() != null && !p.getNameSuffix().trim().isEmpty()) {
                fullName += " " + p.getNameSuffix();
            }

            // Check individual email first, then household
            String email = null;
            if (p.getEmail() != null && !p.getEmail().trim().isEmpty()) {
                email = p.getEmail();
            } else if (p.getHousehold() != null && p.getHousehold().getEmail() != null && !p.getHousehold().getEmail().trim().isEmpty()) {
                email = p.getHousehold().getEmail();
            }

            if (email != null) {
                emailToName.put(email, fullName);
            } else {
                missingEmails.add(fullName);
            }
        }

        // Deduplicate emails
        List<String> recipients = new ArrayList<>(emailToName.keySet());
        List<String> recipientNames = recipients.stream()
                .map(emailToName::get)
                .collect(Collectors.toList());

        // Build preview DTO
        EmailPreviewDTO preview = new EmailPreviewDTO();
        preview.setSubject(subject);
        preview.setBody(body);
        preview.setRecipients(recipients);
        preview.setRecipientNames(recipientNames);
        preview.setMissingEmails(missingEmails);
        preview.setSendMode(sendMode);
        preview.setFilterCriteria(filterCriteria.toString());

        model.addAttribute("preview", preview);
        model.addAttribute("membershipStatuses", MembershipStatus.values());
        return "gmail";
    }

    /**
     * Send emails
     */
    @PostMapping("/send")
    public String send(@RequestParam String subject,
                      @RequestParam String body,
                      @RequestParam String recipients, // Comma-separated emails
                      @RequestParam String sendMode,
                      @RequestParam(required = false) String filterCriteria,
                      RedirectAttributes redirectAttributes) {

        List<String> recipientList = Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toList());

        if (recipientList.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No valid email addresses to send to.");
            return "redirect:/gmail";
        }

        try {
            if ("GROUP_BCC".equals(sendMode)) {
                gmailService.sendEmailBCC(subject, body, recipientList);
            } else {
                gmailService.sendIndividualEmails(subject, body, recipientList);
            }

            // Save to history
            emailHistoryService.saveSentEmail(subject, body, recipientList, sendMode, filterCriteria);

            redirectAttributes.addFlashAttribute("success",
                    "Email sent successfully to " + recipientList.size() + " recipient(s)");

        } catch (Exception e) {
            log.error("Failed to send email", e);
            emailHistoryService.saveFailedEmail(subject, body, recipientList, sendMode, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to send email: " + e.getMessage());
        }

        return "redirect:/gmail";
    }

    /**
     * View email history
     */
    @GetMapping("/history")
    public String history(Model model) {
        List<SentEmail> emailHistory = emailHistoryService.getEmailHistory();
        model.addAttribute("emailHistory", emailHistory);
        return "gmail-history";
    }

    /**
     * Check if parishioner is departed
     */
    private boolean isDeparted(Parishioner p) {
        return p.getStatus() == MembershipStatus.DEPARTED || p.getDeathDate() != null;
    }
}
