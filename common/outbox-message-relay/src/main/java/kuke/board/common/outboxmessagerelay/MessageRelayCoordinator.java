package kuke.board.common.outboxmessagerelay;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 메시지 릴레이 시스템에서 여러 애플리케이션 인스턴스 간의 조정을 담당하는 컴포넌트
 * Redis를 사용하여 활성 애플리케이션 인스턴스를 추적하고, 각 인스턴스에 샤드를 할당함
 */
@Component
@RequiredArgsConstructor
public class MessageRelayCoordinator {
    /** Redis와 통신하기 위한 템플릿 */
    private final StringRedisTemplate redisTemplate;

    /** 현재 실행 중인 애플리케이션 이름 (Spring 설정에서 주입) */
    @Value("${spring.application.name}")
    private String applicationName;

    /** 이 애플리케이션 인스턴스의 고유 ID (실행 시 생성) */
    private final String APP_ID = UUID.randomUUID().toString();

    /** 핑 간격 (초) - 얼마나 자주 활성 상태를 알릴지 결정 */
    private final int PING_INTERVAL_SECONDS = 3;

    /** 핑 임계값 (초) - 이 기간 내에 핑이 없으면 해당 애플리케이션은 비활성화된 것으로 간주 */
    private final int PING_INTERVAL_THRESHOLD = 3;

    /**
     * 현재 애플리케이션 인스턴스에 할당된 샤드 정보를 계산하여 반환
     * Redis에서 활성 애플리케이션 목록을 조회하고, 이를 기반으로 샤드 할당
     *
     * @return 현재 인스턴스에 할당된 샤드 정보
     */
    public AssignedShard assigneShards() {
        return AssignedShard.of(
                APP_ID,
                // Redis에서 활성 애플리케이션 목록을 조회하고 정렬
                redisTemplate.opsForZSet()
                             .reverseRange(generateAppKey(), 0, -1)
                             .stream()
                             .sorted()
                             .toList(),
                MessageRelayConstants.SHARD_COUNT
        );
    }

    /**
     * 주기적으로 Redis에 활성 상태를 알리는 메서드 (핑)
     * 지정된 간격(PING_INTERVAL_SECONDS)마다 자동 실행
     * 동시에 오래된 핑 정보를 제거하여 비활성 애플리케이션 정리
     */
    @Scheduled(fixedRate = PING_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    public void ping() {
        redisTemplate.executePipelined((RedisCallback<?>) action -> {
            StringRedisConnection conn = (StringRedisConnection) action;
            String key = generateAppKey();

            // 현재 시간을 스코어로 사용하여 활성 상태 업데이트
            conn.zAdd(key, Instant.now().toEpochMilli(), APP_ID);

            // 임계값(PING_INTERVAL_THRESHOLD)보다 오래된 핑 정보 제거
            conn.zRemRangeByScore(
                    key,
                    Double.NEGATIVE_INFINITY,
                    Instant.now().minusSeconds(PING_INTERVAL_THRESHOLD).toEpochMilli()
            );
            return null;
        });
    }

    /**
     * 애플리케이션 종료 시 Redis에서 자신의 정보를 제거
     * Spring 컨테이너가 종료될 때 자동으로 실행됨
     */
    @PreDestroy
    public void leave() {
        redisTemplate.opsForZSet()
                     .remove(generateAppKey(), APP_ID);
    }

    /**
     * Redis에서 사용할 키 이름을 생성
     * 애플리케이션 이름을 포함하여 여러 환경에서 충돌 방지
     *
     * @return Redis 키 이름
     */
    private String generateAppKey() {
        return "message-relay-coordinator::app-list::%s".formatted(applicationName);
    }
}