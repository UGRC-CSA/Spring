package com.open.spring.mvc.groups;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DirectMessageService {
    private final GroupsJpaRepository groups;
    private final PersonJpaRepository people;
    private final DirectMessageAccess access;
    private final GroupChatService chat;
    private final DmReadReceiptJpaRepository receipts;
    private final TransactionTemplate transactions;

    public record User(Long id, String uid, String name) {
        static User from(Person person) { return new User(person.getId(), person.getUid(), person.getName()); }
    }
    public record Conversation(Long id, User peer, GroupChatMessage lastMessage, long unreadCount) {}

    public List<User> search(String query) {
        Person me = access.currentPerson();
        String term = query == null ? "" : query.strip();
        if (term.length() < 2) return List.of();
        if (term.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search is too long");
        return people.searchMessageRecipients(me.getId(), term, PageRequest.of(0, 20))
                .stream().map(User::from).toList();
    }

    // Commit inside the lock; the unique key also protects creation across server instances.
    public synchronized Conversation open(Long recipientId) {
        Person me = access.currentPerson();
        if (recipientId == null || recipientId.equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another user to message");
        }
        String key = DmNaming.forPair(me.getId(), recipientId);
        if (groups.findByDmKey(key).isPresent()) {
            return transactions.execute(status -> describe(groups.findByDmKey(key).orElseThrow(), me));
        }
        people.findById(recipientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (groups.findByName(key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A legacy group uses this conversation name; contact an administrator");
        }
        try {
            return transactions.execute(status -> {
                // Allocate the inherited table-generated ID before reads in this transaction.
                // SQLite cannot upgrade a read snapshot after Hibernate's separate sequence write.
                Groups group = new Groups(key, null, null, new ArrayList<>());
                group.setDmKey(key);
                groups.saveAndFlush(group);
                Person current = people.findById(me.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
                Person peer = people.findById(recipientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                group.setGroupMembers(new ArrayList<>(List.of(current, peer)));
                groups.saveAndFlush(group);
                access.requireMember(group, me);
                return describe(group, me);
            });
        } catch (DataIntegrityViolationException conflict) {
            return transactions.execute(status -> describe(groups.findByDmKey(key).orElseThrow(() -> conflict), me));
        }
    }

    @Transactional(readOnly = true)
    public List<Conversation> inbox() {
        Person me = access.currentPerson();
        return groups.findGroupsByPersonIdWithMembers(me.getId()).stream()
                .filter(group -> group.getDmKey() != null).map(group -> describe(group, me))
                .sorted(Comparator.comparing((Conversation c) -> c.lastMessage() == null
                        ? Instant.EPOCH : Instant.parse(c.lastMessage().getDate())).reversed()
                        .thenComparing(Conversation::id)).toList();
    }

    public synchronized void markRead(Long groupId, String messageId) {
        transactions.executeWithoutResult(status -> {
            Person me = access.currentPerson();
            Groups group = groups.findById(groupId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
            if (!DmNaming.isDirect(group)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            access.requireMember(group, me);
            GroupChatMessage message = chat.getMessages(group.getName()).stream()
                    .filter(item -> Objects.equals(item.getId(), messageId)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message not found"));
            String receiptId = groupId + ":" + me.getId();
            DmReadReceipt receipt = receipts.findById(receiptId)
                    .orElse(new DmReadReceipt(receiptId, Instant.EPOCH.toString()));
            if (Instant.parse(message.getDate()).isAfter(Instant.parse(receipt.getReadAt()))) {
                receipt.setReadAt(message.getDate());
                receipts.save(receipt);
            }
        });
    }

    private Conversation describe(Groups group, Person me) {
        access.requireMember(group, me);
        Person peer = group.getGroupMembers().stream().filter(p -> !p.getId().equals(me.getId())).findFirst().orElseThrow();
        List<GroupChatMessage> messages = chat.getMessages(group.getName());
        Instant readAt = receipts.findById(group.getId() + ":" + me.getId())
                .map(receipt -> Instant.parse(receipt.getReadAt())).orElse(Instant.EPOCH);
        long unread = messages.stream().filter(message -> !me.getUid().equals(message.getName()))
                .filter(message -> Instant.parse(message.getDate()).isAfter(readAt)).count();
        return new Conversation(group.getId(), User.from(peer), messages.isEmpty() ? null : messages.getLast(), unread);
    }
}
