package kuke.board.common.event;

import kuke.board.common.event.payload.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@RequiredArgsConstructor
public enum EventType {
    ARTICLE_CREATED(ArticleCreatedEventPayload.class, Topic.ARTICLE_CREATED),
    ARTICLE_UPDATED(ArticleUpdatedEventPayload.class, Topic.ARTICLE_UPDATED),
    ARTICLE_DELETED(ArticleDeletedEventPayload.class, Topic.ARTICLE_DELETED),
    COMMENT_CREATED(CommentCreatedEventPayload.class, Topic.COMMENT_CREATED),
    COMMENT_DELETED(CommentDeletedEventPayload.class, Topic.COMMENT_DELETED),
    ARTICLE_LIKED(ArticleLikedEventPayload.class, Topic.ARTICLE_LIKED),
    ARTICLE_UNLIKED(ArticleUnlikedEventPayload.class, Topic.ARTICLE_UNLIKED),
    ARTICLE_VIEWED(ArticleViewedEventPayload.class, Topic.ARTICLE_VIEWED);

    private final Class<? extends EventPayload> payloadClass;
    private final String topic;

    public static EventType from(String type) {
        try {
            return valueOf(type);
        } catch (Exception e) {
            log.error("Unknown event type: {}", type);
            return null;
        }
    }

    public static class Topic {
        public static final String ARTICLE_CREATED = "article_created";
        public static final String ARTICLE_UPDATED = "article_updated";
        public static final String ARTICLE_DELETED = "article_deleted";
        public static final String COMMENT_CREATED = "comment_created";
        public static final String COMMENT_DELETED = "comment_deleted";
        public static final String ARTICLE_LIKED = "article_liked";
        public static final String ARTICLE_UNLIKED = "article_unliked";
        public static final String ARTICLE_VIEWED = "article_viewed";
    }
}
