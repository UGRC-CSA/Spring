package com.open.spring.mvc.assess;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Entity
@Table(name = "peer_assessment")
@Getter
@Setter
@NoArgsConstructor
public class PeerAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "peer_assessment_gen")
    @SequenceGenerator(name = "peer_assessment_gen", sequenceName = "peer_assessment_seq", allocationSize = 1)
    private Long id;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "assessor_id")
    private Long assessorId;

    @Column(name = "assessed_person_id")
    private Long assessedPersonId;

    @Column
    private Integer rating; // 1-5 stars

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Constructors
    public PeerAssessment(Long assignmentId, Long submissionId, Long assessorId, 
                         Long assessedPersonId, Integer rating, String feedback) {
        this.assignmentId = assignmentId;
        this.submissionId = submissionId;
        this.assessorId = assessorId;
        this.assessedPersonId = assessedPersonId;
        this.rating = rating;
        this.feedback = feedback;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters (Lombok @Data handles these, but kept explicit for clarity)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getAssessorId() {
        return assessorId;
    }

    public void setAssessorId(Long assessorId) {
        this.assessorId = assessorId;
    }

    public Long getAssessedPersonId() {
        return assessedPersonId;
    }

    public void setAssessedPersonId(Long assessedPersonId) {
        this.assessedPersonId = assessedPersonId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "PeerAssessment{" +
                "id=" + id +
                ", assignmentId=" + assignmentId +
                ", submissionId=" + submissionId +
                ", assessorId=" + assessorId +
                ", assessedPersonId=" + assessedPersonId +
                ", rating=" + rating +
                ", feedback='" + feedback + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
