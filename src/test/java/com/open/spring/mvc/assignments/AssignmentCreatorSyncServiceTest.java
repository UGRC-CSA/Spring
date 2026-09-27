package com.open.spring.mvc.assignments;

import static com.open.spring.mvc.assignments.AssignmentCreatorFixtures.assignment;
import static com.open.spring.mvc.assignments.AssignmentCreatorFixtures.student;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import com.open.spring.mvc.assignments.AssignmentCreatorSyncService.UnknownCreatorException;
import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonJpaRepository;

class AssignmentCreatorSyncServiceTest {

    @Mock
    private PersonJpaRepository personRepo;

    private AssignmentCreatorSyncService service;

    private Person first;
    private Person second;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AssignmentCreatorSyncService();
        ReflectionTestUtils.setField(service, "personRepo", personRepo);

        first = student(1L, "AdityaS-2010");
        second = student(2L, "second-creator");
        when(personRepo.findByUid("AdityaS-2010")).thenReturn(first);
        when(personRepo.findByUid("second-creator")).thenReturn(second);
    }

    @Test
    void normalizationTrimsBlanksAndDuplicates() {
        List<String> normalized = service.normalizeUids(
            List.of(" AdityaS-2010 ", "second-creator", "AdityaS-2010", "   ", "second-creator"));

        assertEquals(List.of("AdityaS-2010", "second-creator"), normalized);
    }

    @Test
    void normalizationTreatsAMissingListAsLegacyUnassigned() {
        assertEquals(List.of(), service.normalizeUids(null));
        assertEquals(List.of(), service.normalizeUids(List.of()));
    }

    @Test
    void resolvingAnUnknownUidRejectsTheWholeUpdate() {
        when(personRepo.findByUid("ghost")).thenReturn(null);

        UnknownCreatorException error = assertThrows(UnknownCreatorException.class,
            () -> service.resolveCreators(List.of("AdityaS-2010", "ghost")));

        assertEquals(List.of("ghost"), error.getUnknownUids());
    }

    @Test
    void everyUnknownUidIsReportedTogether() {
        when(personRepo.findByUid("ghost")).thenReturn(null);
        when(personRepo.findByUid("phantom")).thenReturn(null);

        UnknownCreatorException error = assertThrows(UnknownCreatorException.class,
            () -> service.resolveCreators(List.of("ghost", "AdityaS-2010", "phantom")));

        assertEquals(List.of("ghost", "phantom"), error.getUnknownUids());
    }

    @Test
    void applyingCreatorsStoresEveryResolvedPersonInADeterministicOrder() {
        Assignment target = assignment(10L, "pilot");

        // The reported order is sorted rather than depending on database retrieval
        // order - what matters is that it never varies between reads.
        assertTrue(service.applyCreators(target, List.of(second, first)));
        assertEquals(List.of("AdityaS-2010", "second-creator"), service.creatorUidsOf(target));
    }

    @Test
    void applyingTheSameListAgainChangesNothing() {
        Assignment target = assignment(10L, "pilot");
        service.applyCreators(target, List.of(first, second));

        assertFalse(service.applyCreators(target, List.of(first, second)));
        assertEquals(2, target.getCreators().size());
    }

    @Test
    void reorderingTheSameCreatorsIsStillIdempotent() {
        Assignment target = assignment(10L, "pilot");
        service.applyCreators(target, List.of(first, second));

        assertFalse(service.applyCreators(target, List.of(second, first)));
    }

    @Test
    void applyingADifferentListReplacesTheOwners() {
        Assignment target = assignment(10L, "pilot");
        service.applyCreators(target, List.of(first, second));

        assertTrue(service.applyCreators(target, List.of(second)));
        assertEquals(List.of("second-creator"), service.creatorUidsOf(target));
    }

    @Test
    void anAssignmentWithoutCreatorsReportsAnEmptyOwnerList() {
        assertEquals(List.of(), service.creatorUidsOf(assignment(10L, "legacy")));
    }
}
