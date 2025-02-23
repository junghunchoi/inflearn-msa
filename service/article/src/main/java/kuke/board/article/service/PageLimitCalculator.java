package kuke.board.article.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 유틸성 클래스는 final로 선언하고, 생성자를 private으로 선언하여 인스턴스 생성을 막는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageLimitCalculator {

    public static Long calculatePageLimit(Long page, Long pageSize, Long movablePageCount) {
        return (((page - 1) / movablePageCount) + 1) * pageSize * movablePageCount + 1;
    }
}
