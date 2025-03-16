package kuke.board.articleread.service;

import kuke.board.articleread.client.ArticleClient;
import kuke.board.articleread.client.CommentClient;
import kuke.board.articleread.client.LikeClient;
import kuke.board.articleread.client.ViewClient;
import kuke.board.articleread.repository.ArticleIdListRepository;
import kuke.board.articleread.repository.ArticleQueryModel;
import kuke.board.articleread.repository.ArticleQueryModelRepository;
import kuke.board.articleread.repository.BoardArticleCountRepository;
import kuke.board.articleread.service.event.handler.EventHandler;
import kuke.board.articleread.service.response.ArticleReadPageResponse;
import kuke.board.articleread.service.response.ArticleReadResponse;
import kuke.board.common.event.Event;
import kuke.board.common.event.EventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 아티클 조회 관련 기능을 제공하는 서비스 클래스.
 * 아티클, 댓글, 좋아요, 조회수 등의 정보를 조합하여 아티클 조회 응답을 생성합니다.
 * 캐싱 전략을 통해 성능을 최적화하고 있으며, 캐시에 데이터가 없을 경우 원본 소스에서 데이터를 가져옵니다.
 */
public class ArticleReadService {
    /**
     * 여러 클라이언트와 레포지토리를 의존성으로 가지며 아티클 조회 기능을 구현합니다.
     */
    private final ArticleClient articleClient;
    private final CommentClient commentClient;
    private final LikeClient likeClient;
    private final ViewClient viewClient;
    private final ArticleIdListRepository articleIdListRepository;
    private final ArticleQueryModelRepository articleQueryModelRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;
    private final List<EventHandler> eventHandlers;

    /**
     * 이벤트를 처리하는 메소드.
     * 지원 가능한 이벤트 핸들러가 있을 경우 해당 핸들러로 이벤트를 처리합니다.
     *
     * @param event 처리할 이벤트 객체
     */
    public void handleEvent(Event<EventPayload> event) {
        for (EventHandler eventHandler : eventHandlers) {
            if (eventHandler.supports(event)) {
                eventHandler.handle(event);
            }
        }
    }

    /**
     * 특정 아티클 ID에 해당하는 아티클 정보를 조회합니다.
     * 캐시된 데이터가 있으면 캐시에서, 없으면 원본 소스에서 데이터를 가져옵니다.
     *
     * @param articleId 조회할 아티클 ID
     * @return 아티클 조회 응답 객체
     * @throws NoSuchElementException 아티클이 존재하지 않을 경우
     */
    public ArticleReadResponse read(Long articleId) {
        ArticleQueryModel articleQueryModel = articleQueryModelRepository.read(articleId)
            .or(() -> fetch(articleId))
            .orElseThrow();

        return ArticleReadResponse.from(
            articleQueryModel,
            viewClient.count(articleId)
        );
    }

    /**
     * 원본 소스에서 아티클 정보를 가져와 캐시합니다.
     * 아티클, 댓글 수, 좋아요 수 정보를 조합하여 ArticleQueryModel을 생성합니다.
     *
     * @param articleId 조회할 아티클 ID
     * @return 생성된 ArticleQueryModel이 담긴 Optional 객체
     */
    private Optional<ArticleQueryModel> fetch(Long articleId) {
        Optional<ArticleQueryModel> articleQueryModelOptional = articleClient.read(articleId)
            .map(article -> ArticleQueryModel.create(
                article,
                commentClient.count(articleId),
                likeClient.count(articleId)
            ));
        articleQueryModelOptional
            .ifPresent(articleQueryModel -> articleQueryModelRepository.create(articleQueryModel, Duration.ofDays(1)));
        log.info("[ArticleReadService.fetch] fetch data. articleId={}, isPresent={}", articleId, articleQueryModelOptional.isPresent());
        return articleQueryModelOptional;
    }

    /**
     * 특정 게시판의 아티클 목록을 페이징하여 조회합니다.
     *
     * @param boardId 게시판 ID
     * @param page 페이지 번호 (1부터 시작)
     * @param pageSize 페이지당 아티클 수
     * @return 아티클 목록과 전체 개수가 포함된 페이지 응답 객체
     */
    public ArticleReadPageResponse readAll(Long boardId, Long page, Long pageSize) {
        return ArticleReadPageResponse.of(
            readAll(
                readAllArticleIds(boardId, page, pageSize)
            ),
            count(boardId)
        );
    }

    /**
     * 아티클 ID 목록에 해당하는 아티클 정보를 조회합니다.
     * 캐시된 데이터가 있으면 캐시에서, 없으면 원본 소스에서 데이터를 가져옵니다.
     *
     * @param articleIds 조회할 아티클 ID 목록
     * @return 아티클 조회 응답 객체 목록
     */
    private List<ArticleReadResponse> readAll(List<Long> articleIds) {
        Map<Long, ArticleQueryModel> articleQueryModelMap = articleQueryModelRepository.readAll(articleIds);
        return articleIds.stream()
            .map(articleId -> articleQueryModelMap.containsKey(articleId) ?
                articleQueryModelMap.get(articleId) :
                fetch(articleId).orElse(null))
            .filter(Objects::nonNull)
            .map(articleQueryModel ->
                ArticleReadResponse.from(
                    articleQueryModel,
                    viewClient.count(articleQueryModel.getArticleId())
                ))
            .toList();
    }

    /**
     * 특정 게시판의 아티클 ID 목록을 페이징하여 조회합니다.
     * 캐시된 데이터가 있고 충분한 경우 캐시에서, 그렇지 않으면 원본 소스에서 데이터를 가져옵니다.
     *
     * @param boardId 게시판 ID
     * @param page 페이지 번호
     * @param pageSize 페이지당 아티클 수
     * @return 아티클 ID 목록
     */
    private List<Long> readAllArticleIds(Long boardId, Long page, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAll(boardId, (page - 1) * pageSize, pageSize);
        if (pageSize == articleIds.size()) {
            log.info("[ArticleReadService.readAllArticleIds] return redis data.");
            return articleIds;
        }
        log.info("[ArticleReadService.readAllArticleIds] return origin data.");
        return articleClient.readAll(boardId, page, pageSize).getArticles().stream()
            .map(ArticleClient.ArticleResponse::getArticleId)
            .toList();
    }

    /**
     * 특정 게시판의 전체 아티클 수를 조회합니다.
     * 캐시된 데이터가 있으면 캐시에서, 없으면 원본 소스에서 데이터를 가져와 캐시합니다.
     *
     * @param boardId 게시판 ID
     * @return 게시판의 전체 아티클 수
     */
    private long count(Long boardId) {
        Long result = boardArticleCountRepository.read(boardId);
        if (result != null) {
            return result;
        }
        long count = articleClient.count(boardId);
        boardArticleCountRepository.createOrUpdate(boardId, count);
        return count;
    }

    /**
     * 무한 스크롤 방식으로 특정 게시판의 아티클 목록을 조회합니다.
     * 마지막으로 조회한 아티클 ID 이후의 아티클들을 가져옵니다.
     *
     * @param boardId 게시판 ID
     * @param lastArticleId 마지막으로 조회한 아티클 ID
     * @param pageSize 조회할 아티클 수
     * @return 아티클 조회 응답 객체 목록
     */
    public List<ArticleReadResponse> readAllInfiniteScroll(Long boardId, Long lastArticleId, Long pageSize) {
        return readAll(
            readAllInfiniteScrollArticleIds(boardId, lastArticleId, pageSize)
        );
    }

    /**
     * 무한 스크롤 방식으로 특정 게시판의 아티클 ID 목록을 조회합니다.
     * 캐시된 데이터가 있고 충분한 경우 캐시에서, 그렇지 않으면 원본 소스에서 데이터를 가져옵니다.
     *
     * @param boardId 게시판 ID
     * @param lastArticleId 마지막으로 조회한 아티클 ID
     * @param pageSize 조회할 아티클 수
     * @return 아티클 ID 목록
     */
    private List<Long> readAllInfiniteScrollArticleIds(Long boardId, Long lastArticleId, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAllInfiniteScroll(boardId, lastArticleId, pageSize);
        if (pageSize == articleIds.size()) {
            log.info("[ArticleReadService.readAllInfiniteScrollArticleIds] return redis data.");
            return articleIds;
        }
        log.info("[ArticleReadService.readAllInfiniteScrollArticleIds] return origin data.");
        return articleClient.readAllInfiniteScroll(boardId, lastArticleId, pageSize).stream()
            .map(ArticleClient.ArticleResponse::getArticleId)
            .toList();
    }
}
