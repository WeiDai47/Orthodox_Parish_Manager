package com.example.orthodox_prm;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ParishionerFilterCriteria {
    private MembershipStatus status;
    private MaritalStatus maritalStatus;
    private LocalDate baptismDateStart;
    private LocalDate baptismDateEnd;
    private Integer nameDayMonth; // 1-12
    private Long sponsorId; // For Spiritual Kinship export
    private boolean missingBaptismDate; // Sacramental Eligibility
    private boolean groupByHousehold; // Mailing Labels
    private String format; // "excel" or "word"
}