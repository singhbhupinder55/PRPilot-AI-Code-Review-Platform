package dev.prpilot.review.kafka;

public record ReviewCompletedEvent(
        String deliveryId,
        String repoFullName,
        Long prNumber,
        String htmlUrl,
        String reviewBody
) {}