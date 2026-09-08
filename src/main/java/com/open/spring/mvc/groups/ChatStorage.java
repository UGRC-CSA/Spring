package com.open.spring.mvc.groups;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;

import com.open.spring.mvc.S3uploads.S3FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Uses the existing S3 layout; local previews keep all chat data on disk. */
@Service
public class ChatStorage {
    private final S3FileHandler s3;
    private final boolean local;
    private final Path root;

    public ChatStorage(S3FileHandler s3, @Value("${chat.storage:s3}") String storage,
            @Value("${chat.local-directory:volumes/chat-local}") String directory) {
        this.s3 = s3;
        if (!List.of("local", "s3").contains(storage)) throw new IllegalArgumentException("Unknown chat storage");
        this.local = "local".equals(storage);
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String decodeFile(String group, String filename) {
        if (!local) return DmNaming.reserved(group) ? s3.decodePrivateChatFile(group, filename) : s3.decodeFile(group, filename);
        Path path = resolve(group + "/" + filename);
        try {
            return Files.exists(path) ? Base64.getEncoder().encodeToString(Files.readAllBytes(path)) : null;
        } catch (IOException e) { throw new IllegalStateException("Could not read chat storage", e); }
    }

    public String uploadFile(String base64, String filename, String group) {
        if (!local) {
            String result = s3.uploadFile(base64, filename, group);
            if (result == null) throw new IllegalStateException("Chat storage is unavailable; message was not saved");
            return result;
        }
        Path path = resolve(group + "/" + filename);
        try {
            if (filename.endsWith("/")) {
                Files.createDirectories(path);
            } else {
                Files.createDirectories(path.getParent());
                Path temporary = Files.createTempFile(path.getParent(), ".chat-", ".tmp");
                try {
                    Files.write(temporary, Base64.getDecoder().decode(base64));
                    try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
            }
            return filename;
        } catch (IOException e) { throw new IllegalStateException("Could not save chat data", e); }
    }

    public boolean fileExists(String group, String filename) {
        return local ? Files.exists(resolve(group + "/" + filename)) : s3.fileExists(group, filename);
    }

    public List<String> listFiles(String prefix) {
        if (!local) return s3.listFiles(prefix);
        Path directory = resolve(prefix);
        if (!Files.exists(directory)) return List.of();
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).map(root::relativize)
                    .map(path -> path.toString().replace('\\', '/')).sorted().toList();
        } catch (IOException e) { throw new IllegalStateException("Could not list shared files", e); }
    }

    private Path resolve(String key) {
        for (String segment : key.replace('\\', '/').split("/")) {
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Invalid storage path");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IllegalArgumentException("Invalid storage path");
        return path;
    }
}
