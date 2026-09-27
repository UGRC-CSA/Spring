package com.open.spring.mvc.assignments;

import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generates a thorough, assignment-specific AI grading rubric from the assignment's
 * own page content, instead of every assignment relying on the generic
 * {@link Assignment#DEFAULT_AI_RUBRIC}. Failures always fall back to the caller using
 * that default — rubric generation is a best-effort enhancement, never a hard
 * dependency of assignment creation.
 */
@Service
public class AssignmentRubricService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String geminiApiKey;
    private final String geminiApiUrl;

    public AssignmentRubricService(
            ObjectMapper objectMapper,
            @Value("${gemini.api.key:}") String geminiApiKey,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent}") String geminiApiUrl) {
        this.objectMapper = objectMapper;
        this.geminiApiKey = geminiApiKey;
        this.geminiApiUrl = geminiApiUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * @param assignmentName the assignment's name/title
     * @param description    the assignment's short description, if any
     * @param pageContent    the full text of the assignment/lesson page this assignment
     *                       was created from — the primary grounding for the rubric
     * @return a tiered grading rubric, or {@code null} if generation isn't possible
     *         (no API key configured, empty page content, or the AI call failed)
     */
    public String generateRubric(String assignmentName, String description, String pageContent) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return null;
        }
        if (pageContent == null || pageContent.isBlank()) {
            return null;
        }

        String prompt = buildPrompt(assignmentName, description, pageContent);

        try {
            JsonNode response = objectMapper.readTree(callGemini(prompt));
            String rubric = response.path("candidates").path(0).path("content").path("parts").path(0)
                    .path("text").asText("").trim();
            return rubric.isBlank() ? null : rubric;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildPrompt(String assignmentName, String description, String pageContent) {
        return """
                You are writing a grading rubric for a student assignment. Base the rubric on the ACTUAL assignment content below — be specific to what this assignment asks for, not generic.

                ASSIGNMENT NAME: %s
                ASSIGNMENT DESCRIPTION: %s

                ASSIGNMENT PAGE CONTENT:
                %s

                Write a thorough, detailed grading rubric with exactly four tiers, using this exact structure and tone (mirror this example's format, but replace the indicators with specifics grounded in the assignment content above):

                Score 4 — Strong / Exceptional
                <2-4 sentences describing what a submission that fully and impressively meets THIS assignment's requirements looks like>

                Indicators:
                 <specific indicator 1 for this assignment>
                 <specific indicator 2>
                 <specific indicator 3>
                 <specific indicator 4>

                Score 3 — Adequate
                <2-4 sentences describing a submission that meets the core requirement but lacks depth>

                Indicators:
                 <specific indicator 1>
                 <specific indicator 2>
                 <specific indicator 3>

                Score 2 — Limited
                <2-4 sentences describing a partial or shallow submission for THIS assignment>

                Indicators:
                 <specific indicator 1>
                 <specific indicator 2>
                 <specific indicator 3>

                Score 1 — Insufficient
                <2-4 sentences describing a submission that fails to address THIS assignment's core requirement>

                Indicators:
                 <specific indicator 1>
                 <specific indicator 2>

                Return ONLY the rubric text in the structure above — no preamble, no JSON, no markdown code fences.
                """.formatted(
                assignmentName == null ? "" : assignmentName,
                description == null ? "" : description,
                pageContent);
    }

    private String callGemini(String prompt) throws Exception {
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(part))),
                "generationConfig", Map.of("temperature", 0.3));
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        HttpRequest request = HttpRequest.newBuilder(URI.create(geminiApiUrl + "?key=" + geminiApiKey))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini returned HTTP " + response.statusCode());
        }
        return response.body();
    }
}
