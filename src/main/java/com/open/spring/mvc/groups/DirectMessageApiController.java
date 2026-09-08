package com.open.spring.mvc.groups;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DirectMessageApiController {
    private final DirectMessageService messages;
    private final DirectMessageAccess access;

    public record OpenRequest(Long recipientId) {}
    public record ReadRequest(String messageId) {}

    @GetMapping("/me")
    public DirectMessageService.User me() { return DirectMessageService.User.from(access.currentPerson()); }

    @GetMapping("/users")
    public List<DirectMessageService.User> users(@RequestParam(defaultValue = "") String q) {
        return messages.search(q);
    }

    @GetMapping
    public List<DirectMessageService.Conversation> inbox() { return messages.inbox(); }

    @PostMapping
    public DirectMessageService.Conversation open(@RequestBody OpenRequest request) {
        return messages.open(request.recipientId());
    }

    @PostMapping("/{groupId}/read")
    public ResponseEntity<Void> read(@PathVariable Long groupId, @RequestBody ReadRequest request) {
        messages.markRead(groupId, request.messageId());
        return ResponseEntity.noContent().build();
    }
}
