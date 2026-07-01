package dev.prpilot.notification.kafka;

import dev.prpilot.notification.github.GitHubCommentService;
import dev.prpilot.notification.model.ReviewCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes reviews.completed events and posts the review
 * as a comment on the GitHub PR.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewCompletedConsumer {

    private final GitHubCommentService gitHubCommentService;

    @KafkaListener(
            topics = "${prpilot.kafka.topics.reviews-completed}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onReviewCompleted(ReviewCompletedEvent event) {
        log.info("Consumed reviews.completed: delivery={}, repo={}, pr=#{}",
                event.deliveryId(), event.repoFullName(), event.prNumber());

        try {
            gitHubCommentService.postPrComment(
                    event.repoFullName(),
                    event.prNumber(),
                    event.reviewBody());

            log.info("Notified GitHub for delivery={}", event.deliveryId());

        } catch (Exception e) {
            log.error("Failed to post GitHub comment for delivery={}",
                    event.deliveryId(), e);
            // Note: no retry here yet — a dead-letter queue would be the
            // production hardening step for failed GitHub API calls
        }
    }
}