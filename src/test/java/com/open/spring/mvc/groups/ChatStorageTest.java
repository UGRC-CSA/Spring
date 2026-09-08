package com.open.spring.mvc.groups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.open.spring.mvc.S3uploads.S3FileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatStorageTest {
    @TempDir Path directory;

    @Test void concurrentMessagesSurviveReloadAndDeletion() throws Exception {
        ChatStorage storage = new ChatStorage(mock(S3FileHandler.class), "local", directory.toString());
        GroupChatService chat = new GroupChatService(storage, new ObjectMapper());
        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                final String text = "Message " + i;
                futures.add(executor.submit(() -> chat.addMessage("dm-1-2", new GroupChatMessage("alice", text, "", null))));
            }
            for (Future<?> future : futures) future.get();
        }
        GroupChatService reopened = new GroupChatService(storage, new ObjectMapper());
        List<GroupChatMessage> messages = reopened.getMessages("dm-1-2");
        assertEquals(30, messages.size());
        assertEquals(30, messages.stream().map(GroupChatMessage::getMessage).distinct().count());
        reopened.deleteMessage("dm-1-2", messages.getFirst().getId());
        assertEquals(29, chat.getMessages("dm-1-2").size());
    }

    @Test void corruptHistoryIsNotOverwrittenAndS3FailuresAreReported() {
        ChatStorage storage = new ChatStorage(mock(S3FileHandler.class), "local", directory.toString());
        storage.uploadFile(Base64.getEncoder().encodeToString("not json".getBytes()), "messages-images/messages.jsonl", "dm-1-2");
        GroupChatService chat = new GroupChatService(storage, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> chat.addMessage("dm-1-2", new GroupChatMessage("alice", "Hello", "", null)));
        S3FileHandler s3 = mock(S3FileHandler.class);
        ChatStorage remote = new ChatStorage(s3, "s3", directory.toString());
        assertThrows(IllegalStateException.class, () -> remote.uploadFile("", "messages-images/messages.jsonl", "dm-1-2"));
    }

    @Test void invalidContentAndPathsAreRejected() {
        assertEquals("dm-1-2", DmNaming.forPair(2, 1));
        assertThrows(IllegalArgumentException.class, () -> DmNaming.forPair(1, 1));
        assertThrows(RuntimeException.class, () -> DirectMessageContent.validate("  ", null));
        assertThrows(RuntimeException.class, () -> DirectMessageContent.validate("x".repeat(4001), null));
        assertThrows(RuntimeException.class, () -> DirectMessageContent.validateFile("../messages.jsonl", "YQ=="));
        assertThrows(RuntimeException.class, () -> DirectMessageContent.validateFile("a.txt", "invalid base64!"));
        ChatStorage storage = new ChatStorage(mock(S3FileHandler.class), "local", directory.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.uploadFile("YQ==", "../escape", ".."));
    }
}
