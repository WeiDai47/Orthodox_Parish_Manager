package com.example.orthodox_prm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailPreviewDTO {
    private String subject;
    private String body;
    private List<String> recipients;
    private List<String> recipientNames;
    private List<String> missingEmails; // Parishioner names without emails
    private String sendMode; // "INDIVIDUAL" or "GROUP_BCC"
    private String filterCriteria; // e.g., "MEMBER,CATECHUMEN"
}
