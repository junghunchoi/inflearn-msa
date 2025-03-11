package kuke.board.hotarticle.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class ArticleCreatedTimeRepository {
    private final StringRedisTemplate redisTemplate;

    // hot-article::article::{articleId}::created-time
    private static final String KEY_FORMAT = "hot-article::article::%s::created-time";

    /**
     * Redis에 게시글의 생성 시간을 저장하거나 업데이트하는 메서드
     * 생성시간을 저장하면 해당 서비스의 조회없이도 데이터 관리가 가능하다
     *
     * @param articleId 게시글 ID (키 생성에 사용됨)
     * @param createdAt 게시글 생성 시간
     * @param ttl 데이터 만료 시간(Time-To-Live)
     */
    public void createOrUpdate(Long articleId, LocalDateTime createdAt, Duration ttl) {
        redisTemplate.opsForValue().set(
                // 게시글 ID를 기반으로 Redis 키 생성
                generateKey(articleId),
                // 생성 시간을 UTC 기준 epoch 밀리초로 변환하여 문자열로 저장
                String.valueOf(createdAt.toInstant(ZoneOffset.UTC).toEpochMilli()),
                // 지정된 시간 후 데이터 자동 만료 설정
                ttl
        );
    }

    public void delete(Long articleId) {
        redisTemplate.delete(generateKey(articleId));
    }

    public LocalDateTime read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        if (result == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(Long.valueOf(result)), ZoneOffset.UTC
        );
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
