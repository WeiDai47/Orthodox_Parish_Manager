package com.example.orthodox_prm.service;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Household;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.HouseholdRepository;
import com.example.orthodox_prm.repository.ParishionerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ParishionerRepository parishionerRepo;
    private final HouseholdRepository householdRepo;
    private final Random random = new Random();

    public DataInitializer(ParishionerRepository parishionerRepo, HouseholdRepository householdRepo) {
        this.parishionerRepo = parishionerRepo;
        this.householdRepo = householdRepo;
    }

    @Override
    public void run(String... args) {
        // Prevent duplicate data if using a persistent file-based DB
        if (parishionerRepo.count() > 0) {
            System.out.println("--- Data already exists. Skipping initialization. ---");
            return;
        }

        // Data Pools
        String[] lastNames = {"Papadopoulos", "Smith", "Johnson", "Giannopoulos", "Miller", "Kuznetsov", "Rossi", "Ivanov", "Dubois", "Garcia"};
        String[] firstNamesM = {"Spyridon", "John", "Mikhail", "Constantine", "Luke", "Nicholas", "Athanasios", "George", "Demetrios", "Basil"};
        String[] firstNamesF = {"Sophia", "Maria", "Elena", "Catherine", "Anna", "Zoe", "Theodora", "Irene", "Photini", "Olga"};
        String[] saints = {"St. Spyridon", "St. Nicholas", "St. Maria of Paris", "St. John Chrysostom", "St. Katherine", "St. Panteleimon"};

        // 1. Create Households first (Required for Parishioner foreign key)
        List<Household> households = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Household h = new Household();
            h.setFamilyName(lastNames[i % 10]);
            h.setCity("Peaster");
            h.setAddress((100 + i) + " Orthodox Way");
            households.add(householdRepo.save(h));
        }

        List<Parishioner> all = new ArrayList<>();

        // 2. Create 100 Parishioners (Basic Fields)
        for (int i = 0; i < 100; i++) {
            Parishioner p = new Parishioner();
            boolean isMale = (i % 2 == 0); // Alternate gender for easier marriage pairing later

            p.setFirstName(isMale ? firstNamesM[random.nextInt(10)] : firstNamesF[random.nextInt(10)]);
            p.setLastName(lastNames[random.nextInt(10)]);
            p.setBaptismalName(p.getFirstName());
            p.setPatronSaint(saints[random.nextInt(saints.length)]);

            // Fill Dates
            LocalDate birth = LocalDate.now().minusYears(18 + random.nextInt(60)).minusDays(random.nextInt(365));
            p.setBirthday(birth);
            p.setBaptismDate(birth.plusMonths(6 + random.nextInt(12)));
            p.setChrismationDate(p.getBaptismDate());

            // Name Day: Set 10% to "Today" to test the Dashboard
            p.setNameDay(i % 10 == 0 ? LocalDate.now() : LocalDate.now().plusMonths(random.nextInt(6)));

            // Status distribution
            if (i < 10) p.setStatus(MembershipStatus.DEPARTED);
            else if (i < 20) p.setStatus(MembershipStatus.CATECHUMEN);
            else if (i < 30) p.setStatus(MembershipStatus.VISITOR);
            else p.setStatus(MembershipStatus.MEMBER);

            p.setMaritalStatus(MaritalStatus.SINGLE); // Default
            p.setHousehold(households.get(random.nextInt(households.size())));

            // Save initial record to get an ID (Critical for relationships)
            all.add(parishionerRepo.save(p));
        }

        // 3. Establish Relationships (Object-to-Object Links)
        for (int i = 0; i < all.size(); i++) {
            Parishioner p = all.get(i);

            // Marriages: Pair up index 40-80
            if (i >= 40 && i < 80 && i % 2 == 0) {
                Parishioner spouse = all.get(i + 1);
                // Use your model's marry method which handles the symmetry logic
                p.marry(spouse);
                p.setMarriageDate(LocalDate.now().minusYears(random.nextInt(15)));
                spouse.setMarriageDate(p.getMarriageDate());

                // Wedding Sponsor (Koumbaros) - Pick an elder from the first 10
                p.setWeddingSponsor(all.get(random.nextInt(10)));

                parishionerRepo.save(p);
                parishionerRepo.save(spouse);
            }

            // Godparents: Assign to the younger half of the list
            if (i > 50) {
                // Assign from the first 20 parishioners (the "elders")
                p.assignGodfather(all.get(random.nextInt(20)));
                p.assignGodmother(all.get(i % 2 == 0 ? 21 : 22)); // Just a mix
                parishionerRepo.save(p);
            }
        }

        System.out.println("--- 100 Parishioners Successfully Initialized with Zero Errors ---");
    }
}