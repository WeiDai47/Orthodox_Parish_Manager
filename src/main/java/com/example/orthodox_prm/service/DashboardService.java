package com.example.orthodox_prm.service;

import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.repository.HouseholdRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardService {

    private final ParishionerRepository parishionerRepo;
    private final HouseholdRepository householdRepo;

    public DashboardService(ParishionerRepository parishionerRepo, HouseholdRepository householdRepo) {
        this.parishionerRepo = parishionerRepo;
        this.householdRepo = householdRepo;
    }

    public Map<String, Object> getPriestStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMembers", parishionerRepo.count());
        stats.put("totalHouseholds", householdRepo.count());

        // Find everyone with a Name Day today
        stats.put("todaysNameDays", parishionerRepo.findByNameDay(LocalDate.now()));

        return stats;
    }
}