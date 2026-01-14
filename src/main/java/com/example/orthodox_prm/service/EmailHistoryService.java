package com.example.orthodox_prm.service;

import com.example.orthodox_prm.model.SentEmail;
import com.example.orthodox_prm.repository.SentEmailRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailHistoryService {

    private final SentEmailRepository sentEmailRepository;

    public EmailHistoryService(SentEmailRepository sentEmailRepository) {
        this.sentEmailRepository = sentEmailRepository;
    }

    /**
     * Save sent email to history
     */
    public SentEmail saveSentEmail(String subject, String body, List<String> recipients,
                                   String sendMode, String filterCriteria) {
        SentEmail email = new SentEmail();
        email.setSubject(subject);
        email.setBody(body);
        email.setRecipients(String.join(",", recipients));
        email.setRecipientCount(recipients.size());
        email.setSendMode(sendMode);
        email.setSentBy(getCurrentUsername());
        email.setFilterCriteria(filterCriteria);
        email.setSuccess(true);

        return sentEmailRepository.save(email);
    }

    /**
     * Save failed email attempt
     */
    public SentEmail saveFailedEmail(String subject, String body, List<String> recipients,
                                     String sendMode, String errorMessage) {
        SentEmail email = new SentEmail();
        email.setSubject(subject);
        email.setBody(body);
        email.setRecipients(String.join(",", recipients));
        email.setRecipientCount(recipients.size());
        email.setSendMode(sendMode);
        email.setSentBy(getCurrentUsername());
        email.setSuccess(false);
        email.setErrorMessage(errorMessage);

        return sentEmailRepository.save(email);
    }

    /**
     * Get email history sorted by most recent
     */
    public List<SentEmail> getEmailHistory() {
        return sentEmailRepository.findAll(Sort.by(Sort.Direction.DESC, "sentAt"));
    }

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OAuth2User) {
            OAuth2User oAuth2User = (OAuth2User) auth.getPrincipal();
            Object email = oAuth2User.getAttribute("email");
            return email != null ? email.toString() : oAuth2User.getName();
        }

        return auth != null ? auth.getName() : "anonymous";
    }
}
