package com.open.spring.mvc.groups;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Return API errors directly; servlet error dispatch would enter the MVC login flow. */
@RestControllerAdvice(assignableTypes = {DirectMessageApiController.class, GroupChatApiController.class})
public class ChatApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
                error.getReason() == null ? "Request could not be completed" : error.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid chat request"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> unavailable(IllegalStateException error) {
        return ResponseEntity.status(503).body(Map.of("message", "Chat storage is unavailable. Please try again."));
    }
}
