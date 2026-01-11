package com.example.orthodox_prm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipientDTO {
    private Long parishionerId;
    private String fullName;
    private String email;
    private String householdName;
    private String status;
}
