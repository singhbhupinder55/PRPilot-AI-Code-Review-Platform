package dev.prpilot.notification;

import dev.prpilot.notification.github.GitHubCommentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
class NotificationServiceIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("prpilot.github.token", () -> "test-token");
        registry.add("prpilot.github.api-url", () -> "https://api.github.com");
    }

    @MockitoBean
    GitHubCommentService gitHubCommentService;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("reviews.completed event triggers GitHub PR comment")
    void reviewCompletedTriggersGitHubComment() {
        // Send raw JSON string — avoids serializer type mismatch in tests.
        // The consumer's JsonDeserializer will deserialize it into ReviewCompletedEvent.
        String payload = """
                {
                  "deliveryId": "notif-test-001",
                  "repoFullName": "test/test-repo",
                  "prNumber": 1,
                  "htmlUrl": "https://github.com/test/test-repo/pull/1",
                  "reviewBody": "## Code Review\\n\\nLooks good!"
                }
                """;

        kafkaTemplate.send("reviews.completed", "test/test-repo", payload);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                verify(gitHubCommentService).postPrComment(
                        anyString(), anyLong(), anyString()));
    }
}