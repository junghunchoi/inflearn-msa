package kuke.board.common.event.payload;

import kuke.board.common.event.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDeletedEventPayload implements EventPayload {
    private Long articleId;
    private String title;
    private String content;
    private long boardId;
    private Long writerId;
    private LocalDateTime localDateTime;
    private LocalDateTime modifiedAt;
    private Long boardArticleCount;

}
