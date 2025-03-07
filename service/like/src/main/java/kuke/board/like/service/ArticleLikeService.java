package kuke.board.like.service;

//import kuke.board.common.event.EventType;
//import kuke.board.common.event.payload.ArticleLikedEventPayload;
//import kuke.board.common.event.payload.ArticleUnlikedEventPayload;
//import kuke.board.common.outboxmessagerelay.OutboxEventPublisher;
import kuke.board.common.snowflake.Snowflake;
import kuke.board.like.entity.ArticleLike;
import kuke.board.like.entity.ArticleLikeCount;
import kuke.board.like.repository.ArticleLikeCountRepository;
import kuke.board.like.repository.ArticleLikeRepository;
import kuke.board.like.service.response.ArticleLikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleLikeService {
    private final Snowflake snowflake = new Snowflake();
//    private final OutboxEventPublisher outboxEventPublisher;
    private final ArticleLikeRepository articleLikeRepository;
    private final ArticleLikeCountRepository articleLikeCountRepository;

    public ArticleLikeResponse read(Long articleId, Long userId) {
        return articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                .map(ArticleLikeResponse::from)
                .orElseThrow();
    }

    /**
     * 비관적 락 방식 1: UPDATE 구문 직접 사용
     * 특징: 별도의 SELECT FOR UPDATE 없이 UPDATE 쿼리를 바로 실행하여 행 락 획득
     */
    @Transactional
    public void likePessimisticLock1(Long articleId, Long userId) {
        // 1. 좋아요 엔티티를 먼저 저장
        ArticleLike articleLike = articleLikeRepository.save(
                ArticleLike.create(
                        snowflake.nextId(),
                        articleId,
                        userId
                )
        );

        // 2. UPDATE 쿼리를 직접 실행하여 좋아요 카운트 증가 시도
        // (articleLikeCountRepository.increase() 메서드는 내부적으로 UPDATE 쿼리 실행)
        int result = articleLikeCountRepository.increase(articleId);

        // 3. UPDATE 쿼리 결과가 0이면 (영향받은 행이 없음) 해당 게시글의 좋아요 카운트 레코드가 없는 상태
        if (result == 0) {
            // 4. 최초 요청 시 레코드가 없으므로 새로 생성하고 값을 1로 초기화
            // 주의: 동시에 여러 요청이 이 부분에 도달할 경우 중복 삽입 가능성 있음
            // 해결책: 게시글 생성 시점에 미리 카운트 레코드를 0으로 초기화해두는 것이 안전
            articleLikeCountRepository.save(
                    ArticleLikeCount.init(articleId, 1L)
            );
        }
    }

    /**
     * 비관적 락 방식 1: 좋아요 취소
     */
    @Transactional
    public void unlikePessimisticLock1(Long articleId, Long userId) {

        // 1. 사용자의 좋아요 기록 찾기
        articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                             .ifPresent(articleLike -> {
                                 // 2. 좋아요 기록이 있으면 삭제
                                 articleLikeRepository.delete(articleLike);

                                 // 3. 좋아요 카운트 감소 (내부적으로 UPDATE 쿼리 실행)
                                 // 이 방식에서는 카운트가 없는 경우에 대한 처리가 없음 (이미 존재한다고 가정)
                                 articleLikeCountRepository.decrease(articleId);
                             });

    }

    /**
     * 비관적 락 방식 2: SELECT FOR UPDATE + UPDATE
     * 특징: 먼저 레코드를 잠금 상태로 조회한 후 수정
     */
    @Transactional
    public void likePessimisticLock2(Long articleId, Long userId) {
        // 1. 좋아요 엔티티 저장
        articleLikeRepository.save(
                ArticleLike.create(
                        snowflake.nextId(),
                        articleId,
                        userId
                )
        );

        // 2. SELECT FOR UPDATE로 카운트 레코드를 잠금 상태로 조회
        // findLockedByArticleId() 메서드는 내부적으로 'SELECT ... FOR UPDATE' 쿼리 사용
        // 이 쿼리는 다른 트랜잭션이 해당 레코드에 접근하지 못하도록 행 락을 획득
        ArticleLikeCount articleLikeCount = articleLikeCountRepository.findLockedByArticleId(articleId)
                                                                      .orElseGet(() -> ArticleLikeCount.init(articleId,
                                                                                                             0L)); // 없으면 새로 생성

        // 3. 좋아요 카운트 증가 (엔티티 내부 상태 변경)
        articleLikeCount.increase();

        // 4. 변경된 엔티티 저장
        articleLikeCountRepository.save(articleLikeCount);
    }

    /**
     * 비관적 락 방식 2: 좋아요 취소
     */
    @Transactional
    public void unlikePessimisticLock2(Long articleId, Long userId) {
        // 1. 사용자의 좋아요 기록 찾기
        articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                             .ifPresent(articleLike -> {
                                 // 2. 좋아요 기록이 있으면 삭제
                                 articleLikeRepository.delete(articleLike);

                                 // 3. SELECT FOR UPDATE로 카운트 레코드를 잠금 상태로 조회
                                 ArticleLikeCount articleLikeCount = articleLikeCountRepository.findLockedByArticleId(articleId).orElseThrow();

                                 // 4. 좋아요 카운트 감소 (엔티티 내부 상태 변경)
                                 articleLikeCount.decrease();
                                 // 여기서는 save()가 명시적으로 호출되지 않지만, @Transactional에 의해 트랜잭션 종료 시 변경사항 자동 반영
                             });
    }

    /**
     * 낙관적 락 방식: 버전 정보를 이용한 충돌 감지
     * 특징: 별도의 잠금 없이 데이터를 읽고, 저장 시점에 버전 충돌 여부 확인
     * (ArticleLikeCount 엔티티에 @Version 필드가 있다고 가정)
     */
    @Transactional
    public void likeOptimisticLock(Long articleId, Long userId) {
        // 1. 좋아요 엔티티 저장
        articleLikeRepository.save(
                ArticleLike.create(
                        snowflake.nextId(),
                        articleId,
                        userId
                )
        );

        // 2. 카운트 레코드를 일반 조회 (잠금 없음)
        ArticleLikeCount articleLikeCount = articleLikeCountRepository.findById(articleId)
                                                                      .orElseGet(() -> ArticleLikeCount.init(articleId, 0L)); // 없으면 새로 생성

        // 3. 좋아요 카운트 증가 (엔티티 내부 상태 변경)
        articleLikeCount.increase();

        // 4. 변경된 엔티티 저장
        // 이 시점에 다른 트랜잭션이 동일 레코드를 변경했다면 버전 불일치로 OptimisticLockException 발생
        // 예외 발생 시 트랜잭션은 롤백되며, 애플리케이션에서 재시도 로직을 구현해야 함
        articleLikeCountRepository.save(articleLikeCount);
    }

    /**
     * 낙관적 락 방식: 좋아요 취소
     */
    @Transactional
    public void unlikeOptimisticLock(Long articleId, Long userId) {
        // 1. 사용자의 좋아요 기록 찾기
        articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                             .ifPresent(articleLike -> {
                                 // 2. 좋아요 기록이 있으면 삭제
                                 articleLikeRepository.delete(articleLike);

                                 // 3. 카운트 레코드를 일반 조회 (잠금 없음)
                                 ArticleLikeCount articleLikeCount = articleLikeCountRepository.findById(articleId).orElseThrow();

                                 // 4. 좋아요 카운트 감소 (엔티티 내부 상태 변경)
                                 articleLikeCount.decrease();

                                 // 저장 구문이 없지만 @Transactional에 의해 트랜잭션 종료 시 변경사항 자동 반영
                                 // 이 시점에 버전 충돌 발생 가능 (낙관적 락 예외 처리 필요)
                             });
    }

    public Long count(Long articleId) {
        return articleLikeCountRepository.findById(articleId)
                .map(ArticleLikeCount::getLikeCount)
                .orElse(0L);
    }
}
