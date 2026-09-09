package com.open.spring.mvc.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The stats grader without a key. It must answer 503 and never call Google.
 * Before this change the key was a constant in the controller.
 */
public class StatsGeminiConfigTest {

    private StatsController controller(String key, String url) throws Exception {
        StatsController c = new StatsController();
        set(c, "geminiApiKey", key);
        set(c, "geminiApiUrl", url);
        return c;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static StatsGradeDto request() {
        StatsGradeDto dto = new StatsGradeDto();
        dto.setUsername("toby");
        dto.setModule("m");
        dto.setSubmodule(1);
        dto.setQuestion("What is a class?");
        dto.setResponse("A blueprint.");
        return dto;
    }

    @Test
    public void noKeyIsServiceUnavailableNotACallToGoogle() throws Exception {
        ResponseEntity<Stats> r = controller("", "https://example.invalid/v1beta/models/x:generateContent").submitGrade(request());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
    }

    @Test
    public void noEndpointIsServiceUnavailableToo() throws Exception {
        ResponseEntity<Stats> r = controller("a-key", "").submitGrade(request());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
    }
}
