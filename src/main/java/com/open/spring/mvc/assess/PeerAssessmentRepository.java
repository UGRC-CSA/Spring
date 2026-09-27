package com.open.spring.mvc.assess;

import com.open.spring.mvc.assess.PeerAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PeerAssessmentRepository extends JpaRepository<PeerAssessment, Long> {
    List<PeerAssessment> findByAssignmentId(Long assignmentId);
    List<PeerAssessment> findBySubmissionId(Long submissionId);
    List<PeerAssessment> findByAssessorId(Long assessorId);
    List<PeerAssessment> findByAssessedPersonId(Long assessedPersonId);
    List<PeerAssessment> findByAssignmentIdAndAssessedPersonId(Long assignmentId, Long assessedPersonId);
}