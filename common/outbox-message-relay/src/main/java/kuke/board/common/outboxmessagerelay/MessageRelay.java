package kuke.board.common.outboxmessagerelay;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay {
    private final OutboxRepository outboxRepository;
    private final MessageRelayCoordinator messageRelayCoordinator;
    private final KafkaTemplate<String, String> messageRelayKafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createOutbox(OutboxEvent outboxEvent) {
        log.info("[MessageRelay.createOutbox] outboxEvent : {}", outboxEvent);
        outboxRepository.save(outboxEvent.getOutbox());
    }

    @Async("messageRelayPublisherEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOutbox(OutboxEvent outboxEvent) {
        Outbox outbox = outboxEvent.getOutbox();
      try {
        messageRelayKafkaTemplate.send(
            outbox.getEventType().getTopic(),
            String.valueOf(outbox.getShardKey()),
            outbox.getPayload()
        ).get(1, TimeUnit.SECONDS);
        outboxRepository.delete(outbox);
      } catch (Exception e) {
        log.error("[MessageRelay.publishOutbox] outbox : {}", outbox, e);
      }
    }
    private void publishEvent(Outbox outbox) {
        try {
            messageRelayKafkaTemplate.send(
                outbox.getEventType().getTopic(),
                String.valueOf(outbox.getShardKey()),
                outbox.getPayload()
            ).get(1, TimeUnit.SECONDS);
            outboxRepository.delete(outbox);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("[MessageRelay.publishEvent] outbox : {}", outbox, e);
        }
    }


    @Scheduled(
        fixedDelay = 10,
        initialDelay = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "messageRelayPublishPendingEventExecutor"
    )
    public void publisherPendingEvent() {
        AssignedShard assignedShard = messageRelayCoordinator.assigneShards();
        log.info("[MessageRelay.publisherPendingEvent] assignedShard : {}", assignedShard.getShards().size());
      for (Long shard : assignedShard.getShards()) {
        List<Outbox> outboxes = outboxRepository.findAllByShardKeyAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            shard,
            LocalDateTime.now().minusSeconds(10),
            Pageable.ofSize(100)
        );

        for (Outbox outbox : outboxes) {
          publishEvent(outbox);
        }
      }
    }

}
