package com.open.spring.mvc.geminiFRQgrading;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@RestController
@RequestMapping("/api/gemini-frq")
public class GeminiController {

    @Autowired
    private GeminiRepository geminiRepository;

    // The key and endpoint come from application.properties (GEMINI_API_KEY,
    // GEMINI_MODEL or GEMINI_API_URL in the environment). A .env file next to
    // the app still works as the fallback, as before.
    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:}")
    private String geminiApiUrl;

    private Dotenv dotenv;

    private Dotenv env() {
        if (dotenv == null) {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        }
        return dotenv;
    }

    /** The property when it is set, otherwise the .env value, otherwise null. */
    private String setting(String value, String envName) {
        if (value != null && !value.isBlank()) return value;
        String fromEnv = env().get(envName);
        return fromEnv != null && !fromEnv.isBlank() ? fromEnv : null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeRequest {
        private String question;
        private String answer;
    }

    // POST - Grade a student's answer
    @PostMapping("/grade")
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public ResponseEntity<?> grade(@RequestBody GradeRequest request) {
        try {
            String question = request.getQuestion();
            String answer = request.getAnswer();

            if (question == null || answer == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing question or answer"));
            }

            String apiKey = setting(geminiApiKey, "GEMINI_API_KEY");
            String apiUrl = setting(geminiApiUrl, "GEMINI_API_URL");
            if (apiKey == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "The Gemini API key is not set on the server (GEMINI_API_KEY)."));
            }
            if (apiUrl == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "The Gemini endpoint is not set on the server (GEMINI_API_URL or GEMINI_MODEL)."));
            }

            // Build the grading prompt
            String prompt = String.format("""
                You are an expert tutor grading a student's answer to a free-response question.
                Your task is to:
                1. Determine a grade for the student's response based on the following 0.55-1.0 scale, where 0.55 is the lowest, 0.9 is really good, and 1.0 is immaculate (don't often hand out grades higher than 0.92):
                   - 5: The answer addresses all parts of the question and is detailed and comprehensive.
                   - 4: The answer is correct and addresses most parts of the question.
                   - 3: The answer is correct but may be incomplete or lack detail.
                   - 2: The answer has significant inaccuracies or is incomplete.
                   - 1: The answer is incorrect or does not address the question.
                   Write the grade like this: "Grade: (0.55-1.0)/1.0"
                2. Provide detailed, constructive feedback explaining the grade.
                3. Offer very short suggestions on what the user could improve on.
                The question is: %s
                The student's response is: %s
                Format your final output with a clear heading for the grade and feedback. Also, do not use HTML or markdown formatting in your response, just simple text.
            """, question, answer);

            // Proper JSON payload with escaped characters
            String jsonPayload = String.format("""
                {
                    "contents": [{
                        "parts": [{
                            "text": "%s"    
                        }]
                    }]
                }
            """, prompt.replace("\"", "\\\"").replace("\n", "\\n"));

            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> httpRequest = new HttpEntity<>(jsonPayload, headers);
            String fullUrl = apiUrl + "?key=" + apiKey;

            // Send POST request to Gemini API
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.POST, httpRequest, String.class);

            String gradingResult = response.getBody();

            // Parse and modify response to update only the grading text
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseNode = mapper.readTree(gradingResult);
            
            // Update the text in candidates[0].content.parts[0].text with just the grading feedback
            String extractedText = extractGradingText(gradingResult);
            ((com.fasterxml.jackson.databind.node.ObjectNode) responseNode
                    .get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0))
                .put("text", extractedText);

            // Persist grading to DB without userId
            Gemini record = new Gemini(question, answer);
            record.setGradingResult(extractedText);
            Gemini saved = geminiRepository.save(record);

            // Return concise payload plus compatibility fields the frontend expects:
            // - "feedback" (string)
            // - "candidates": [{ "content": { "parts": [{ "text": "<feedback>" }] } }]
            return ResponseEntity.ok(
                Map.of(
                    "status", "success",
                    "id", saved.getId(),
                    "question", saved.getQuestion(),
                    "answer", saved.getAnswer(),
                    "gradingResult", saved.getGradingResult(),
                    "createdAt", saved.getCreatedAt(),
                    "feedback", extractedText,
                    "candidates", List.of(
                        Map.of(
                            "content", Map.of(
                                "parts", List.of(
                                    Map.of("text", extractedText)
                                )
                            )
                        )
                    )
                )
            );

        } catch (HttpClientErrorException.TooManyRequests e) {
            return ResponseEntity.status(429).body(Map.of("error", "Gemini quota exceeded. Please try again later."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    // GET - Fetch all grading results (no user filtering)
    @GetMapping("/grades")
    public ResponseEntity<?> getGrades() {
        List<Gemini> results = geminiRepository.findAll();
        return ResponseEntity.ok(Map.of(
            "count", results.size(),
            "results", results
        ));
    }

    // Helper method to extract grading text from Gemini API response
    private String extractGradingText(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            
            // Navigate: candidates[0].content.parts[0].text
            JsonNode textNode = root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");
            
            if (textNode.isTextual()) {
                return textNode.asText();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return "Error parsing grading result";
    }
}
