package com.example.orthodox_prm.model;

import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.Enum.SubmissionStatus;
import com.example.orthodox_prm.Enum.SubmissionType;
import com.example.orthodox_prm.model.Parishioner;
import jakarta.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "parishioner_submission", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_submitted_at", columnList = "submitted_at")
})
public class ParishionerSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionType submissionType;

    @ManyToOne
    @JoinColumn(name = "submission_link_id", nullable = false)
    private SubmissionLink submissionLink;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    private LocalDateTime reviewedAt;

    @Column(length = 255)
    private String reviewedBy;

    @Column(columnDefinition = "TEXT")
    private String reviewNotes;

    @ManyToOne
    @JoinColumn(name = "target_parishioner_id")
    private Parishioner targetParishioner;

    // --- BASIC PARISHIONER DATA ---
    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 10)
    private String nameSuffix;

    private LocalDate birthday;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 100)
    private String email;

    // --- ORTHODOX FIELDS ---
    @Column(length = 100)
    private String baptismalName;

    @Column(length = 150)
    private String patronSaint;

    private LocalDate baptismDate;

    private LocalDate chrismationDate;

    @Enumerated(EnumType.STRING)
    private MembershipStatus membershipStatus;

    // --- MARRIAGE FIELDS ---
    @Enumerated(EnumType.STRING)
    private MaritalStatus maritalStatus;

    private LocalDate marriageDate;

    // --- RELATIONSHIP FIELDS (MANUAL TEXT ONLY) ---
    @Column(length = 100)
    private String manualSpouseName;

    @Column(length = 100)
    private String manualGodfatherName;

    @Column(length = 100)
    private String manualGodmotherName;

    @Column(length = 100)
    private String manualSponsorName;

    // --- SPOUSE CREATION FIELDS ---
    @Column(length = 100)
    private String spouseFirstName;

    @Column(length = 100)
    private String spouseLastName;

    @Column(length = 100)
    private String spouseEmail;

    @Column(length = 20)
    private String spousePhoneNumber;

    // --- ADDRESS ---
    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    // --- CHILDREN (stored as JSON string) ---
    @Column(columnDefinition = "TEXT")
    private String childrenJson;

    // --- ORTHODOX FLAG ---
    private Boolean isOrthodox;

    // Constructors
    public ParishionerSubmission() {
    }

    public ParishionerSubmission(SubmissionType submissionType, SubmissionLink submissionLink) {
        this.submissionType = submissionType;
        this.submissionLink = submissionLink;
        this.submittedAt = LocalDateTime.now();
        this.status = SubmissionStatus.PENDING;
        this.isOrthodox = false;
    }

    // Helper methods for children JSON serialization
    public static class ChildData {
        public String name;
        public LocalDate birthday;

        public ChildData() {
        }

        public ChildData(String name, LocalDate birthday) {
            this.name = name;
            this.birthday = birthday;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDate getBirthday() {
            return birthday;
        }

        public void setBirthday(LocalDate birthday) {
            this.birthday = birthday;
        }
    }

    public List<ChildData> getChildrenList() {
        if (childrenJson == null || childrenJson.isEmpty()) {
            return List.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return List.of(mapper.readValue(childrenJson, ChildData[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setChildrenList(List<ChildData> children) {
        if (children == null || children.isEmpty()) {
            this.childrenJson = null;
        } else {
            try {
                ObjectMapper mapper = new ObjectMapper();
                this.childrenJson = mapper.writeValueAsString(children);
            } catch (Exception e) {
                this.childrenJson = null;
            }
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SubmissionType getSubmissionType() {
        return submissionType;
    }

    public void setSubmissionType(SubmissionType submissionType) {
        this.submissionType = submissionType;
    }

    public SubmissionLink getSubmissionLink() {
        return submissionLink;
    }

    public void setSubmissionLink(SubmissionLink submissionLink) {
        this.submissionLink = submissionLink;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public Parishioner getTargetParishioner() {
        return targetParishioner;
    }

    public void setTargetParishioner(Parishioner targetParishioner) {
        this.targetParishioner = targetParishioner;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getNameSuffix() {
        return nameSuffix;
    }

    public void setNameSuffix(String nameSuffix) {
        this.nameSuffix = nameSuffix;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBaptismalName() {
        return baptismalName;
    }

    public void setBaptismalName(String baptismalName) {
        this.baptismalName = baptismalName;
    }

    public String getPatronSaint() {
        return patronSaint;
    }

    public void setPatronSaint(String patronSaint) {
        this.patronSaint = patronSaint;
    }

    public LocalDate getBaptismDate() {
        return baptismDate;
    }

    public void setBaptismDate(LocalDate baptismDate) {
        this.baptismDate = baptismDate;
    }

    public LocalDate getChrismationDate() {
        return chrismationDate;
    }

    public void setChrismationDate(LocalDate chrismationDate) {
        this.chrismationDate = chrismationDate;
    }

    public MembershipStatus getMembershipStatus() {
        return membershipStatus;
    }

    public void setMembershipStatus(MembershipStatus membershipStatus) {
        this.membershipStatus = membershipStatus;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public LocalDate getMarriageDate() {
        return marriageDate;
    }

    public void setMarriageDate(LocalDate marriageDate) {
        this.marriageDate = marriageDate;
    }

    public String getManualSpouseName() {
        return manualSpouseName;
    }

    public void setManualSpouseName(String manualSpouseName) {
        this.manualSpouseName = manualSpouseName;
    }

    public String getManualGodfatherName() {
        return manualGodfatherName;
    }

    public void setManualGodfatherName(String manualGodfatherName) {
        this.manualGodfatherName = manualGodfatherName;
    }

    public String getManualGodmotherName() {
        return manualGodmotherName;
    }

    public void setManualGodmotherName(String manualGodmotherName) {
        this.manualGodmotherName = manualGodmotherName;
    }

    public String getManualSponsorName() {
        return manualSponsorName;
    }

    public void setManualSponsorName(String manualSponsorName) {
        this.manualSponsorName = manualSponsorName;
    }

    public String getSpouseFirstName() {
        return spouseFirstName;
    }

    public void setSpouseFirstName(String spouseFirstName) {
        this.spouseFirstName = spouseFirstName;
    }

    public String getSpouseLastName() {
        return spouseLastName;
    }

    public void setSpouseLastName(String spouseLastName) {
        this.spouseLastName = spouseLastName;
    }

    public String getSpouseEmail() {
        return spouseEmail;
    }

    public void setSpouseEmail(String spouseEmail) {
        this.spouseEmail = spouseEmail;
    }

    public String getSpousePhoneNumber() {
        return spousePhoneNumber;
    }

    public void setSpousePhoneNumber(String spousePhoneNumber) {
        this.spousePhoneNumber = spousePhoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getChildrenJson() {
        return childrenJson;
    }

    public void setChildrenJson(String childrenJson) {
        this.childrenJson = childrenJson;
    }

    public Boolean getIsOrthodox() {
        return isOrthodox;
    }

    public void setIsOrthodox(Boolean isOrthodox) {
        this.isOrthodox = isOrthodox;
    }
}
