package com.open.spring.mvc.groups;

import com.open.spring.mvc.person.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectMessageServiceTest {
    private final GroupsJpaRepository groups = mock(GroupsJpaRepository.class);
    private final PersonJpaRepository people = mock(PersonJpaRepository.class);
    private final DirectMessageAccess access = mock(DirectMessageAccess.class);
    private final GroupChatService chat = mock(GroupChatService.class);
    private final DmReadReceiptJpaRepository receipts = mock(DmReadReceiptJpaRepository.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final DirectMessageService service = new DirectMessageService(groups, people, access, chat, receipts, transactions);
    private Person alice, bob;
    private Groups group;

    @BeforeEach void setup() {
        alice = DirectMessageSecurityTest.person(1, "alice"); bob = DirectMessageSecurityTest.person(2, "bob");
        when(access.currentPerson()).thenReturn(alice);
        when(people.findById(2L)).thenReturn(Optional.of(bob));
        when(people.findById(1L)).thenReturn(Optional.of(alice));
        group = new Groups("dm-1-2", null, null, List.of(alice, bob)); group.setDmKey("dm-1-2");
        ReflectionTestUtils.setField(group, "id", 10L);
        when(groups.findByDmKey("dm-1-2")).thenReturn(Optional.of(group));
        when(groups.findById(10L)).thenReturn(Optional.of(group));
        when(groups.findGroupsByPersonIdWithMembers(1L)).thenReturn(List.of(group));
        when(chat.getMessages("dm-1-2")).thenReturn(List.of());
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        doAnswer(invocation -> { ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) invocation.getArgument(0)).accept(new SimpleTransactionStatus()); return null; })
                .when(transactions).executeWithoutResult(any());
    }

    @Test void reopeningFromEitherSideReturnsTheSameGroup() {
        assertEquals(10L, service.open(2L).id());
        when(access.currentPerson()).thenReturn(bob);
        when(people.findById(1L)).thenReturn(Optional.of(alice));
        assertEquals(10L, service.open(1L).id());
        verify(groups, never()).saveAndFlush(any());
    }

    @Test void newConversationHasExactlyTwoMembersAndCanonicalKey() {
        when(groups.findByDmKey("dm-1-2")).thenReturn(Optional.empty());
        when(groups.saveAndFlush(any())).thenAnswer(invocation -> {
            Groups created = invocation.getArgument(0);
            assertEquals("dm-1-2", created.getDmKey());
            ReflectionTestUtils.setField(created, "id", 10L);
            return created;
        });
        assertEquals("bob", service.open(2L).peer().uid());
        var saved = org.mockito.ArgumentCaptor.forClass(Groups.class);
        verify(groups, times(2)).saveAndFlush(saved.capture());
        assertEquals(List.of(alice, bob), saved.getValue().getGroupMembers());
    }

    @Test void selfAndMissingRecipientsAreRejected() {
        assertThrows(ResponseStatusException.class, () -> service.open(1L));
        assertThrows(ResponseStatusException.class, () -> service.open(null));
        assertThrows(ResponseStatusException.class, () -> service.open(9L));
        verify(groups, never()).saveAndFlush(any());
    }

    @Test void unreadExcludesOwnMessagesAndOnlyMarksTheDisplayedMessage() {
        GroupChatMessage older = new GroupChatMessage("m1", "bob", "old", "2026-01-01T00:00:00Z", null);
        GroupChatMessage own = new GroupChatMessage("m2", "alice", "mine", "2026-01-01T00:00:01Z", null);
        GroupChatMessage newer = new GroupChatMessage("m3", "bob", "new", "2026-01-01T00:00:02Z", null);
        when(chat.getMessages("dm-1-2")).thenReturn(List.of(older, own, newer));
        assertEquals(2, service.inbox().getFirst().unreadCount());
        service.markRead(10L, "m1");
        verify(receipts).save(new DmReadReceipt("10:1", older.getDate()));
        when(receipts.findById("10:1")).thenReturn(Optional.of(new DmReadReceipt("10:1", older.getDate())));
        assertEquals(1, service.inbox().getFirst().unreadCount());
        assertThrows(ResponseStatusException.class, () -> service.markRead(10L, "forged"));
    }

    @Test void oldReadRequestsCannotMoveTheReceiptBackwards() {
        when(chat.getMessages("dm-1-2")).thenReturn(List.of(new GroupChatMessage("m1", "bob", "old", "2026-01-01T00:00:00Z", null)));
        when(receipts.findById("10:1")).thenReturn(Optional.of(new DmReadReceipt("10:1", "2026-01-01T00:00:02Z")));
        service.markRead(10L, "m1");
        verify(receipts, never()).save(any());
    }
}
