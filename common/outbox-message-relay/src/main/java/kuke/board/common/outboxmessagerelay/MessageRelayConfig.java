package kuke.board.common.outboxmessagerelay;

import java.util.concurrent.ScheduledExecutorService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Outbox 패턴을 사용한 메시지 릴레이 시스템 구성 클래스.
 * 데이터베이스에 저장된 Outbox 테이블의 이벤트를 Kafka 메시지로 변환하여 전송하는 기능을 제공합니다.
 * 비동기 처리와 스케줄링을 활용하여 안정적인 메시지 전달을 보장합니다.
 */
@EnableAsync // 비동기 처리 활성화 - Outbox에서 메시지를 발행할 때 비동기로 처리
@Configuration
@ComponentScan("kuke.board.common.outboxmessagerelay") // 메시지 릴레이 관련 컴포넌트 스캔 범위 지정
@EnableScheduling // 전달되지 않은 메시지를 주기적으로 전달하기 위한 스케줄링 기능 활성화
public class MessageRelayConfig {
    /**
     * Kafka 서버 주소 설정 (application.properties 또는 application.yml에서 주입)
     */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Kafka 메시지 생성자 템플릿 빈 설정.
     * 문자열 키와 문자열 값을 가진 메시지를 Kafka로 전송하는 데 사용됩니다.
     *
     * @return Kafka 메시지 전송을 위한 KafkaTemplate 인스턴스
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        // Kafka 생성자 설정 구성
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers); // Kafka 서버 주소
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class); // 키 직렬화 방식
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class); // 값 직렬화 방식
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 복제본이 메시지를 받았을 때만 성공으로 간주

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configProps));
    }

    /**
     * 메시지 릴레이 이벤트 발행을 위한 비동기 실행기 빈 설정.
     * 다수의 이벤트를 동시에 처리하기 위한 스레드 풀을 구성합니다.
     *
     * @return 이벤트 처리를 위한 ThreadPoolTaskExecutor 인스턴스
     */
    @Bean
    public Executor messageRelayPublisherEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20); // 기본 스레드 수 - 20개
        executor.setMaxPoolSize(50); // 최대 스레드 수 - 50개
        executor.setQueueCapacity(100); // 대기 큐 용량 - 100개
        executor.setThreadNamePrefix("mr-pub-event-"); // 스레드 이름 접두사

        return executor;
    }

    /**
     * 미처리된 메시지를 주기적으로 처리하기 위한 스케줄 실행기 빈 설정.
     * 단일 스레드로 실행되어 주기적으로 전송되지 않은 메시지를 검사하고 전송합니다.
     *
     * @return 스케줄 작업 실행을 위한 ScheduledExecutorService 인스턴스
     */
    @Bean
    public ScheduledExecutorService messageRelayPublishPendingEventExecutor() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        return executor;
    }
}