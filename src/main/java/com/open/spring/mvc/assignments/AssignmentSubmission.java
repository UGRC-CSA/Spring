package com.open.spring.mvc.assignments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.open.spring.mvc.groups.Submitter;
import com.open.spring.mvc.person.Person;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"assignedGraders"})
public class AssignmentSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Assignment assignment;

    @ManyToOne
    @JoinColumn(name = "submitter_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonManagedReference(value = "submitter-submissions")
    private Submitter submitter;

    @ManyToMany
    @JoinTable(
        name = "assignment_submission_graders",
        joinColumns = @JoinColumn(name = "submission_id"),
        inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private List<Person> assignedGraders;

    @Convert(converter = SubmissionContentConverter.class)
    @Column(columnDefinition = "text")
    private Map<String, Object> content;
    private Double grade;
    private String feedback;

    @Column(columnDefinition = "text")
    private String aiSummary;

    private Integer qualityScore;

    private String comment;

    private Long assignmentid;

    private Boolean isLate;

    // ========== SELF-ASSESSMENT (required on every submission) ==========
    // 1-5 ratings; range is enforced server-side in the controllers/upload
    // service rather than with a DB constraint, so older rows (submitted
    // before this feature existed) can keep these columns null.
    private Integer technicalExcellence;
    private Integer communication;
    private Integer workHabits;
    private Integer aiOrchestration;

    @Column(columnDefinition = "text")
    private String selfAssessmentReflection;

    public AssignmentSubmission(Assignment assignment, Submitter submitter, Map<String, Object> content, String comment, boolean isLate) {
        this.assignment = assignment;
        this.submitter = submitter;
        this.content = content;
        this.grade = null;
        this.feedback = null;
        this.comment = comment;
        this.assignmentid = assignment.getId();
        this.isLate = isLate;
    }

    // Getter for assignment_id (foreign key column)
    public Long getAssignmentId2() {
        return assignment != null ? assignment.getId() : null;
    }

    /**
     * Validates the required self-assessment fields shared by every submission
     * entry point. Returns a human-readable error message if something is
     * missing or out of range, or null if the values are acceptable.
     */
    public static String validateSelfAssessment(
            Integer technicalExcellence,
            Integer communication,
            Integer workHabits,
            Integer aiOrchestration,
            String selfAssessmentReflection) {
        if (!isValidRating(technicalExcellence)) {
            return "technicalExcellence is required and must be between 1 and 5";
        }
        if (!isValidRating(communication)) {
            return "communication is required and must be between 1 and 5";
        }
        if (!isValidRating(workHabits)) {
            return "workHabits is required and must be between 1 and 5";
        }
        if (!isValidRating(aiOrchestration)) {
            return "aiOrchestration is required and must be between 1 and 5";
        }
        if (selfAssessmentReflection == null || selfAssessmentReflection.trim().isEmpty()) {
            return "selfAssessmentReflection is required";
        }
        return null;
    }

    private static boolean isValidRating(Integer value) {
        return value != null && value >= 1 && value <= 5;
    }
}