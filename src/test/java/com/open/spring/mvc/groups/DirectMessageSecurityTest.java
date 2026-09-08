package com.open.spring.mvc.groups;

import com.open.spring.mvc.person.*;
import com.open.spring.mvc.S3uploads.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectMessageSecurityTest {
    private final PersonJpaRepository people = mock(PersonJpaRepository.class);
    private final GroupsJpaRepository groups = mock(GroupsJpaRepository.class);
    private final GroupChatService storage = mock(GroupChatService.class);
    private final GroupChatRealtimeService realtime = mock(GroupChatRealtimeService.class);
    private final DirectMessageAccess access = new DirectMessageAccess(people, groups);
    private final GroupChatApiController controller = new GroupChatApiController(storage, realtime, groups, people, access);
    private Groups dm;

    static Person person(long id, String uid) {
        Person result = new Person(); result.setUid(uid); result.setName(uid);
        ReflectionTestUtils.setField(result, "id", id); return result;
    }

    @BeforeEach void setup() {
        Person alice = person(1, "alice"), bob = person(2, "bob"), outsider = person(3, "charlie");
        when(people.findByUid("alice")).thenReturn(alice);
        when(people.findByUid("charlie")).thenReturn(outsider);
        dm = new Groups("dm-1-2", null, null, List.of(alice, bob)); dm.setDmKey("dm-1-2");
        ReflectionTestUtils.setField(dm, "id", 10L);
        when(groups.findById(10L)).thenReturn(Optional.of(dm));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private void login(String uid) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(uid, null, List.of())); }

    @Test void outsidersCannotReadSendDeleteOrShareFiles() {
        login("charlie");
        List<Runnable> requests = List.of(() -> controller.getMessages(10L),
                () -> controller.postMessage(10L, new GroupChatMessage("alice", "hello", "", null)),
                () -> controller.deleteMessage(10L, "message"), () -> controller.getSharedFiles(10L),
                () -> controller.uploadSharedFile(10L, new GroupChatApiController.FileUploadRequest("a.txt", "YQ==")));
        requests.forEach(request -> assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, request::run).getStatusCode()));
        verifyNoInteractions(storage, realtime);
    }

    @Test void guestCannotReadDm() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.getMessages(10L)).getStatusCode());
    }

    @Test void senderComesFromAuthenticatedAccount() {
        login("alice");
        controller.postMessage(10L, new GroupChatMessage("bob", "Hello", "forged date", null));
        verify(realtime).publishMessage(10L, "alice", "Hello", null);
    }

    @Test void participantsCannotDeleteEachOthersMessages() {
        login("alice");
        when(storage.getMessages(dm.getName())).thenReturn(List.of(new GroupChatMessage("m1", "bob", "Hello", "2026-01-01T00:00:00Z", null)));
        assertEquals(HttpStatus.FORBIDDEN, controller.deleteMessage(10L, "m1").getStatusCode());
        verifyNoInteractions(realtime);
    }

    @Test void socketSubscriptionsCheckMembershipAndRejectWildcardsAndBrokerInjection() {
        ChatChannelInterceptor security = new ChatChannelInterceptor(access);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setLeaveMutable(true);
        headers.setDestination("/topic/group/10");
        headers.setUser(() -> "charlie");
        assertThrows(ResponseStatusException.class, () -> security.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
        headers.setUser(() -> "alice");
        assertNotNull(security.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
        headers.setDestination("/topic/group/*");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> security.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/topic/group/10");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> security.preSend(MessageBuilder.createMessage(new byte[0], send.getMessageHeaders()), null));
    }

    @Test void socketSendChecksMembershipAndIgnoresForgedSender() {
        GroupChatPresenceService presence = mock(GroupChatPresenceService.class);
        GroupChatWebSocketController socket = new GroupChatWebSocketController(realtime, presence, access);
        GroupChatEvent event = GroupChatEvent.builder().context("sendMessage").groupId(10L).sender("bob").message("Hello").build();
        assertThrows(ResponseStatusException.class, () -> socket.handleGroupEvent(event, () -> "charlie", "session"));
        verifyNoInteractions(presence, realtime);
        socket.handleGroupEvent(event, () -> "alice", "session");
        verify(realtime).publishMessage(10L, "alice", "Hello", null);
    }

    @Test void genericGroupsCannotListRenameOrAddMembersToDm() {
        GroupsApiController generic = new GroupsApiController();
        ReflectionTestUtils.setField(generic, "groupsRepository", groups);
        when(groups.findAll()).thenReturn(List.of(dm));
        assertTrue(generic.getAllGroups().getBody().isEmpty());
        assertEquals(HttpStatus.NOT_FOUND, generic.getGroupById(10L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, generic.updateGroup(10L, new GroupsApiController.GroupUpdateDto("public", "", "")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, generic.addPersonToGroup(10L, 3L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, generic.deleteGroup(10L).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, generic.createGroup(new GroupsApiController.GroupCreateDto(" DM-1-2", "", "", List.of())).getStatusCode());
    }

    @Test void genericFileApiCannotReadOrOverwritePrivateStorage() {
        FileHandler files = mock(FileHandler.class);
        S3FileApiController generic = new S3FileApiController();
        ReflectionTestUtils.setField(generic, "fileHandler", files);
        assertEquals(HttpStatus.FORBIDDEN, generic.downloadFileByQuery("dm-1-2", "messages-images/messages.jsonl").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, generic.uploadFile("dm-1-2", "messages-images/messages.jsonl", "").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, generic.deleteUserFiles("dm-1-2").getStatusCode());
        verifyNoInteractions(files);
    }

    @Test void ordinaryClassChatStillWorks() {
        Groups ordinary = new Groups("csa-week-1", "1", "CSA", List.of());
        when(groups.findById(11L)).thenReturn(Optional.of(ordinary));
        assertEquals(HttpStatus.OK, controller.getMessages(11L).getStatusCode());
        assertNotNull(access.requireChatAccess(11L, null));
    }
}
