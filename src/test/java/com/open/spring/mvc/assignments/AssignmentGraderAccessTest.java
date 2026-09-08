package com.open.spring.mvc.assignments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import com.open.spring.mvc.groups.GroupsJpaRepository;
import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonJpaRepository;
import com.open.spring.mvc.person.PersonRole;

/**
 * Who may list and grade a homework's submissions, and who may pick graders.
 *
 * Team teach makes the students who taught a lesson the graders of its
 * homework. They are named on the Assignment's grader list. These tests pin
 * down that such a student can see and grade the submissions, that a student
 * who was not named cannot, that staff are unchanged, and that only staff can
 * name graders in the first place.
 */
public class AssignmentGraderAccessTest {

    @Mock private AssignmentSubmissionJPA submissionRepo;
    @Mock private AssignmentJpaRepository assignmentRepo;
    @Mock private PersonJpaRepository personRepo;
    @Mock private GroupsJpaRepository groupRepo;

    @InjectMocks private AssignmentSubmissionAPIController submissionController;
    @InjectMocks private AssignmentsApiController assignmentsController;

    private Person teacher;
    private Person grader;      // a student named on the assignment's grader list
    private Person bystander;   // a student who was not
    private Assignment assignment;
    private AssignmentSubmission submission;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        teacher = person(1L, "teacher", "ROLE_TEACHER");
        grader = person(2L, "grader", "ROLE_STUDENT");
        bystander = person(3L, "bystander", "ROLE_STUDENT");

        assignment = new Assignment();
        setId(assignment, 10L);
        assignment.setName("Chat and WebSockets homework");
        assignment.setAssignedGraders(new ArrayList<>(List.of(grader)));

        submission = new AssignmentSubmission();
        setId(submission, 100L);
        submission.setAssignment(assignment);
        submission.setAssignedGraders(new ArrayList<>());
        submission.setIsLate(false);

        when(personRepo.findByUid("teacher")).thenReturn(teacher);
        when(personRepo.findByUid("grader")).thenReturn(grader);
        when(personRepo.findByUid("bystander")).thenReturn(bystander);
        when(assignmentRepo.findById(10L)).thenReturn(Optional.of(assignment));
        when(submissionRepo.findById(100L)).thenReturn(Optional.of(submission));
        when(submissionRepo.findByAssignmentId(10L)).thenReturn(List.of(submission));
        when(submissionRepo.save(any(AssignmentSubmission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepo.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- the entity check itself -------------------------------------------

    @Test
    public void graderOnAssignmentCountsAsGrader() {
        assertTrue(submission.isAssignedGrader(grader));
        assertFalse(submission.isAssignedGrader(bystander));
        assertFalse(submission.isAssignedGrader(null));
    }

    @Test
    public void graderOnSubmissionStillCounts() {
        assignment.setAssignedGraders(new ArrayList<>());
        submission.setAssignedGraders(new ArrayList<>(List.of(bystander)));
        assertTrue(submission.isAssignedGrader(bystander));
        assertFalse(submission.isAssignedGrader(grader));
    }

    @Test
    public void matchesByIdNotByInstance() {
        Person sameRowDifferentObject = person(2L, "grader", "ROLE_STUDENT");
        assertTrue(submission.isAssignedGrader(sameRowDifferentObject));
    }

    // ---- listing submissions ------------------------------------------------

    @Test
    public void assignmentGraderSeesTheSubmissions_onBothListEndpoints() {
        ResponseEntity<?> a = submissionController.getSubmissionsByAssignment(10L, user("grader"));
        assertEquals(HttpStatus.OK, a.getStatusCode());
        assertEquals(1, ((List<?>) a.getBody()).size());

        ResponseEntity<?> b = assignmentsController.getSubmissions(10L, user("grader"));
        assertEquals(HttpStatus.OK, b.getStatusCode());
        assertEquals(1, ((List<?>) b.getBody()).size());
    }

    @Test
    public void bystanderSeesNothing_onBothListEndpoints() {
        ResponseEntity<?> a = submissionController.getSubmissionsByAssignment(10L, user("bystander"));
        assertEquals(0, ((List<?>) a.getBody()).size());

        ResponseEntity<?> b = assignmentsController.getSubmissions(10L, user("bystander"));
        assertEquals(0, ((List<?>) b.getBody()).size());
    }

    @Test
    public void teacherSeesEverything() {
        ResponseEntity<?> a = submissionController.getSubmissionsByAssignment(10L, user("teacher"));
        assertEquals(1, ((List<?>) a.getBody()).size());
    }

    // ---- grading ------------------------------------------------------------

    @Test
    public void assignmentGraderCanGrade() {
        ResponseEntity<?> r = submissionController.gradeSubmission(100L, user("grader"), 0.9, "On time and correct.");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(0.9, submission.getGrade());
        assertEquals("On time and correct.", submission.getFeedback());
        verify(submissionRepo, times(1)).save(submission);
    }

    @Test
    public void bystanderCannotGrade() {
        ResponseEntity<?> r = submissionController.gradeSubmission(100L, user("bystander"), 0.9, "nope");
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertNull(submission.getGrade());
        verify(submissionRepo, never()).save(any());
    }

    @Test
    public void teacherCanStillGrade() {
        ResponseEntity<?> r = submissionController.gradeSubmission(100L, user("teacher"), 0.91, "Extra.");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(0.91, submission.getGrade());
    }

    @Test
    public void gradingAMissingSubmissionIs404BeforeAnyPermissionCheck() {
        ResponseEntity<?> r = submissionController.gradeSubmission(999L, user("bystander"), 0.9, "x");
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
    }

    // ---- choosing graders ---------------------------------------------------

    @Test
    public void studentCannotAssignGraders() {
        when(personRepo.findAllById(List.of(3L))).thenReturn(List.of(bystander));
        ResponseEntity<?> r = assignmentsController.assignGradersToAssignment(10L, List.of(3L), user("bystander"));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals(List.of(grader), assignment.getAssignedGraders());
        verify(assignmentRepo, never()).save(any());
    }

    @Test
    public void anonymousCannotAssignGraders() {
        ResponseEntity<?> r = assignmentsController.assignGradersToAssignment(10L, List.of(3L), null);
        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        verify(assignmentRepo, never()).save(any());
    }

    @Test
    public void teacherCanAssignGraders() {
        when(personRepo.findAllById(List.of(2L, 3L))).thenReturn(List.of(grader, bystander));
        ResponseEntity<?> r = assignmentsController.assignGradersToAssignment(10L, List.of(2L, 3L), user("teacher"));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(2, assignment.getAssignedGraders().size());
        verify(assignmentRepo, times(1)).save(assignment);
    }

    // ---- helpers ------------------------------------------------------------

    private static Person person(Long id, String uid, String roleName) {
        Person p = new Person();
        setId(p, id);
        p.setUid(uid);
        p.setName(uid);
        p.setRoles(new ArrayList<>(List.of(new PersonRole(roleName))));
        return p;
    }

    /** Ids are set by JPA, not by a setter, so the tests set them the way the
     *  repo's other unit tests set generated fields: through the field. */
    private static void setId(Object entity, Long id) {
        Class<?> c = entity.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("no id field on " + entity.getClass());
    }

    private static UserDetails user(String uid) {
        return User.withUsername(uid).password("x").authorities("ROLE_USER").build();
    }
}
