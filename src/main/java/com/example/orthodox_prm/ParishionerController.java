package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/parishioners")
public class ParishionerController {

    private final ParishionerRepository parishionerRepository;
    // 1. ADD THIS FIELD
    private final HouseholdRepository householdRepository;

    // 2. UPDATE CONSTRUCTOR TO INCLUDE BOTH
    public ParishionerController(ParishionerRepository parishionerRepository, HouseholdRepository householdRepository) {
        this.parishionerRepository = parishionerRepository;
        this.householdRepository = householdRepository;
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

        List<Parishioner> allParishioners = parishionerRepository.findAll();
        // ADD THIS LINE
        List<Household> allHouseholds = householdRepository.findAll();

        model.addAttribute("parishioner", p);
        model.addAttribute("allParishioners", allParishioners);
        model.addAttribute("allHouseholds", allHouseholds); // AND THIS LINE
        model.addAttribute("allStatuses", MembershipStatus.values());
        model.addAttribute("allMaritalStatuses", MaritalStatus.values());

        return "edit-parishioner";
    }

    // Handles: POST /parishioners/update
    @PostMapping("/update")
    public String update(@ModelAttribute Parishioner parishioner,
                         @RequestParam(required = false) Long spouseId,
                         @RequestParam(required = false) Long godfatherId,
                         @RequestParam(required = false) Long godmotherId,
                         @RequestParam(required = false) Long weddingSponsorId) {

        // 1. BI-DIRECTIONAL SPOUSE LOGIC
        // If an internal member is selected as a spouse
        if (spouseId != null) {
            Parishioner internalSpouse = parishionerRepository.findById(spouseId).orElse(null);
            if (internalSpouse != null) {
                parishioner.setSpouse(internalSpouse);
                parishioner.setManualSpouseName(null); // Clear manual if member is picked
                parishioner.setMaritalStatus(MaritalStatus.MARRIED);

                // MIRROR: Update the spouse's record to point back to this person
                internalSpouse.setSpouse(parishioner);
                internalSpouse.setMaritalStatus(MaritalStatus.MARRIED);
                parishionerRepository.save(internalSpouse);
            }
        }

        // 2. GODCHILDREN / SPIRITUAL PARENT LOGIC
        // The relationship is "owned" by the child, but we clear manual entries if a member is picked
        if (godfatherId != null) {
            parishioner.setGodfather(parishionerRepository.findById(godfatherId).orElse(null));
            parishioner.setManualGodfatherName(null);
        }

        if (godmotherId != null) {
            parishioner.setGodmother(parishionerRepository.findById(godmotherId).orElse(null));
            parishioner.setManualGodmotherName(null);
        }

        // 3. WEDDING SPONSOR (KOUMBARO) LOGIC
        if (weddingSponsorId != null) {
            parishioner.setWeddingSponsor(parishionerRepository.findById(weddingSponsorId).orElse(null));
            parishioner.setManualSponsorName(null);
        }

        parishionerRepository.save(parishioner);
        return "redirect:/parishioners";
    }

}