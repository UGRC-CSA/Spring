package com.open.spring.mvc.assignments;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonJpaRepository;

/**
 * Turns the creatorUids sent by the Pages synchronization bot into assignment ownership.
 *
 * Every uid is resolved before the assignment is touched, so an unknown uid rejects the
 * whole update instead of leaving a half-written creator list behind.
 */
@Service
public class AssignmentCreatorSyncService {

    /** Thrown when at least one creator uid does not match an OCS account. */
    public static class UnknownCreatorException extends RuntimeException {
        private final List<String> unknownUids;

        public UnknownCreatorException(List<String> unknownUids) {
            super("Unknown creator uid(s): " + String.join(", ", unknownUids));
            this.unknownUids = List.copyOf(unknownUids);
        }

        public List<String> getUnknownUids() {
            return unknownUids;
        }
    }

    @Autowired
    private PersonJpaRepository personRepo;

    /** Trims, drops blanks, and removes duplicates while preserving frontmatter order. */
    public List<String> normalizeUids(List<String> rawUids) {
        if (rawUids == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String rawUid : rawUids) {
            if (rawUid == null) {
                continue;
            }
            String uid = rawUid.trim();
            if (!uid.isEmpty()) {
                normalized.add(uid);
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * Resolves every uid against Person.uid.
     *
     * @throws UnknownCreatorException if any uid is unknown, listing all of them so the
     *         Pages run can fix the whole frontmatter in one pass.
     */
    public List<Person> resolveCreators(List<String> normalizedUids) {
        List<Person> resolved = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String uid : normalizedUids) {
            Person person = personRepo.findByUid(uid);
            if (person == null) {
                unknown.add(uid);
            } else {
                resolved.add(person);
            }
        }
        if (!unknown.isEmpty()) {
            throw new UnknownCreatorException(unknown);
        }
        return resolved;
    }

    /**
     * Replaces the assignment's creator list with the resolved people.
     *
     * @return true when the stored list actually changed, so re-running synchronization
     *         with the same frontmatter is a no-op.
     */
    public boolean applyCreators(Assignment assignment, List<Person> creators) {
        Set<String> desiredUids = creators.stream()
            .map(Person::getUid)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> currentUids = currentCreatorUids(assignment);

        if (currentUids.equals(desiredUids)) {
            return false;
        }

        assignment.getCreators().clear();
        assignment.getCreators().addAll(creators);
        return true;
    }

    /**
     * Creator uids currently stored on the assignment.
     *
     * Sorted so callers and API responses receive a deterministic list regardless of
     * database retrieval order.
     */
    public Set<String> currentCreatorUids(Assignment assignment) {
        return assignment.getCreators().stream()
            .map(Person::getUid)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    public List<String> creatorUidsOf(Assignment assignment) {
        return List.copyOf(currentCreatorUids(assignment));
    }
}
