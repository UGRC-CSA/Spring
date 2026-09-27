package com.open.spring.mvc.assess;

import com.open.spring.mvc.assess.PeerAssessment;
import com.open.spring.mvc.assess.PeerAssessmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/peer-assessments")
@CrossOrigin(origins = "http://localhost:3000")
public class PeerAssessmentController {

    @Autowired
    private PeerAssessmentRepository peerAssessmentRepository;

    /**
     * Submit a peer assessment
     */
    @PostMapping
    public ResponseEntity<PeerAssessment> submitPeerAssessment(@RequestBody PeerAssessment assessment) {
        PeerAssessment saved = peerAssessmentRepository.save(assessment);
        return ResponseEntity.ok(saved);
    }

    /**
     * Get assessments for a specific submission
     */
    @GetMapping("/submission/{submissionId}")
    public ResponseEntity<List<PeerAssessment>> getAssessmentsForSubmission(@PathVariable Long submissionId) {
        List<PeerAssessment> assessments = peerAssessmentRepository.findBySubmissionId(submissionId);
        return ResponseEntity.ok(assessments);
    }

    /**
     * Get assessments given by a person
     */
    @GetMapping("/assessor/{assessorId}")
    public ResponseEntity<List<PeerAssessment>> getAssessmentsByAssessor(@PathVariable Long assessorId) {
        List<PeerAssessment> assessments = peerAssessmentRepository.findByAssessorId(assessorId);
        return ResponseEntity.ok(assessments);
    }

    /**
     * Get assessments received by a person for a specific assignment
     */
    @GetMapping("/assignment/{assignmentId}/person/{personId}")
    public ResponseEntity<List<PeerAssessment>> getAssessmentsForPerson(
            @PathVariable Long assignmentId,
            @PathVariable Long personId) {
        List<PeerAssessment> assessments = peerAssessmentRepository
                .findByAssignmentIdAndAssessedPersonId(assignmentId, personId);
        return ResponseEntity.ok(assessments);
    }

    /**
     * Get all assessments for an assignment
     */
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<List<PeerAssessment>> getAssessmentsForAssignment(@PathVariable Long assignmentId) {
        List<PeerAssessment> assessments = peerAssessmentRepository.findByAssignmentId(assignmentId);
        return ResponseEntity.ok(assessments);
    }
}