package com.example.orthodox_prm;

import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.DashboardService;
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

    public DashboardController(DashboardService dashboardService, ParishionerRepository parishionerRepository) {
        this.dashboardService = dashboardService;
        this.parishionerRepository = parishionerRepository;
    }


    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        model.addAttribute("stats", dashboardService.getPriestStats());
        return "dashboard"; // This looks for src/main/resources/templates/dashboard.html
    }


}