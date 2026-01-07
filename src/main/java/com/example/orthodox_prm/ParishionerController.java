package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.ParishionerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/parishioners") // This prefixes all routes below with /parishioners
public class ParishionerController {

    private final ParishionerRepository parishionerRepository;

    public ParishionerController(ParishionerRepository parishionerRepository) {
        this.parishionerRepository = parishionerRepository;
    }

    // Handles: GET /parishioners
    @GetMapping
    public String list(@RequestParam(required = false) String search, Model model) {
        List<Parishioner> list;
        if (search != null && !search.isEmpty()) {
            list = parishionerRepository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrBaptismalNameContainingIgnoreCaseOrHousehold_FamilyNameContainingIgnoreCase(
                    search, search, search, search
            );
        } else {
            list = parishionerRepository.findAll();
        }
        model.addAttribute("parishioners", list);
        model.addAttribute("searchQuery", search);
        return "parishioner-list";
    }

    // Handles: GET /parishioners/edit/{id}
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        // THIS IS KEY: Fetch everyone so the dropdowns can show names
        List<Parishioner> allParishioners = parishionerRepository.findAll();

        model.addAttribute("parishioner", p);
        model.addAttribute("allParishioners", allParishioners);
        model.addAttribute("allStatuses", MembershipStatus.values());
        model.addAttribute("allMaritalStatuses", MaritalStatus.values());

        return "edit-parishioner";
    }

    // Handles: POST /parishioners/update
    @PostMapping("/update")
    public String update(@ModelAttribute Parishioner parishioner) {
        parishionerRepository.save(parishioner);
        return "redirect:/parishioners";
    }
}