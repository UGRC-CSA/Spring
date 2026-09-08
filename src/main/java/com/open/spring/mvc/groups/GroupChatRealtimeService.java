package com.open.spring.mvc.groups;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Service to handle the real-time publishing of group chat messages and presence events.
 * It uses {@link SimpMessagingTemplate} to broadcast messages to corresponding specific group topics.
 */
@Service
@RequiredArgsConstructor
public class GroupChatRealtimeService {

    public static final String GROUP_TOPIC_PREFIX = "/topic/group/";

    private final GroupChatService groupChatService;
    private final GroupsJpaRepository groupsRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GroupChatEvent publishMessage(Long groupId, String sender, String message, String image) {
        return publishMessage(groupId, sender, message, image, null);
    }

    /**
     * @param requestedDate caller-supplied ISO-8601 timestamp (e.g. from a client republishing
     *                      history that predates the live backend). Used as-is when it parses to a
     *                      non-future instant; otherwise falls back to now(), same as before.
     */
    public GroupChatEvent publishMessage(Long groupId, String sender, String message, String image, String requestedDate) {
        Groups group = getGroupOrThrow(groupId);

        String date = resolveDate(requestedDate);
        GroupChatMessage persisted = new GroupChatMessage(sender, message, date, image);
        groupChatService.addMessage(group.getName(), persisted);

        GroupChatEvent event = GroupChatEvent.builder()
                .context("sendMessageServer")
                .groupId(groupId)
                .messageId(persisted.getId())
                .sender(sender)
                .message(message)
                .image(image)
                .date(date)
                .build();

        broadcastToGroup(groupId, event);
        return event;
    }

    public void deleteMessage(Long groupId, String messageId) {
        Groups group = getGroupOrThrow(groupId);
        groupChatService.deleteMessage(group.getName(), messageId);

        GroupChatEvent event = GroupChatEvent.builder()
                .context("deleteMessageServer")
                .groupId(groupId)
                .messageId(messageId)
                .date(Instant.now().toString())
                .build();

        broadcastToGroup(groupId, event);
    }

    public GroupChatEvent publishFile(Long groupId, String sender, String filename, String base64Data) {
        Groups group = getGroupOrThrow(groupId);

        String uploadResult = groupChatService.uploadSharedFile(group.getName(), filename, base64Data);
        if (uploadResult == null) {
            throw new IllegalStateException("Upload failed");
        }

        GroupChatEvent event = GroupChatEvent.builder()
                .context("sendFileServer")
                .groupId(groupId)
                .sender(sender)
                .filename(filename)
                .base64Data(base64Data)
                .date(Instant.now().toString())
                .build();

        broadcastToGroup(groupId, event);
        return event;
    }

    public void publishPresence(Long groupId, String context, String sender, java.util.List<String> participants) {
        GroupChatEvent event = GroupChatEvent.builder()
                .context(context)
                .groupId(groupId)
                .sender(sender)
                .participants(participants)
                .date(Instant.now().toString())
                .build();
        broadcastToGroup(groupId, event);
    }

    public void publishTyping(Long groupId, String sender, boolean typing) {
        GroupChatEvent event = GroupChatEvent.builder()
                .context(typing ? "typingStartServer" : "typingStopServer")
                .groupId(groupId)
                .sender(sender)
                .date(Instant.now().toString())
                .build();
        broadcastToGroup(groupId, event);
    }

    public void publishError(Long groupId, String sender, String errorMessage) {
        GroupChatEvent event = GroupChatEvent.builder()
                .context("errorServer")
                .groupId(groupId)
                .sender(sender)
                .error(errorMessage)
                .date(Instant.now().toString())
                .build();
        broadcastToGroup(groupId, event);
    }

    private void broadcastToGroup(Long groupId, GroupChatEvent event) {
        messagingTemplate.convertAndSend(GROUP_TOPIC_PREFIX + groupId, event);
    }

    private String resolveDate(String requestedDate) {
        if (requestedDate == null || requestedDate.isBlank()) {
            return Instant.now().toString();
        }
        try {
            Instant parsed = Instant.parse(requestedDate.trim());
            return parsed.isAfter(Instant.now()) ? Instant.now().toString() : parsed.toString();
        } catch (DateTimeParseException ex) {
            return Instant.now().toString();
        }
    }

    private Groups getGroupOrThrow(Long groupId) {
        Optional<Groups> groupOpt = groupsRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            throw new IllegalArgumentException("Group not found");
        }
        return groupOpt.get();
    }
}
