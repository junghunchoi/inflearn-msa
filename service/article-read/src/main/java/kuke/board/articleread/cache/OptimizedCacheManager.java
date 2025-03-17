package kuke.board.articleread.cache;

import kuke.board.common.dataserializer.DataSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static java.util.stream.Collectors.joining;

/**
 * 최적화된 캐싱 로직을 처리하는 매니저 클래스
 * 캐시 조회, 갱신, 만료 처리 등 핵심 캐싱 로직을 관리
 */
@Component
@RequiredArgsConstructor
public class OptimizedCacheManager {
    private final StringRedisTemplate redisTemplate;
    private final OptimizedCacheLockProvider optimizedCacheLockProvider;

    private static final String DELIMITER = "::";

    /**
     * 캐시 처리의 핵심 로직을 수행하는 메소드
     *
     * @param type 캐시 타입(prefix)
     * @param ttlSeconds 캐시 유효 시간(초)
     * @param args 메소드 인자 값들(캐시 키 생성에 사용)
     * @param returnType 반환 타입 클래스
     * @param originDataSupplier 원본 데이터 제공자(실제 메소드 호출 래퍼)
     * @return 캐시된 데이터 또는 신규 조회 데이터
     * @throws Throwable 원본 메소드 실행 중 발생할 수 있는 모든 예외
     */
    public Object process(String type, long ttlSeconds, Object[] args, Class<?> returnType,
        OptimizedCacheOriginDataSupplier<?> originDataSupplier) throws Throwable {
        // 캐시 키 생성
        String key = generateKey(type, args);

        // Redis에서 캐시된 데이터 조회
        String cachedData = redisTemplate.opsForValue().get(key);
        // 캐시 데이터가 없는 경우(캐시 미스) 원본 데이터 조회 및 캐싱
        if (cachedData == null) {
            return refresh(originDataSupplier, key, ttlSeconds);
        }

        // 캐시된 데이터를 OptimizedCache 객체로 역직렬화
        OptimizedCache optimizedCache = DataSerializer.deserialize(cachedData, OptimizedCache.class);
        // 역직렬화 실패 시 원본 데이터 조회 및 캐싱
        if (optimizedCache == null) {
            return refresh(originDataSupplier, key, ttlSeconds);
        }

        // 캐시가 만료되지 않았으면 캐시된 데이터 반환
        if (!optimizedCache.isExpired()) {
            return optimizedCache.parseData(returnType);
        }

        // 캐시가 만료된 경우 분산 락 획득 시도
        if (!optimizedCacheLockProvider.lock(key)) {
            // 락 획득 실패 시(다른 스레드/프로세스가 이미 갱신 중) 기존 캐시 데이터 반환
            return optimizedCache.parseData(returnType);
        }

        try {
            // 락 획득 성공 시 원본 데이터 조회 및 캐싱
            return refresh(originDataSupplier, key, ttlSeconds);
        } finally {
            // 작업 완료 후 반드시 락 해제
            optimizedCacheLockProvider.unlock(key);
        }
    }

    /**
     * 원본 데이터를 조회하고 캐싱하는 메소드
     *
     * @param originDataSupplier 원본 데이터 제공자
     * @param key 캐시 키
     * @param ttlSeconds 캐시 유효 시간(초)
     * @return 원본 데이터 조회 결과
     * @throws Throwable 원본 데이터 조회 중 발생할 수 있는 모든 예외
     */
    private Object refresh(OptimizedCacheOriginDataSupplier<?> originDataSupplier, String key, long ttlSeconds) throws Throwable {
        // 원본 데이터 조회(실제 메소드 실행)
        Object result = originDataSupplier.get();

        // TTL 설정 객체 생성(논리적/물리적 TTL 구분)
        OptimizedCacheTTL optimizedCacheTTL = OptimizedCacheTTL.of(ttlSeconds);
        // 원본 데이터와 논리적 TTL로 캐시 객체 생성
        OptimizedCache optimizedCache = OptimizedCache.of(result, optimizedCacheTTL.getLogicalTTL());

        // Redis에 캐시 데이터 저장(물리적 TTL 적용)
        redisTemplate.opsForValue()
            .set(
                key,
                DataSerializer.serialize(optimizedCache),
                optimizedCacheTTL.getPhysicalTTL()
            );

        // 원본 데이터 반환
        return result;
    }

    private String generateKey(String prefix, Object[] args) {
        // 접두어와 인자 값들을 구분자로 연결하여 캐시 키 생성
        return prefix + DELIMITER +
            Arrays.stream(args)
                .map(String::valueOf)
                .collect(joining(DELIMITER));
    }
}
