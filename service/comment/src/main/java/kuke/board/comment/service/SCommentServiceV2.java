package kuke.board.comment.service;

import kuke.board.comment.entity.ArticleCommentCount;
import kuke.board.comment.entity.CommentPath;
import kuke.board.comment.entity.CommentV2;
import kuke.board.comment.repository.ArticleCommentCountRepository;
import kuke.board.comment.repository.CommentRepositoryV2;
import kuke.board.comment.service.request.CommentCreateRequestV2;
import kuke.board.comment.service.response.CommentPageResponse;
import kuke.board.comment.service.response.CommentResponse;
import kuke.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.function.Predicate.not;

/**
 * 학습용 서비스 서비스 클래스
 */
@Service
@RequiredArgsConstructor
public class SCommentServiceV2 {

    private final Snowflake snowflake = new Snowflake();
    private final CommentRepositoryV2 commentRepository;
    //    private final OutboxEventPublisher outboxEventPublisher;
    private final ArticleCommentCountRepository articleCommentCountRepository;

    /**
     * 댓글 생성
     * 사용자가 작성한 댓글을 저장하고, 댓글 수를 증가시키는 기능
     * 계층형 구조 지원을 위해 부모 댓글 정보를 활용하여 경로(path) 생성
     *
     * @param request 댓글 생성 요청 정보 (내용, 게시글 ID, 작성자 ID, 부모 댓글 경로)
     * @return 생성된 댓글 정보
     */
    @Transactional
    public CommentResponse create(CommentCreateRequestV2 request) {
        CommentV2 commentV2 = commentRepository.save(CommentV2.create(
                snowflake.nextId(),
                request.getContent(),
                request.getArticleId(),
                request.getWriterId(),
                CommentPath.create(request.getParentPath())
        ));

        return CommentResponse.from(commentV2);
    }

    /**
     * 부모 댓글 찾기
     * 답글 작성 시 부모 댓글을 조회하는 기능
     * 부모 경로가 없거나 삭제된 댓글인 경우 처리
     *
     * @param request 댓글 생성 요청 정보
     * @return 부모 댓글 객체 또는 null
     */
    private CommentV2 findParent(CommentCreateRequestV2 request) {
        String parentPath = request.getParentPath();

        if (parentPath == null) {
            return null;
        }

        return commentRepository.findByPath(parentPath)
                .filter(not(CommentV2::getDeleted))
                                .orElseThrow();
    }

    /**
     * 댓글 조회
     * 특정 댓글 ID를 기반으로 댓글 정보를 조회하는 기능
     *
     * @param commentId 조회할 댓글 ID
     * @return 조회된 댓글 정보
     */
    public CommentResponse read(Long commentId) {
        return CommentResponse.from(commentRepository.findById(commentId)
                                              .orElseThrow());
    }

    /**
     * 댓글 삭제
     * 특정 댓글 ID를 기반으로 댓글을 삭제하는 기능
     * 하위 댓글이 있는 경우 내용만 삭제 처리하고, 없는 경우 실제 댓글 삭제
     *
     * @param commentId 삭제할 댓글 ID
     */
    @Transactional
    public void delete(Long commentId) {
        commentRepository.findById(commentId)
                .ifPresent(commentV2 -> {
                    if (hasChildren(commentV2)) {
                        commentV2.delete();
                    } else {
                        delete(commentV2);
                    }
                });
    }

    /**
     * 하위 댓글 존재 여부 확인
     * 특정 댓글에 하위 댓글이 있는지 확인하는 기능
     *
     * @param comment 확인할 댓글 객체
     * @return 하위 댓글 존재 여부
     */
    private boolean hasChildren(CommentV2 comment) {
        return commentRepository.findDescendantsTopPath(
                comment.getArticleId(),
                comment.getCommentPath().getPath()
        ).isPresent();
    }

    /**
     * 댓글 실제 삭제 처리
     * 댓글을 실제로 삭제하고 댓글 수를 감소시키는 기능
     * 부모 댓글까지 연쇄적으로 삭제 조건 확인 및 처리
     *
     * @param comment 삭제할 댓글 객체
     */
    private void delete(CommentV2 comment) {
        commentRepository.delete(comment);
        articleCommentCountRepository.decrease(comment.getArticleId());
        if (!comment.isRoot()) {
            commentRepository.findByPath(comment.getCommentPath().getParentPath())
                             .filter(CommentV2::getDeleted)
                             .filter(not(this::hasChildren))
                             .ifPresent(this::delete);
        }
    }

    /**
     * 페이지 단위 댓글 목록 조회
     * 특정 게시글의 댓글을 페이지 단위로 조회하는 기능
     *
     * @param articleId 게시글 ID
     * @param page 조회할 페이지 번호
     * @param pageSize 페이지당 댓글 수
     * @return 페이지네이션이 적용된 댓글 목록 및 페이지 정보
     */
    public CommentPageResponse readAll(Long articleId, Long page, Long pageSize) {
        return CommentPageResponse.of(
                commentRepository.findAll(articleId, (page - 1) * pageSize, pageSize)
                                 .stream()
                                 .map(CommentResponse::from)
                                 .toList(),
                commentRepository.count(articleId, PageLimitCalculator.calculatePageLimit(page, pageSize, 10L))
        );
    }

    /**
     * 무한 스크롤용 댓글 목록 조회
     * 특정 게시글의 댓글을 무한 스크롤 방식으로 조회하는 기능
     * 마지막으로 조회한 댓글 이후의 댓글을 가져오는 방식
     *
     * @param articleId 게시글 ID
     * @param lastPath 마지막으로 조회한 댓글의 경로
     * @param pageSize 조회할 댓글 수
     * @return 조회된 댓글 목록
     */
    public List<CommentResponse> readAllInfiniteScroll(Long articleId, String lastPath, Long pageSize) {
        List<CommentV2> commentV2s = lastPath == null ?
                commentRepository.findAllInfiniteScroll(articleId, pageSize) :
                commentRepository.findAllInfiniteScroll(articleId, lastPath, pageSize);

        return commentV2s.stream()
                         .map(CommentResponse::from)
                         .toList();
    }

    /**
     * 게시글별 댓글 수 조회
     * 특정 게시글의 총 댓글 수를 조회하는 기능
     *
     * @param articleId 게시글 ID
     * @return 댓글 수
     */
    public Long count(Long articleId) {
        return articleCommentCountRepository.findById(articleId)
                                            .map(ArticleCommentCount::getCommentCount)
                                            .orElse(0L);
    }
}
