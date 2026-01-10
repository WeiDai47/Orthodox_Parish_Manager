package com.example.orthodox_prm.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Household {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "household_id")
    private Long id;

    private String familyName; // e.g., "The Papadopoulos Family"
    private String address;
    private String city;
    private String phoneNumber;
    private String email;

    @OneToMany(mappedBy = "household")
    private List<Parishioner> members;

    @OneToMany(mappedBy = "household", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Note> notes = new ArrayList<>();
}