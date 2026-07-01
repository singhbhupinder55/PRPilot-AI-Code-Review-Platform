package dev.prpilot.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors the event published by review-service to reviews.completed.
 * Each service owns its own copy of the event contract — no shared library.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewCompletedEvent(
        String deliveryId,
        String repoFullName,
        Long prNumber,
        String htmlUrl,
        String reviewBody
) {}