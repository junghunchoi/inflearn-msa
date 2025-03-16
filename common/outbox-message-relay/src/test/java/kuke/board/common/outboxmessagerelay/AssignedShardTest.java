package kuke.board.common.outboxmessagerelay;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class AssignedShardTest {

    @Test
    void ofTest() {
        // given
        Long shardCount = 64L;
        List<String> appList = List.of("appId1", "appId2", "appId3");

        //when
        AssignedShard assignedShard1 = AssignedShard.of("appId1", appList, shardCount);
        AssignedShard assignedShard2 = AssignedShard.of("appId2", appList, shardCount);
        AssignedShard assignedShard3 = AssignedShard.of("appId3", appList, shardCount);
        AssignedShard assignedShard4 = AssignedShard.of("invalidAppId", appList, shardCount);

        //then
        List<Long> result = Stream.of(assignedShard1.getShards(), assignedShard2.getShards(), assignedShard3.getShards(), assignedShard4.getShards())
                .flatMap(List::stream)
                .toList();

        assertThat(result).hasSize(shardCount.intValue());

        for (int i = 0; i < 64; i++) {
            log.info("result.get(i) : {}", result.get(i));
            assertThat(result.get(i)).isEqualTo(i);
        }

        assertThat(assignedShard4.getShards()).isEmpty();
    }

}