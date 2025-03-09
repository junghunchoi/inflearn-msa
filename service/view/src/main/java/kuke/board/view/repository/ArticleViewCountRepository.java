package kuke.board.view.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;


/**
 * redis는 인터페이스가 아니라 class로 만드나봄
 */
@Repository
@RequiredArgsConstructor
public class ArticleViewCountRepository {
    private final StringRedisTemplate redisTemplate;

    // redis 에서 키값을 고정으로 쓰기 위해선 String api 를 통해서 값을 설정한 후 삽입함
    // view::article::{article_id}::view_count
    private static final String KEY_FORMAT = "view::article::%s::view_count";

    public Long read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        return result == null ? 0L : Long.valueOf(result);
    }

    public Long increase(Long articleId) {
        return redisTemplate.opsForValue().increment(generateKey(articleId));
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
