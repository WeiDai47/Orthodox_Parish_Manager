package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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
        if (searchName != null && !searchName.trim().isEmpty()) {
            parishioners = parishionerRepository.searchBySecularName(searchName.trim(), sort);
        } else if (searchBaptismal != null && !searchBaptismal.trim().isEmpty()) {
            parishioners = parishionerRepository.searchByBaptismalName(searchBaptismal.trim(), sort);
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
    @Transactional
    public String update(@ModelAttribute Parishioner parishioner,
                         @RequestParam(required = false) Long spouseId,
                         @RequestParam(required = false) Long godfatherId,
                         @RequestParam(required = false) Long godmotherId,
                         @RequestParam(required = false) Long weddingSponsorId,
                         @RequestParam(required = false) Long householdId,
                         @RequestParam(required = false) String newHouseholdName,
                         @RequestParam(required = false) String address,
                         @RequestParam(required = false) String city) {

        // 1. Fetch the existing state from DB to identify the current spouse before changes
        Parishioner existingRecord = parishionerRepository.findById(parishioner.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid parishioner Id:" + parishioner.getId()));

        // Helper method to check if any household data is provided
        boolean hasHouseholdData = (address != null && !address.trim().isEmpty()) ||
                (city != null && !city.trim().isEmpty());

        // 1.5. HANDLE HOUSEHOLD MANAGEMENT
        if (newHouseholdName != null && !newHouseholdName.trim().isEmpty()) {
            // Create new household
            Household newHousehold = new Household();
            newHousehold.setFamilyName(newHouseholdName);
            newHousehold.setAddress(address);
            newHousehold.setCity(city);
            Household savedHousehold = householdRepository.save(newHousehold);
            parishioner.setHousehold(savedHousehold);
        }
        else if (householdId != null) {
            // Assign to existing household
            Household existingHousehold = householdRepository.findById(householdId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid household Id:" + householdId));
            parishioner.setHousehold(existingHousehold);
        }
        else {
            // Keep current household but update its details
            if (parishioner.getHousehold() != null) {
                Household currentHousehold = parishioner.getHousehold();
                // Only update if household already exists in DB (not a new shallow reference)
                if (currentHousehold.getId() != null) {
                    currentHousehold = householdRepository.findById(currentHousehold.getId())
                            .orElse(currentHousehold);

                    // Update address fields only
                    currentHousehold.setAddress(address);
                    currentHousehold.setCity(city);
                    householdRepository.save(currentHousehold);
                    parishioner.setHousehold(currentHousehold);
                }
            } else if (hasHouseholdData) {
                // No household assigned and none selected - create one if address or city provided
                Household newHousehold = new Household();
                newHousehold.setFamilyName(parishioner.getLastName() + " Family");
                newHousehold.setAddress(address);
                newHousehold.setCity(city);
                Household savedHousehold = householdRepository.save(newHousehold);
                parishioner.setHousehold(savedHousehold);
            }
        }

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
            // Clear old spouse relationship if changing spouse
            if (existingRecord.getSpouse() != null && !existingRecord.getSpouse().getId().equals(spouseId)) {
                Parishioner oldSpouse = existingRecord.getSpouse();
                oldSpouse.setSpouse(null);
                parishionerRepository.save(oldSpouse);
            }

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
            // Remove from old godfather's list if changing
            if (existingRecord.getGodfather() != null && !existingRecord.getGodfather().getId().equals(godfatherId)) {
                existingRecord.getGodfather().getChildrenAsGodfather().remove(existingRecord);
                parishionerRepository.save(existingRecord.getGodfather());
            }
            // Add to new godfather's list
            Parishioner newGodfather = parishionerRepository.findById(godfatherId).orElse(null);
            if (newGodfather != null) {
                parishioner.setGodfather(newGodfather);
                if (!newGodfather.getChildrenAsGodfather().contains(parishioner)) {
                    newGodfather.getChildrenAsGodfather().add(parishioner);
                    parishionerRepository.save(newGodfather);
                }
            }
            parishioner.setManualGodfatherName(null);
        }

        if (godmotherId != null) {
            // Remove from old godmother's list if changing
            if (existingRecord.getGodmother() != null && !existingRecord.getGodmother().getId().equals(godmotherId)) {
                existingRecord.getGodmother().getChildrenAsGodmother().remove(existingRecord);
                parishionerRepository.save(existingRecord.getGodmother());
            }
            // Add to new godmother's list
            Parishioner newGodmother = parishionerRepository.findById(godmotherId).orElse(null);
            if (newGodmother != null) {
                parishioner.setGodmother(newGodmother);
                if (!newGodmother.getChildrenAsGodmother().contains(parishioner)) {
                    newGodmother.getChildrenAsGodmother().add(parishioner);
                    parishionerRepository.save(newGodmother);
                }
            }
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
    @Transactional
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
        List<Parishioner> weddingSponsoredPeople = parishionerRepository.findByWeddingSponsor_Id(id);
        for (Parishioner sponsored : weddingSponsoredPeople) {
            sponsored.setWeddingSponsor(null);
            parishionerRepository.save(sponsored);
        }

        // 5. Handle orphaned households
        Household household = p.getHousehold();

        // 6. Finally, delete the record
        parishionerRepository.delete(p);

        // 7. Check if household is now empty and delete it if so
        if (household != null) {
            List<Parishioner> householdMembers = parishionerRepository.findByHousehold_Id(household.getId());
            if (householdMembers.isEmpty()) {
                householdRepository.delete(household);
            }
        }

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

    // Handles: GET /parishioners/add
    @GetMapping("/add")
    public String showAddForm(Model model) {
        Parishioner newParishioner = new Parishioner();
        newParishioner.setStatus(MembershipStatus.VISITOR);

        model.addAttribute("parishioner", newParishioner);
        model.addAttribute("allStatuses", MembershipStatus.values());
        model.addAttribute("allMaritalStatuses", MaritalStatus.values());

        return "add-parishioner";
    }

    // Handles: POST /parishioners/add
    @PostMapping("/add")
    @Transactional
    public String addMember(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam(required = false) String nameSuffix,
            @RequestParam(required = false) String baptismalName,
            @RequestParam(required = false) String patronSaint,
            @RequestParam(required = false) String maritalStatusStr,
            @RequestParam(required = false) String manualSpouseName,
            @RequestParam(required = false) String manualSponsorName,
            @RequestParam(required = false) LocalDate birthday,
            @RequestParam(required = false) LocalDate baptismDate,
            @RequestParam(required = false) LocalDate chrismationDate,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String spouseFirstName,
            @RequestParam(required = false) String spouseLastName,
            @RequestParam(required = false) String spouseEmail,
            @RequestParam(required = false) String spousePhoneNumber,
            @RequestParam(required = false) String[] childNames,
            @RequestParam(required = false) String[] childBirthdays,
            @RequestParam(defaultValue = "false") boolean isOrthodox,
            @RequestParam(defaultValue = "VISITOR") String membershipStatusStr) {

        // Validate required fields
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }

        // Trim all string inputs to prevent whitespace issues
        firstName = firstName.trim();
        lastName = lastName.trim();

        // 1. CREATE HOUSEHOLD
        Household household = new Household();
        household.setFamilyName(lastName + " Family");
        if (address != null && !address.trim().isEmpty()) {
            household.setAddress(address.trim());
        }
        if (city != null && !city.trim().isEmpty()) {
            household.setCity(city.trim());
        }
        Household savedHousehold = householdRepository.save(household);

        // 2. CREATE PARENT PARISHIONER
        Parishioner parent = new Parishioner();
        parent.setFirstName(firstName);
        parent.setLastName(lastName);
        if (nameSuffix != null && !nameSuffix.trim().isEmpty()) {
            parent.setNameSuffix(nameSuffix.trim());
        }

        // Set membership status from form
        try {
            parent.setStatus(MembershipStatus.valueOf(membershipStatusStr));
        } catch (IllegalArgumentException e) {
            parent.setStatus(MembershipStatus.VISITOR);
        }

        parent.setBirthday(birthday);
        parent.setHousehold(savedHousehold);
        parent.setPhoneNumber(phoneNumber);
        parent.setEmail(email);

        // Trim optional string fields
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            parent.setPhoneNumber(phoneNumber.trim());
        }
        if (email != null && !email.trim().isEmpty()) {
            parent.setEmail(email.trim());
        }

        // Orthodox-specific fields (only if isOrthodox = true)
        if (isOrthodox) {
            if (baptismalName != null && !baptismalName.trim().isEmpty()) {
                parent.setBaptismalName(baptismalName.trim());
            }
            if (patronSaint != null && !patronSaint.trim().isEmpty()) {
                parent.setPatronSaint(patronSaint.trim());
            }
            parent.setBaptismDate(baptismDate);
            parent.setChrismationDate(chrismationDate);
            if (manualSponsorName != null && !manualSponsorName.trim().isEmpty()) {
                parent.setManualSponsorName(manualSponsorName.trim());
            }
        }

        // Marital status and spouse (for both Orthodox and Non-Orthodox)
        if (maritalStatusStr != null && !maritalStatusStr.trim().isEmpty()) {
            try {
                parent.setMaritalStatus(MaritalStatus.valueOf(maritalStatusStr));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid marital status: " + maritalStatusStr);
            }
        }
        if (manualSpouseName != null && !manualSpouseName.trim().isEmpty()) {
            parent.setManualSpouseName(manualSpouseName.trim());
        }

        parishionerRepository.save(parent);

        // 2.5 CREATE SPOUSE IF PROVIDED
        if (spouseFirstName != null && !spouseFirstName.trim().isEmpty() &&
            spouseLastName != null && !spouseLastName.trim().isEmpty()) {

            spouseFirstName = spouseFirstName.trim();
            spouseLastName = spouseLastName.trim();

            Parishioner spouse = new Parishioner();
            spouse.setFirstName(spouseFirstName);
            spouse.setLastName(spouseLastName);

            // Trim optional spouse contact fields
            if (spouseEmail != null && !spouseEmail.trim().isEmpty()) {
                spouse.setEmail(spouseEmail.trim());
            }
            if (spousePhoneNumber != null && !spousePhoneNumber.trim().isEmpty()) {
                spouse.setPhoneNumber(spousePhoneNumber.trim());
            }
            spouse.setHousehold(savedHousehold); // Same household as parent
            spouse.setStatus(MembershipStatus.VISITOR);
            spouse.setMaritalStatus(MaritalStatus.MARRIED);

            // Link spouse to parent
            parent.setSpouse(spouse);
            spouse.setSpouse(parent);

            Parishioner savedSpouse = parishionerRepository.save(spouse);
            parishionerRepository.save(parent); // Save parent with spouse link
        }

        // 3. CREATE CHILDREN (if any)
        if (childNames != null && childNames.length > 0) {
            for (int i = 0; i < childNames.length; i++) {
                if (childNames[i] != null && !childNames[i].trim().isEmpty()) {
                    Parishioner child = new Parishioner();

                    // Split child name into first/last (assume "FirstName LastName" format)
                    String[] nameParts = childNames[i].trim().split("\\s+", 2);

                    // Ensure first name is not empty
                    if (nameParts.length == 0 || nameParts[0].trim().isEmpty()) {
                        throw new IllegalArgumentException("Child name cannot be empty at index " + i);
                    }

                    String childFirstName = nameParts[0].trim();
                    String childLastName = (nameParts.length > 1 && !nameParts[1].trim().isEmpty())
                            ? nameParts[1].trim()
                            : lastName;  // Falls back to parent's last name

                    child.setFirstName(childFirstName);
                    child.setLastName(childLastName);

                    child.setStatus(MembershipStatus.VISITOR);
                    child.setHousehold(savedHousehold);

                    // Parse child birthday if provided - with error handling
                    if (childBirthdays != null && i < childBirthdays.length
                            && childBirthdays[i] != null && !childBirthdays[i].trim().isEmpty()) {
                        try {
                            child.setBirthday(LocalDate.parse(childBirthdays[i]));
                        } catch (java.time.format.DateTimeParseException e) {
                            throw new IllegalArgumentException("Invalid date format for child birthday at index " + i + ": " + childBirthdays[i]);
                        }
                    }

                    parishionerRepository.save(child);
                }
            }
        }

        return "redirect:/parishioners";
    }


}