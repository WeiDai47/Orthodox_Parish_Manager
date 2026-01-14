package com.example.orthodox_prm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentEmail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sent_email_id")
    private Long id;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    // Comma-separated list of recipient emails
    @Column(name = "recipients", columnDefinition = "TEXT", nullable = false)
    private String recipients;

    // How many recipients received the email
    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount;

    // INDIVIDUAL or GROUP_BCC
    @Column(name = "send_mode", nullable = false)
    private String sendMode;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    // Who sent it (username from OAuth)
    @Column(name = "sent_by", nullable = false)
    private String sentBy;

    // Optional: filter criteria used (e.g., "MEMBER,CATECHUMEN")
    @Column(name = "filter_criteria")
    private String filterCriteria;

    // Track if there were any failures
    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
        if (success == null) {
            success = true;
        }
    }
}
