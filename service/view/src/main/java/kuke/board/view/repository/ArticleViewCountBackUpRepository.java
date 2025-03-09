package kuke.board.view.repository;

import kuke.board.view.entity.ArticleViewCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleViewCountBackUpRepository extends JpaRepository<ArticleViewCount, Long> {

    // redis에선 조회수 집계가 빨리 올라가는데 rdb에선 그 속도를 맞출 수 없으니 아래와 같이 조건절을 통해
    // 방어하는 로직을 추가하여 정상적인 데이터를 저장할 수 있게 한다.
    @Query(
            value = "update article_view_count set view_count = :viewCount " +
                    "where article_id = :articleId and view_count < :viewCount",
            nativeQuery = true
    )
    @Modifying
    int updateViewCount(
            @Param("articleId") Long articleId,
            @Param("viewCount") Long viewCount
    );
}
