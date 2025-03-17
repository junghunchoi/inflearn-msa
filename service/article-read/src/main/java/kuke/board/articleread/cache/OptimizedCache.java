package kuke.board.articleread.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import kuke.board.common.dataserializer.DataSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@ToString
public class OptimizedCache {
    private String data;
    private LocalDateTime expiredAt;

    public static OptimizedCache of(Object data, Duration ttl) {
        OptimizedCache optimizedCache = new OptimizedCache();
        optimizedCache.data = DataSerializer.serialize(data);
        optimizedCache.expiredAt = LocalDateTime.now().plus(ttl); // logical ttl 이기 때문에 설정한 시간보다 조금 더 여유있게 시간을 설정한다.
        return optimizedCache;
    }

    @JsonIgnore // 필드로 들어갈 수 있기때문에 어노테이션을 단다.
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }

    public <T> T parseData(Class<T> dataType) {
        return DataSerializer.deserialize(data, dataType);
    }
}
