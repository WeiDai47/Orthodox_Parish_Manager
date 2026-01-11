package com.example.orthodox_prm.model;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Parishioner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parishioner_id")
    private Long id;


    // Inside Parishioner.java
    private LocalDate deathDate;
    // Legal Name
    private String firstName;
    private String lastName;

    // Orthodox Specifics
    private String baptismalName; // e.g., "Spyridon"
    private String patronSaint;   // e.g., "St. Spyridon the Wonderworker"
    private LocalDate nameDay;    // Dec 12th

    @Enumerated(EnumType.STRING)
    private MembershipStatus status;

    // Marriage status
    @Enumerated(EnumType.STRING)
    private MaritalStatus maritalStatus;

    @OneToOne
    @JoinColumn(name = "spouse_id")
    @ToString.Exclude // Pro-tip: Prevents infinite loops in Lombok's toString()
    private Parishioner spouse;

    private LocalDate marriageDate; // The Crowning Date

    // Changed to EAGER to prevent 500 errors on the edit page
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "wedding_sponsor_id")
    private Parishioner weddingSponsor; // The Koumbaros / Koumbara

    private LocalDate birthday;
    private LocalDate baptismDate;
    private LocalDate chrismationDate;

    // Relationships
    @ManyToOne
    @JoinColumn(name = "household_id")
    private Household household;

    // --- SPIRITUAL PARENTS ---

    // Changed to EAGER so these load immediately for the edit form
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "godfather_id")
    private Parishioner godfather;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "godmother_id")
    private Parishioner godmother;

    // --- SPIRITUAL CHILDREN (Godchildren) ---

    // Changed to EAGER so the godchildren list displays on the sponsor's edit page
    @OneToMany(mappedBy = "godfather", fetch = FetchType.EAGER)
    private List<Parishioner> childrenAsGodfather = new ArrayList<>();

    @OneToMany(mappedBy = "godmother", fetch = FetchType.EAGER)
    private List<Parishioner> childrenAsGodmother = new ArrayList<>();

    @OneToMany(mappedBy = "parishioner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Note> notes = new ArrayList<>();

    @OneToMany(mappedBy = "parishioner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventParticipant> eventParticipations = new ArrayList<>();

    // Manual Overrides for non-members
    private String manualSpouseName;
    private String manualGodfatherName;
    private String manualGodmotherName;
    private String manualSponsorName; // For the Wedding Sponsor (Koumbaros)

    // --- HELPER METHODS ---

    public void marry(Parishioner spouse) {
        // Prevent infinite recursion and null reference errors
        if (spouse == null || spouse.getId().equals(this.getId())) {
            throw new IllegalArgumentException("Invalid spouse: cannot marry null or self");
        }
        this.spouse = spouse;
        this.maritalStatus = MaritalStatus.MARRIED;
        // Symmetry: Ensure the other person is also marked as married to this person
        if (spouse.getSpouse() == null || !spouse.getSpouse().getId().equals(this.getId())) {
            spouse.spouse = this;
            spouse.maritalStatus = MaritalStatus.MARRIED;
        }
    }

    public void assignGodfather(Parishioner sponsor) {
        if (sponsor == null) {
            throw new IllegalArgumentException("Godfather cannot be null");
        }
        this.godfather = sponsor;
        // Prevent duplicates in the godchildren list
        if (!sponsor.getChildrenAsGodfather().contains(this)) {
            sponsor.getChildrenAsGodfather().add(this);
        }
    }

    public void assignGodmother(Parishioner sponsor) {
        if (sponsor == null) {
            throw new IllegalArgumentException("Godmother cannot be null");
        }
        this.godmother = sponsor;
        // Prevent duplicates in the godchildren list
        if (!sponsor.getChildrenAsGodmother().contains(this)) {
            sponsor.getChildrenAsGodmother().add(this);
        }
    }

    // Setter helpers for ID-based updates from the controller
    public void setHouseholdId(Long id) {
        if (id != null) {
            this.household = new Household();
            this.household.setId(id);
        }
    }

    public void setSpouseId(Long id) {
        if (id != null) {
            this.spouse = new Parishioner();
            this.spouse.setId(id);
        }
    }

    public void setGodfatherId(Long id) {
        if (id != null) {
            this.godfather = new Parishioner();
            this.godfather.setId(id);
        }
    }

    public void setGodmotherId(Long id) {
        if (id != null) {
            this.godmother = new Parishioner();
            this.godmother.setId(id);
        }
    }

    public void setWeddingSponsorId(Long id) {
        if (id != null) {
            this.weddingSponsor = new Parishioner();
            this.weddingSponsor.setId(id);
        }
    }
}