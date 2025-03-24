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

/**
 * Outbox 패턴을 구현한 메시지 릴레이 컴포넌트.
 *
 * 이 클래스는 트랜잭션 이벤트를 감지하여 메시지를 Outbox 테이블에 저장하고,
 * 트랜잭션 완료 후 Kafka로 전송하는 역할을 담당합니다.
 * 또한 주기적으로 전송에 실패한 메시지를 재시도하는 기능을 제공합니다.
 *
 * Outbox 패턴은 데이터베이스 트랜잭션과 메시지 발행의 원자성을 보장하기 위한 패턴입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay {
  private final OutboxRepository outboxRepository;
  private final MessageRelayCoordinator messageRelayCoordinator;
  private final KafkaTemplate<String, String> messageRelayKafkaTemplate;

  /**
   * 트랜잭션 커밋 전에 Outbox 이벤트를 처리하여 메시지를 Outbox 테이블에 저장합니다.
   *
   * 데이터베이스 트랜잭션과 함께 처리되므로, 트랜잭션이 롤백되면 Outbox 저장도 롤백됩니다.
   *
   * @param outboxEvent 저장할 Outbox 이벤트
   */
  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void createOutbox(OutboxEvent outboxEvent) {
    log.info("[MessageRelay.createOutbox] outboxEvent : {}", outboxEvent);
    outboxRepository.save(outboxEvent.getOutbox());
  }

  /**
   * 트랜잭션 커밋 후에 비동기로 Outbox 메시지를 Kafka로 발행합니다.
   *
   * 메시지 발행이 성공하면 Outbox 레코드를 삭제하고, 실패하면 로그를 남깁니다.
   * 실패한 메시지는 나중에 scheduledJob에 의해 재처리됩니다.
   *
   * @param outboxEvent 발행할 Outbox 이벤트
   */
  @Async("messageRelayPublisherEventExecutor") // 비동기로 메시지 발행 작업 실행. 별도 스레드 풀 사용
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 트랜잭션 커밋 후 실행
  public void publishOutbox(OutboxEvent outboxEvent) {
    Outbox outbox = outboxEvent.getOutbox();
    try {
      // Kafka에 메시지를 전송하고 최대 1초 동안 응답을 기다림
      // get(1, TimeUnit.SECONDS)는 비동기 작업을 동기적으로 처리하게 만들며, 1초 후에는 TimeoutException 발생
      messageRelayKafkaTemplate.send(
          outbox.getEventType().getTopic(),
          String.valueOf(outbox.getShardKey()),
          outbox.getPayload()
      ).get(1, TimeUnit.SECONDS);
      outboxRepository.delete(outbox); // 성공적으로 발행된 메시지는 Outbox에서 삭제
    } catch (Exception e) {
      log.error("[MessageRelay.publishOutbox] outbox : {}", outbox, e);
      // 예외 발생 시 Outbox 레코드는 삭제되지 않고 남아있어 나중에 재처리 가능
    }
  }

  /**
   * Outbox 메시지를 Kafka로 발행하는 내부 메소드.
   *
   * @param outbox 발행할 Outbox 메시지
   */
  private void publishEvent(Outbox outbox) {
    try {
      // Kafka에 메시지 전송 및 응답 대기(최대 1초)
      messageRelayKafkaTemplate.send(
          outbox.getEventType().getTopic(),
          String.valueOf(outbox.getShardKey()),
          outbox.getPayload()
      ).get(1, TimeUnit.SECONDS);
      outboxRepository.delete(outbox); // 성공 시 Outbox 레코드 삭제
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("[MessageRelay.publishEvent] outbox : {}", outbox, e);
      // 실패 시 로그만 남기고 레코드 유지 (다음 스케줄링에서 재시도)
    }
  }

  /**
   * 주기적으로 미처리된 Outbox 메시지를 발행하는 스케줄링 메소드.
   *
   * 이 메소드는 초기 5초 후 시작되어 10초마다 실행되며,
   * 샤딩 기반으로 작업을 분산하여 처리합니다.
   */
  @Scheduled(
      fixedDelay = 10,  // 작업 완료 후 10초 간격으로 실행
      initialDelay = 5, // 애플리케이션 시작 후 5초 후 첫 실행
      timeUnit = TimeUnit.SECONDS,
      scheduler = "messageRelayPublishPendingEventExecutor" // 사용할 스케줄러(스레드 풀) 지정
  )
  public void publisherPendingEvent() {
    // 현재 인스턴스가 처리해야 할 샤드 목록 할당받음 (분산 환경에서 여러 인스턴스가 같은 샤드를 처리하지 않도록)
    AssignedShard assignedShard = messageRelayCoordinator.assigneShards();
    for (Long shard : assignedShard.getShards()) {
      // 각 샤드별로 10초 이전에 생성된 미처리 메시지를 최대 100개까지 조회
      // 너무 오래된 메시지만 처리하여 최근 발행된 메시지와의 충돌 방지
      List<Outbox> outboxes = outboxRepository.findAllByShardKeyAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
          shard,
          LocalDateTime.now().minusSeconds(10),
          Pageable.ofSize(100) // 한 번에 처리할 메시지 수 제한
      );

      // 조회된 각 메시지를 순차적으로 발행
      for (Outbox outbox : outboxes) {
        publishEvent(outbox);
      }
    }
  }
}