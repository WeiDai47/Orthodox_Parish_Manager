package com.example.orthodox_prm.repository;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Parishioner;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ParishionerRepository extends JpaRepository<Parishioner, Long>,
        JpaSpecificationExecutor<Parishioner> {

    // Add this line
    long countByStatus(MembershipStatus status);

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
    // Add to ParishionerRepository.java
    List<Parishioner> findByStatusAndMaritalStatusAndBaptismDateBetween(
            MembershipStatus status,
            MaritalStatus maritalStatus,
            LocalDate start,
            LocalDate end
    );
    // 1. Searches First + Last name specifically
    @Query("SELECT p FROM Parishioner p WHERE " +
            "LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Parishioner> searchBySecularName(@Param("name") String name, Sort sort);

    // 2. Searches Baptismal name specifically
    @Query("SELECT p FROM Parishioner p WHERE " +
            "LOWER(p.baptismalName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Parishioner> searchByBaptismalName(@Param("name") String name, Sort sort);

    // Search for parishioner by first and last name (for UPDATE submissions)
    List<Parishioner> findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(String firstName, String lastName);
}