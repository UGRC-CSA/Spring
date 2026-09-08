package com.open.spring.mvc.groups;

import java.security.Principal;

import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DirectMessageAccess {
    private final PersonJpaRepository people;
    private final GroupsJpaRepository groups;

    public Person currentPerson() {
        return person(SecurityContextHolder.getContext().getAuthentication());
    }

    public Person person(Principal principal) {
        if (principal == null || principal instanceof AnonymousAuthenticationToken
                || (principal instanceof Authentication auth && !auth.isAuthenticated())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to use messages");
        }
        Person person = people.findByUid(principal.getName());
        if (person == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again");
        return person;
    }

    public void requireMember(Groups group, Person person) {
        if (group.getDmKey() == null || !group.getDmKey().equals(group.getName())
                || group.getGroupMembers().size() != 2 || group.getGroupMembers().stream()
                .noneMatch(member -> member.getId().equals(person.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This conversation is private");
        }
        if (!DmNaming.forPair(group.getGroupMembers().getFirst().getId(), group.getGroupMembers().getLast().getId()).equals(group.getDmKey())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid conversation membership");
        }
    }

    @Transactional(readOnly = true)
    public Groups requireChatAccess(Long id, Principal principal) {
        Groups group = groups.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (DmNaming.isDirect(group)) requireMember(group, person(principal));
        return group;
    }

    public void requireChatAccess(Groups group) {
        if (DmNaming.isDirect(group)) requireMember(group, currentPerson());
    }
}
