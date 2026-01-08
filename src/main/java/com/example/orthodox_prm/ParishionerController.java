package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    // Inside ParishionerController.java

    @GetMapping
    public String list(@RequestParam(required = false) String searchName,
                       @RequestParam(required = false) String searchBaptismal,
                       @RequestParam(defaultValue = "lastName") String sortField,
                       @RequestParam(defaultValue = "asc") String sortDir,
                       Model model) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
        List<Parishioner> parishioners;

        // Logic to decide which search to run
        if (searchName != null && !searchName.isEmpty()) {
            parishioners = parishionerRepository.searchBySecularName(searchName, sort);
        } else if (searchBaptismal != null && !searchBaptismal.isEmpty()) {
            parishioners = parishionerRepository.searchByBaptismalName(searchBaptismal, sort);
        } else {
            parishioners = parishionerRepository.findAll(sort);
        }

        model.addAttribute("parishioners", parishioners);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");
        return "parishioner-list";
    }

    // Handles: GET /parishioners/edit/{id}
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        // Basic Data for Dropdowns
        model.addAttribute("parishioner", p);
        model.addAttribute("allParishioners", parishionerRepository.findAll());
        model.addAttribute("allHouseholds", householdRepository.findAll());
        model.addAttribute("allStatuses", MembershipStatus.values());
        model.addAttribute("allMaritalStatuses", MaritalStatus.values());

        // NEW: Logic for the Household Sidebar
        if (p.getHousehold() != null) {
            // Fetch everyone in this household
            List<Parishioner> family = parishionerRepository.findByHousehold_Id(p.getHousehold().getId());

            // Remove the person currently being edited from the sidebar list
            family.removeIf(member -> member.getId().equals(id));

            model.addAttribute("family", family);
        }

        return "edit-parishioner";
    }

    // Handles: POST /parishioners/update
    @PostMapping("/update")
    public String update(@ModelAttribute Parishioner parishioner,
                         @RequestParam(required = false) Long spouseId,
                         @RequestParam(required = false) Long godfatherId,
                         @RequestParam(required = false) Long godmotherId,
                         @RequestParam(required = false) Long weddingSponsorId) {

        // 1. Fetch the existing state from DB to identify the current spouse before changes
        Parishioner existingRecord = parishionerRepository.findById(parishioner.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + parishioner.getId()));

        // 2. HANDLE DIVORCE LOGIC
        if (parishioner.getMaritalStatus() == MaritalStatus.DIVORCED) {
            Parishioner formerSpouse = existingRecord.getSpouse();

            if (formerSpouse != null) {
                // Sever the link on the former spouse's side
                formerSpouse.setSpouse(null);
                formerSpouse.setMaritalStatus(MaritalStatus.DIVORCED);
                parishionerRepository.save(formerSpouse);
            }

            // Sever the link on the current parishioner's side
            parishioner.setSpouse(null);
            parishioner.setManualSpouseName(null);
            // We keep the marriageDate and weddingSponsor for historical/canonical records
            // unless you specifically wish to nullify them here.
        }

        // 3. BI-DIRECTIONAL MARRIAGE LOGIC
        else if (spouseId != null) {
            Parishioner internalSpouse = parishionerRepository.findById(spouseId).orElse(null);
            if (internalSpouse != null) {
                parishioner.setSpouse(internalSpouse);
                parishioner.setManualSpouseName(null);
                parishioner.setMaritalStatus(MaritalStatus.MARRIED);

                // Mirror: Update the spouse's record to point back
                internalSpouse.setSpouse(parishioner);
                internalSpouse.setMaritalStatus(MaritalStatus.MARRIED);
                parishionerRepository.save(internalSpouse);
            }
        }

        // 4. SPIRITUAL PARENT LOGIC
        if (godfatherId != null) {
            parishioner.setGodfather(parishionerRepository.findById(godfatherId).orElse(null));
            parishioner.setManualGodfatherName(null);
        }

        if (godmotherId != null) {
            parishioner.setGodmother(parishionerRepository.findById(godmotherId).orElse(null));
            parishioner.setManualGodmotherName(null);
        }

        // 5. WEDDING SPONSOR (KOUMBARO) LOGIC
        if (weddingSponsorId != null) {
            parishioner.setWeddingSponsor(parishionerRepository.findById(weddingSponsorId).orElse(null));
            parishioner.setManualSponsorName(null);
        }

        parishionerRepository.save(parishioner);
        return "redirect:/parishioners";
    }
    @GetMapping("/delete/{id}")
    public String deleteParishioner(@PathVariable Long id) {
        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + id));

        // 1. Handle Spouse Link
        if (p.getSpouse() != null) {
            Parishioner spouse = p.getSpouse();
            spouse.setSpouse(null);
            // Optional: Change spouse status to SINGLE or WIDOWED depending on your parish policy
            parishionerRepository.save(spouse);
        }

        // 2. Handle Spiritual Children (as Godfather)
        List<Parishioner> godchildrenAsFather = parishionerRepository.findByGodfather_Id(id);
        for (Parishioner child : godchildrenAsFather) {
            child.setGodfather(null);
            parishionerRepository.save(child);
        }

        // 3. Handle Spiritual Children (as Godmother)
        List<Parishioner> godchildrenAsMother = parishionerRepository.findByGodmother_Id(id);
        for (Parishioner child : godchildrenAsMother) {
            child.setGodmother(null);
            parishionerRepository.save(child);
        }

        // 4. Handle Wedding Sponsor Link (Koumbaros)
        // You may need to add findByWeddingSponsor_Id to your repository first

        // 5. Finally, delete the record
        parishionerRepository.delete(p);

        return "redirect:/parishioners";
    }

    @PostMapping("/departed/{id}")
    public String markAsDeparted(@PathVariable Long id) {
        Parishioner p = parishionerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ID"));

        // 1. Set Status and Date
        p.setStatus(MembershipStatus.DEPARTED);
        p.setDeathDate(LocalDate.now());

        // 2. Handle Spouse (Widowhood)
        if (p.getSpouse() != null) {
            Parishioner spouse = p.getSpouse();
            spouse.setMaritalStatus(MaritalStatus.WIDOWED);
            spouse.setSpouse(null); // Clear the link but keep the status
            parishionerRepository.save(spouse);

            p.setSpouse(null); // Clear the link on the departed side too
        }

        parishionerRepository.save(p);
        return "redirect:/parishioners/edit/" + id;
    }


}