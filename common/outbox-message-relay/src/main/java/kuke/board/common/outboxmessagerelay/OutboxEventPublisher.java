package kuke.board.common.outboxmessagerelay;

import kuke.board.common.event.Event;
import kuke.board.common.event.EventPayload;
import kuke.board.common.event.EventType;
import kuke.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final Snowflake outboxIdsnowflake = new Snowflake();
    private final Snowflake eventIdsnowflake = new Snowflake();
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(EventType type, EventPayload payload, Long shardKey) {
        Event event = Event.of(eventIdsnowflake.nextId(), type, payload);
        System.out.println("Event 객체: " + event);

        String jsonPayload = event.toJson();
        System.out.println("JSON 변환 결과: " + jsonPayload);
        Outbox outbox = Outbox.create(
            outboxIdsnowflake.nextId(),
            type,
            Event.of(eventIdsnowflake.nextId(), type, payload).toJson(),
            shardKey % MessageRelayConstants.SHARD_COUNT);

        applicationEventPublisher.publishEvent(OutboxEvent.of(outbox));
    }
}
