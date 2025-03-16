package kuke.board.articleread.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 게시판별 아티클 ID 목록을 Redis에 저장하고 관리하는 레포지토리 클래스.
 * 정렬된 집합(Sorted Set)을 사용하여 아티클 ID를 저장하고, 페이징 및 무한 스크롤 조회를 지원합니다.
 */
@Repository
@RequiredArgsConstructor
public class ArticleIdListRepository {
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_FORMAT = "article-read::board::%s::article-list";

    /**
     * 게시판에 새 아티클 ID를 추가하고, 최대 개수를 초과하는 경우 가장 오래된 항목을 제거합니다.
     * Redis의 파이프라인을 사용하여 두 작업을 효율적으로 실행합니다.
     *
     * @param boardId 게시판 ID
     * @param articleId 추가할 아티클 ID
     * @param limit 유지할 최대 아티클 수
     */
    public void add(Long boardId, Long articleId, Long limit) {
        redisTemplate.executePipelined((RedisCallback<?>) action -> {
            StringRedisConnection conn = (StringRedisConnection) action;
            String key = generateKey(boardId);
            conn.zAdd(key, 0, toPaddedString(articleId));  // 아티클 ID 추가 (모든 score는 0으로 설정)
            conn.zRemRange(key, 0, - limit - 1);  // 최대 개수를 초과하는 경우 가장 오래된 항목 제거
            return null;
        });
    }

    /**
     * 게시판에서 특정 아티클 ID를 삭제합니다.
     *
     * @param boardId 게시판 ID
     * @param articleId 삭제할 아티클 ID
     */
    public void delete(Long boardId, Long articleId) {
        redisTemplate.opsForZSet().remove(generateKey(boardId), toPaddedString(articleId));
    }

    /**
     * 게시판의 아티클 ID 목록을 페이징하여 조회합니다.
     * 아티클 ID는 역순(최신순)으로 정렬됩니다.
     *
     * @param boardId 게시판 ID
     * @param offset 시작 오프셋
     * @param limit 조회할 최대 개수
     * @return 아티클 ID 목록
     */
    public List<Long> readAll(Long boardId, Long offset, Long limit) {
        return redisTemplate.opsForZSet()
            .reverseRange(generateKey(boardId), offset, offset + limit - 1)
            .stream().map(Long::valueOf).toList();
    }

    /**
     * 무한 스크롤 방식으로 게시판의 아티클 ID 목록을 조회합니다.
     * 마지막으로 조회한 아티클 ID 이후의 아티클들을 가져옵니다.
     * 아티클 ID는 역순(최신순)으로 정렬됩니다.
     *
     * @param boardId 게시판 ID
     * @param lastArticleId 마지막으로 조회한 아티클 ID (null인 경우 처음부터 조회)
     * @param limit 조회할 최대 개수
     * @return 아티클 ID 목록
     */
    public List<Long> readAllInfiniteScroll(Long boardId, Long lastArticleId, Long limit) {
        return redisTemplate.opsForZSet().reverseRangeByLex(
            generateKey(boardId),
            lastArticleId == null ?
                Range.unbounded() :  // 처음 조회 시 전체 범위
                Range.leftUnbounded(Range.Bound.exclusive(toPaddedString(lastArticleId))),  // 마지막 ID 이후부터
            Limit.limit().count(limit.intValue())
        ).stream().map(Long::valueOf).toList();
    }

    /**
     * 아티클 ID를 19자리 패딩된 문자열로 변환합니다.
     * 이렇게 하면 문자열 비교 시 숫자 순서가 유지됩니다.
     *
     * @param articleId 변환할 아티클 ID
     * @return 패딩된 문자열 (예: 1234 -> 0000000000000001234)
     */
    private String toPaddedString(Long articleId) {
        return "%019d".formatted(articleId);
        // 1234 -> 0000000000000001234
    }

    /**
     * 게시판 ID로부터 Redis 키를 생성하는 헬퍼 메소드
     *
     * @param boardId 게시판 ID
     * @return 생성된 Redis 키
     */
    private String generateKey(Long boardId) {
        return KEY_FORMAT.formatted(boardId);
    }
}