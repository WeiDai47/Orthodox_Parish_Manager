package com.example.orthodox_prm;

import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.DashboardService;
import com.example.orthodox_prm.service.SubmissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final ParishionerRepository parishionerRepository;
    private final SubmissionService submissionService;

    public DashboardController(DashboardService dashboardService, ParishionerRepository parishionerRepository, SubmissionService submissionService) {
        this.dashboardService = dashboardService;
        this.parishionerRepository = parishionerRepository;
        this.submissionService = submissionService;
    }

    /**
     * Root path redirects to dashboard (will redirect to login if not authenticated)
     */
    @GetMapping("/")
    public String redirectRoot() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('PRIEST','SECRETARY','VIEWER')")
    public String showDashboard(Model model) {
        model.addAttribute("stats", dashboardService.getPriestStats());
        model.addAttribute("pendingSubmissionCount", submissionService.getPendingSubmissionCount());
        return "dashboard"; // This looks for src/main/resources/templates/dashboard.html
    }


}
