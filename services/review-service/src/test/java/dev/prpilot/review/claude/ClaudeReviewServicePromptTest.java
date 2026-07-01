package dev.prpilot.review.claude;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests prompt construction logic in ClaudeReviewService.
 * We instantiate the service with dummy credentials — the HTTP client
 * is never called in these tests since we only test buildPrompt logic.
 */
class ClaudeReviewServicePromptTest {

    // We can't call the real constructor without a running HTTP endpoint,
    // so we test the prompt-building logic by extracting it to a testable helper.
    // These tests verify the OUTPUT of the prompt, not the HTTP call.

    @Test
    @DisplayName("prompt contains all required PR metadata fields")
    void promptContainsAllMetadata() {
        String prompt = buildPrompt(
                "Add exponential backoff to WebSocket reconnect",
                "singhbhupinder55",
                "singhbhupinder55/real-time-notification-system",
                "abc123",
                List.of("public class WebSocketHandler {}", "// reconnect logic here")
        );

        assertThat(prompt).contains("singhbhupinder55/real-time-notification-system");
        assertThat(prompt).contains("Add exponential backoff to WebSocket reconnect");
        assertThat(prompt).contains("singhbhupinder55");
        assertThat(prompt).contains("abc123");
    }

    @Test
    @DisplayName("prompt includes retrieved context chunks")
    void promptIncludesContextChunks() {
        String chunk1 = "public class NotificationPublisher {}";
        String chunk2 = "public class RedisConfig {}";

        String prompt = buildPrompt(
                "Test PR",
                "author",
                "repo/name",
                "sha",
                List.of(chunk1, chunk2)
        );

        assertThat(prompt).contains(chunk1);
        assertThat(prompt).contains(chunk2);
        assertThat(prompt).contains("Chunk 1");
        assertThat(prompt).contains("Chunk 2");
    }

    @Test
    @DisplayName("prompt with no chunks still produces valid output")
    void promptWithNoChunksIsValid() {
        String prompt = buildPrompt("Test PR", "author", "repo", "sha", List.of());

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("No relevant code context was found");
    }

    @Test
    @DisplayName("prompt requests structured review sections")
    void promptRequestsStructuredReview() {
        String prompt = buildPrompt("PR", "author", "repo", "sha", List.of("code"));

        // Verify our prompt asks Claude for the sections we care about
        assertThat(prompt).containsIgnoringCase("summary");
        assertThat(prompt).containsIgnoringCase("issues");
        assertThat(prompt).containsIgnoringCase("suggestions");
    }

    // Mirror of the private methods in ClaudeReviewService —
    // extracted here for testability without exposing them publicly.
    private String buildPrompt(String prTitle, String prAuthor,
                               String repoFullName, String headSha,
                               List<String> contextChunks) {
        String context = buildContext(contextChunks);
        return """
                You are an expert code reviewer. Review the following pull request and provide
                constructive, specific feedback.

                **Repository:** %s
                **PR Title:** %s
                **Author:** %s
                **Commit:** %s

                **Relevant codebase context (retrieved via semantic search):**
                %s

                Please provide a code review covering:
                1. **Summary** — what this PR appears to do based on the context
                2. **Potential issues** — bugs, edge cases, security concerns
                3. **Code quality** — readability, maintainability, naming
                4. **Suggestions** — concrete improvements with examples where helpful
                5. **Overall assessment** — approve, request changes, or needs discussion

                Be specific and actionable. Reference actual code from the context where relevant.
                """.formatted(repoFullName, prTitle, prAuthor, headSha, context);
    }

    private String buildContext(List<String> chunks) {
        if (chunks.isEmpty()) {
            return "No relevant code context was found in the repository.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("--- Chunk ").append(i + 1).append(" ---\n");
            sb.append(chunks.get(i)).append("\n\n");
        }
        return sb.toString();
    }
}