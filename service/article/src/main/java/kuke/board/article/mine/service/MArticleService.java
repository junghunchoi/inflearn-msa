package kuke.board.article.mine.service;

import jakarta.transaction.Transactional;
import kuke.board.article.entity.Article;
import kuke.board.article.entity.BoardArticleCount;
import kuke.board.article.mine.repository.MArticleRepository;
import kuke.board.article.repository.BoardArticleCountRepository;
import kuke.board.article.service.PageLimitCalculator;
import kuke.board.article.service.request.ArticleCreateRequest;
import kuke.board.article.service.response.ArticlePageResponse;
import kuke.board.article.service.response.ArticleResponse;
import kuke.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MArticleService {
    private final Snowflake snowflake = new Snowflake();
    private final MArticleRepository articleRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;

    @Transactional
    public ArticleResponse create(ArticleCreateRequest articleCreateRequest) {
        Article article = articleRepository.save(
                Article.create(snowflake.nextId(), articleCreateRequest.getTitle(), articleCreateRequest.getContent(), articleCreateRequest.getBoardId(), articleCreateRequest.getWriterId())
        );

        int result = boardArticleCountRepository.increase(articleCreateRequest.getBoardId());
        if(result == 0){
            boardArticleCountRepository.save(
                    BoardArticleCount.init(articleCreateRequest.getBoardId(), 1L)
            );
        }

        return ArticleResponse.from(article);
    }

    public ArticlePageResponse readAll(Long boardId, Long page, Long pageSize) {
        return ArticlePageResponse.of(
                articleRepository.findAll(boardId, (page - 1) * pageSize, pageSize).stream()
                                 .map(ArticleResponse::from)
                                 .toList(),
                articleRepository.count(
                        boardId,
                        PageLimitCalculator.calculatePageLimit(page, pageSize, 10L)
                )
        );
    }
}
