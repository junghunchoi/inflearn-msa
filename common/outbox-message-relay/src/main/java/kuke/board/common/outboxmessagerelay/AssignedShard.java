package kuke.board.common.outboxmessagerelay;

import lombok.Getter;

import java.util.List;
import java.util.stream.LongStream;

/**
 * 애플리케이션에 할당된 샤드 정보를 관리하는 클래스
 * 분산 시스템에서 여러 애플리케이션에 샤드를 균등하게 분배하는 역할을 함
 */
@Getter
public class AssignedShard {
    /**
     * 현재 애플리케이션에 할당된 샤드 ID 목록
     */
    private List<Long> shards;

    /**
     * 애플리케이션 ID, 전체 애플리케이션 ID 목록, 총 샤드 수를 기반으로
     * 현재 애플리케이션에 할당될 샤드를 계산하여 AssignedShard 객체를 생성
     *
     * @param appId 현재 애플리케이션의 ID
     * @param appIds 시스템 내 모든 애플리케이션 ID 목록
     * @param shardCount 총 샤드 수
     * @return 할당된 샤드 정보가 담긴 AssignedShard 객체
     */
    public static AssignedShard of(String appId, List<String> appIds, long shardCount) {
        AssignedShard assignedShard = new AssignedShard();
        assignedShard.shards = assign(appId, appIds, shardCount);
        return assignedShard;
    }

    /**
     * 애플리케이션에 샤드를 할당하는 알고리즘을 구현
     * 전체 샤드를 애플리케이션 수로 균등하게 분배하는 방식 사용
     *
     * @param appId 현재 애플리케이션의 ID
     * @param appIds 시스템 내 모든 애플리케이션 ID 목록
     * @param shardCount 총 샤드 수
     * @return 현재 애플리케이션에 할당된 샤드 ID 목록
     */
    private static List<Long> assign(String appId, List<String> appIds, long shardCount) {
        // 현재 애플리케이션의 인덱스 찾기
        int appIndex = findAppIndex(appId, appIds);
        // 애플리케이션이 목록에 없으면 빈 리스트 반환
        if (appIndex == -1) {
            return List.of();
        }

        // 시작 샤드와 끝 샤드 계산 (균등 분배 방식)
        long start = appIndex * shardCount / appIds.size();
        long end = (appIndex + 1) * shardCount / appIds.size() - 1;

        // 시작부터 끝까지의 모든 샤드 ID를 리스트로 반환
        return LongStream.rangeClosed(start, end).boxed().toList();
    }

    /**
     * 주어진 애플리케이션 ID의 인덱스를 애플리케이션 ID 목록에서 찾음
     *
     * @param appId 찾을 애플리케이션 ID
     * @param appIds 전체 애플리케이션 ID 목록
     * @return 찾은 인덱스 (없으면 -1)
     */
    private static int findAppIndex(String appId, List<String> appIds) {
        for (int i = 0; i < appIds.size(); i++) {
            if(appIds.get(i).equals(appId)) {
                return i;
            }
        }
        return -1;
    }
}