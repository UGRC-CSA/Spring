package com.open.spring.mvc.groups;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DirectMessageContent {
    private DirectMessageContent() {}

    public static void validate(String text, String image) {
        if (text == null || text.isBlank() || text.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a message of 1 to 4000 characters");
        }
        if (image != null && !image.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the attachment button to share images");
        }
    }

    public static void validateFile(String filename, String base64) {
        if (filename == null || filename.isBlank() || filename.length() > 150
                || filename.contains("/") || filename.contains("\\") || filename.equals("..")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment filename");
        }
        try {
            if (base64 == null || base64.length() > 1400000 || java.util.Base64.getDecoder().decode(base64).length > 1048576) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachments must be at most 1 MB");
        }
    }
}
