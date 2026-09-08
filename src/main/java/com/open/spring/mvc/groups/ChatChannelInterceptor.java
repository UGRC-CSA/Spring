package com.open.spring.mvc.groups;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChatChannelInterceptor implements ChannelInterceptor {
    private final DirectMessageAccess access;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        String destination = headers.getDestination();
        if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            // Wildcards and alternate paths could bypass per-conversation checks.
            if (destination == null || !destination.matches("/topic/group/[1-9][0-9]*")) {
                throw new AccessDeniedException("Invalid chat subscription");
            }
            access.requireChatAccess(Long.valueOf(destination.substring("/topic/group/".length())), headers.getUser());
        }
        if (headers.getCommand() == StompCommand.SEND && !"/app/groups.chat".equals(destination)) {
            throw new AccessDeniedException("Send chat events through the application");
        }
        return message;
    }
}
