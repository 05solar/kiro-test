package com.naeil.study.common.config;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 의존성을 빈으로 분리한다.
 *
 * <p>세션 만료, 시험까지 남은 시간, 실제 학습시간 계산이 전부 이 {@link Clock} 을 거친다.
 * 테스트에서 고정된 시각을 주입할 수 있도록 서비스는 항상 이 빈을 주입받아 쓴다.
 *
 * <p><b>시간대를 명시적으로 고정한다.</b> {@code Clock.systemDefaultZone()} 을 쓰면
 * 서버의 OS 설정에 따라 결과가 달라진다. 개발 PC는 KST지만 AWS의 EC2/ECS는 기본이 UTC다.
 *
 * <p>이 서비스는 시각을 {@code LocalDateTime}(시간대 없음)으로 저장한다. 사용자가 입력한
 * "시험 8/29 10시"는 한국 시각이므로, 서버가 UTC로 현재 시각을 잡으면 남은 시간이
 * 9시간 부풀려진다.
 *
 * <pre>
 * 실제 남은 시간   24시간
 * UTC 기준 계산    33시간   ← 실행 불가능한 계획이 만들어진다
 * </pre>
 *
 * 예외가 나지 않고 조용히 틀린 답을 내므로 배포 후에 알아채기 어렵다.
 * 그래서 OS 설정에 기대지 않고 {@code app.timezone} 으로 못박는다.
 *
 * <p>JVM 기본 시간대도 함께 맞춘다. Hibernate와 Jackson 등 우리가 직접 부르지 않는
 * 코드가 시각을 다룰 때 같은 기준을 보게 하기 위해서다.
 */
@Configuration
public class ClockConfig {

    private static final Logger log = LoggerFactory.getLogger(ClockConfig.class);

    private final ZoneId zoneId;

    public ClockConfig(@Value("${app.timezone:Asia/Seoul}") String timezone) {
        this.zoneId = ZoneId.of(timezone);
    }

    /**
     * JVM 기본 시간대를 애플리케이션 시간대에 맞춘다.
     *
     * <p>도커 이미지에 {@code TZ} 를 넣는 것만으로는 부족하다. 실행 환경이 바뀌어도
     * 애플리케이션이 스스로 같은 기준을 쓰도록 여기서 한 번 더 고정한다.
     */
    @PostConstruct
    void applyDefaultTimeZone() {
        // clock() 을 부르지 않는다. 같은 @Configuration 의 @Bean 메서드를 초기화 도중에
        // 호출하면 CGLIB 프록시가 아직 생성 중인 빈을 다시 요청해 순환 참조로 실패한다.
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        log.info("application timezone fixed: {} (now={})", zoneId, LocalDateTime.now(zoneId));
    }

    @Bean
    public Clock clock() {
        return Clock.system(zoneId);
    }
}
