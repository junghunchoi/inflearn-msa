package kuke.board.articleread.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class ViewClientTest {
    @Autowired
    ViewClient viewClient;

    @Test
    void readCacheableTest() throws InterruptedException {
        viewClient.count(1L); // 로그 출력
        viewClient.count(1L); // 로그 미출력
        viewClient.count(1L); // 로그 미출력

        TimeUnit.SECONDS.sleep(3);
        viewClient.count(1L); // 로그 출력
    }

    /**
     * 캐시 기능의 멀티스레드 환경에서의 동작을 테스트하는 메소드.
     *
     * 이 테스트는 다음 시나리오를 검증합니다:
     * 1. 5개의 스레드로 구성된 스레드 풀을 생성
     * 2. 초기 캐시를 설정하기 위해 viewClient.count(1L) 호출
     * 3. 5회 반복하여 다음 작업 수행:
     *    a. CountDownLatch를 사용해 5개 스레드가 동시에 viewClient.count(1L) 메소드 호출
     *    b. 모든 스레드 작업이 완료될 때까지 대기
     *    c. 2초 대기 (캐시 만료 시간으로 추정됨)
     *    d. "=== cache expired ===" 메시지 출력
     *
     * 주요 테스트 목적:
     * - 캐시 적용된 메소드가 다중 스레드 환경에서 올바르게 동작하는지 확인
     * - 캐시 만료 후 재로딩 동작 검증
     * - 캐시 적용 시 동시성 이슈 발생 여부 확인
     *
     * @throws InterruptedException 스레드 대기 중 인터럽트 발생 시
     */
    @Test
    void readCacheableMultiThreadTest() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);

        viewClient.count(1L); // init cache

        for(int i=0;i <5; i++) {
            CountDownLatch latch = new CountDownLatch(5);
            for(int j=0;j<5;j++) {
                executorService.submit(() -> {
                    viewClient.count(1L);
                    latch.countDown();
                });
            }
            latch.await();
            TimeUnit.SECONDS.sleep(2);
            System.out.println("=== cache expired ===");
        }
    }
}