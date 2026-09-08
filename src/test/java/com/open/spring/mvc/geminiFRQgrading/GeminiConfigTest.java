package com.open.spring.mvc.geminiFRQgrading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * The FRQ grader without a key. It must answer with a clear error and never
 * call Google. The .env fallback is pointed at a folder with no .env so the
 * test does not depend on the machine it runs on.
 */
public class GeminiConfigTest {

    private GeminiController controller(String key, String url) throws Exception {
        GeminiController c = new GeminiController();
        set(c, "geminiApiKey", key);
        set(c, "geminiApiUrl", url);
        set(c, "dotenv", Dotenv.configure().directory("/nonexistent-for-this-test").ignoreIfMissing().load());
        return c;
    }

    @Test
    public void noKeyIsAClearErrorNotACallToGoogle() throws Exception {
        ResponseEntity<?> r = controller("", "https://example.invalid/v1beta/models/x:generateContent")
                .grade(new GeminiController.GradeRequest("What is a class?", "A blueprint."));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertTrue(String.valueOf(((Map<?, ?>) r.getBody()).get("error")).contains("GEMINI_API_KEY"));
    }

    @Test
    public void noEndpointIsAClearErrorToo() throws Exception {
        ResponseEntity<?> r = controller("a-key", "")
                .grade(new GeminiController.GradeRequest("q", "a"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertTrue(String.valueOf(((Map<?, ?>) r.getBody()).get("error")).contains("GEMINI_MODEL"));
    }

    @Test
    public void missingQuestionIsStillABadRequest() throws Exception {
        ResponseEntity<?> r = controller("a-key", "https://example.invalid/x")
                .grade(new GeminiController.GradeRequest(null, "a"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    private static void set(Object target, String field, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
