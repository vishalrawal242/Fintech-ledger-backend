package com.fintech.ledger.publisher;

import com.fintech.ledger.entity.OutboxMessage;
import com.fintech.ledger.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ledger.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${ledger.kafka.topic}")
    private String topic;

    @Scheduled(fixedDelay = 2000)
    public void publishPending() {
        List<OutboxMessage> pending = outboxMessageRepository.findByStatusOrderByCreatedAtAsc(STATUS_PENDING);
        for (OutboxMessage message : pending) {
            try {
                kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload())
                        .get(10, TimeUnit.SECONDS);
                message.setStatus(STATUS_SENT);
                outboxMessageRepository.save(message);
                log.info("Published outbox message id={} to topic={}", message.getId(), topic);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("Could not publish outbox message id={}, will retry on next poll", message.getId(), ex);
                return;
            }
        }
    }
}