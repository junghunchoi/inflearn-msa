package kuke.board.articleread.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis를 사용하여 분산 환경에서 캐시 작업의 동시성을 제어하기 위한 락(Lock) 제공 클래스
 */
@Component
@RequiredArgsConstructor
public class OptimizedCacheLockProvider {
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "optimized-cache-lock::";
    // 락의 유효 시간 - 3초로 설정하여 데드락 방지
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    /**
     * 지정된 키에 대한 락 획득을 시도
     *
     * @param key 락을 획득할 대상 키
     * @return 락 획득 성공 여부 (true: 성공, false: 실패)
     */
    public boolean lock(String key) {
        // Redis의 SETNX(SET if Not eXists) 명령어를 사용하여 락 획득 시도
        // 키가 존재하지 않을 때만 값을 설정하고 TTL을 적용
        return redisTemplate.opsForValue().setIfAbsent(
            generateLockKey(key), // 락 키 생성
            "",                  // 값은 중요하지 않음(빈 문자열 사용)
            LOCK_TTL            // 락의 유효 시간
        );
    }

    /**
     * 지정된 키에 대한 락 해제
     *
     * @param key 락을 해제할 대상 키
     */
    public void unlock(String key) {
        // Redis에서 락 키를 삭제하여 락 해제
        redisTemplate.delete(generateLockKey(key));
    }


    private String generateLockKey(String key) {
        return KEY_PREFIX + key;
    }
}
