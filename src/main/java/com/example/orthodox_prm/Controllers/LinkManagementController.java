package com.example.orthodox_prm.Controllers;

import com.example.orthodox_prm.model.ParishionerSubmission;
import com.example.orthodox_prm.model.SubmissionLink;
import com.example.orthodox_prm.service.QRCodeService;
import com.example.orthodox_prm.service.SubmissionLinkService;
import com.example.orthodox_prm.service.SubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/links")
@PreAuthorize("hasAnyRole('PRIEST','SECRETARY')")
public class LinkManagementController {

    @Autowired
    private SubmissionLinkService submissionLinkService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private QRCodeService qrCodeService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

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
     * Display all submission links created by current user
     */
    @GetMapping
    public String listLinks(Model model) {
        String currentUser = getCurrentUserEmail();
        List<SubmissionLink> links = submissionLinkService.getLinksByCreator(currentUser);

        long pendingCount = submissionService.getPendingSubmissionCount();

        model.addAttribute("links", links);
        model.addAttribute("pendingSubmissionCount", pendingCount);

        return "links/manage-links";
    }

    /**
     * Display form to create a new submission link
     */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("link", new SubmissionLink());
        return "links/create-link";
    }

    /**
     * Process creation of a new submission link
     */
    @PostMapping("/create")
    public String createLink(
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String expirationDate,
            @RequestParam(defaultValue = "false") boolean neverExpires,
            @RequestParam(defaultValue = "true") boolean unlimitedSubmissions,
            @RequestParam(required = false) Integer maxSubmissions,
            Model model) {

        String currentUser = getCurrentUserEmail();

        // Calculate expiration time
        LocalDateTime expiresAt = null;
        if (!neverExpires && expirationDate != null && !expirationDate.isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                LocalDateTime date = LocalDateTime.parse(expirationDate + "T23:59:59", formatter);
                expiresAt = date;
            } catch (Exception e) {
                expiresAt = LocalDateTime.now().plusDays(30); // Default to 30 days if parse fails
            }
        }

        // Determine max submissions (null = unlimited)
        Integer effectiveMaxSubmissions = unlimitedSubmissions ? null : maxSubmissions;

        // Create the link
        SubmissionLink link = submissionLinkService.createLink(currentUser, expiresAt, description, effectiveMaxSubmissions);

        // Generate the full URL for the submission form
        String submissionUrl = appUrl + "/public/submit/" + link.getToken();

        // Generate QR code
        String qrCodeBase64;
        try {
            qrCodeBase64 = qrCodeService.generateQRCodeBase64(submissionUrl, 300, 300);
        } catch (Exception e) {
            qrCodeBase64 = null;
        }

        model.addAttribute("link", link);
        model.addAttribute("submissionUrl", submissionUrl);
        model.addAttribute("qrCodeBase64", qrCodeBase64);
        model.addAttribute("successMessage", "Submission link created successfully!");

        return "links/create-link";
    }

    /**
     * Display printable QR code page for a link
     */
    @GetMapping("/{id}/qr-print")
    public String showQRPrintPage(@PathVariable Long id, Model model) {
        Optional<SubmissionLink> linkOpt = submissionLinkService.getLinkById(id);

        if (linkOpt.isEmpty()) {
            return "redirect:/links";
        }

        SubmissionLink link = linkOpt.get();
        String submissionUrl = appUrl + "/public/submit/" + link.getToken();

        // Generate QR code
        String qrCodeBase64;
        try {
            qrCodeBase64 = qrCodeService.generateQRCodeBase64(submissionUrl, 400, 400);
        } catch (Exception e) {
            qrCodeBase64 = null;
        }

        model.addAttribute("link", link);
        model.addAttribute("submissionUrl", submissionUrl);
        model.addAttribute("qrCodeBase64", qrCodeBase64);

        return "links/qr-code-print";
    }

    /**
     * Deactivate a submission link
     */
    @PostMapping("/{id}/deactivate")
    public String deactivateLink(@PathVariable Long id) {
        submissionLinkService.deactivateLink(id);
        return "redirect:/links";
    }

    /**
     * View all submissions for a specific link
     */
    @GetMapping("/{id}/submissions")
    public String viewLinkSubmissions(@PathVariable Long id, Model model) {
        Optional<SubmissionLink> linkOpt = submissionLinkService.getLinkById(id);

        if (linkOpt.isEmpty()) {
            return "redirect:/links";
        }

        SubmissionLink link = linkOpt.get();
        List<ParishionerSubmission> submissions = submissionService.getSubmissionsForLink(id);

        model.addAttribute("link", link);
        model.addAttribute("submissions", submissions);

        return "links/view-submissions";
    }
}
