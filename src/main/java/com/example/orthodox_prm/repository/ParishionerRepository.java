package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.model.Parishioner;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ParishionerRepository extends JpaRepository<Parishioner, Long> {

    // Find all parishioners who share a specific Patron Saint
    List<Parishioner> findByPatronSaint(String patronSaint);

    // Find everyone whose Name Day is on a specific date (for the weekly bulletin)
    List<Parishioner> findByNameDay(LocalDate date);

    // Find all Godchildren of a specific person
    // TO THIS (Notice the underscore or specific naming):
    List<Parishioner> findByGodfather_Id(Long godfatherId);
    List<Parishioner> findByGodmother_Id(Long godmotherId);
    // Search by baptismal name
    List<Parishioner> findByBaptismalNameIgnoreCase(String baptismalName);

    // Inside ParishionerRepository.java
    List<Parishioner> findByHousehold_Id(Long householdId);

    List<Parishioner> findByWeddingSponsor_Id(Long id);
    // Removed 'static' and the method body to allow Spring Data to implement it
    List<Parishioner> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrBaptismalNameContainingIgnoreCaseOrHousehold_FamilyNameContainingIgnoreCase(
            String lastName, String firstName, String baptismalName, String householdName, org.springframework.data.domain.Sort sort
    );
}