package com.open.spring.mvc.assignments;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignmentSubmissionsDTO {
    private Long id;
    private Map<String, Object> content;
    private String comment;
    private Integer technicalExcellence;
    private Integer communication;
    private Integer workHabits;
    private Integer aiOrchestration;
    private String selfAssessmentReflection;

    public AssignmentSubmissionsDTO(AssignmentSubmission submission) {
        this.id = submission.getId();
        this.content = submission.getContent();
        this.comment = submission.getComment();
        this.technicalExcellence = submission.getTechnicalExcellence();
        this.communication = submission.getCommunication();
        this.workHabits = submission.getWorkHabits();
        this.aiOrchestration = submission.getAiOrchestration();
        this.selfAssessmentReflection = submission.getSelfAssessmentReflection();
    }
}