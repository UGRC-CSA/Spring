package com.open.spring.mvc.groups;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration class for setting up a STOMP over WebSocket broker.
 * Implements {@link WebSocketMessageBrokerConfigurer} to customize the 
 * message broker and configure registered STOMP endpoints.
 * <p>
 * This configuration enables a simple memory-based message broker to 
 * route messages back to the client on destinations prefixed with "/topic",
 * and registers the "/ws-chat" endpoint.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketBrokerConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatChannelInterceptor chatSecurity;

    public WebSocketBrokerConfig(ChatChannelInterceptor chatSecurity) { this.chatSecurity = chatSecurity; }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(chatSecurity);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        // JWT cookies are scoped to /api; this alias authenticates the handshake.
        registry.addEndpoint("/api/ws-chat")
                .setAllowedOriginPatterns("https://*.opencodingsociety.com", "https://ugrc-csa.github.io",
                        "http://localhost:4500", "http://127.0.0.1:4500")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
