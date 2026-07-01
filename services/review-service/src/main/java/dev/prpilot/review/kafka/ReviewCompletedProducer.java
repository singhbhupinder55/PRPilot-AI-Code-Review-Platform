package dev.prpilot.review.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewCompletedProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${prpilot.kafka.topics.reviews-completed:reviews.completed}")
    private String topic;

    public void publish(ReviewCompletedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.repoFullName(), payload);
            log.info("Published review.completed for delivery={}", event.deliveryId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ReviewCompletedEvent", e);
        }
    }
}