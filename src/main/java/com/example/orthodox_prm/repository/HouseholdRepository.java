package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.model.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long> {

    // Find a household by the family name (e.g., "Papadopoulos")
    List<Household> findByFamilyNameContainingIgnoreCase(String familyName);

    // Find households in a specific city
    List<Household> findByCityIgnoreCase(String city);
}