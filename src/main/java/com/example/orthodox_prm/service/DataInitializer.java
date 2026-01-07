package com.example.orthodox_prm.service;

import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;


@Component
public class DataInitializer implements CommandLineRunner {

    private final ParishionerRepository parishionerRepo;
    private final HouseholdRepository householdRepo;

    public DataInitializer(ParishionerRepository parishionerRepo, HouseholdRepository householdRepo) {
        this.parishionerRepo = parishionerRepo;
        this.householdRepo = householdRepo;
    }

    @Override
    public void run(String... args) {
        // 1. Create a Household
        Household h = new Household();
        h.setFamilyName("Papadopoulos");
        h.setCity("Peaster");
        householdRepo.save(h);

        // 2. Create the Father of the house
        Parishioner p1 = new Parishioner();
        p1.setFirstName("Spyridon");
        p1.setLastName("Papadopoulos");
        p1.setBaptismalName("Spyridon");
        p1.setPatronSaint("St. Spyridon");
        p1.setNameDay(LocalDate.now()); // Setting this to TODAY for the dashboard test
        p1.setStatus(MembershipStatus.MEMBER);
        p1.setHousehold(h);
        parishionerRepo.save(p1);

        // 3. Create a Godchild
        Parishioner p2 = new Parishioner();
        p2.setFirstName("John");
        p2.setLastName("Smith");
        p2.setBaptismalName("Ioannis");
        p2.setStatus(MembershipStatus.MEMBER);
        p2.assignGodfather(p1); // Spiritual Kinship!
        parishionerRepo.save(p2);

        Parishioner p3 = new Parishioner();
        p3.setFirstName("Sophia");
        p3.setLastName("Papadopoulos");
        p3.setBaptismalName("placeholder");
        p3.setPatronSaint("St. Spyridon");
        p3.setNameDay(LocalDate.now()); // Setting this to TODAY for the dashboard test
        p3.setStatus(MembershipStatus.MEMBER);
        p3.setHousehold(h);
        parishionerRepo.save(p3);

        System.out.println("--- Sample Parish Data Loaded ---");
    }
}