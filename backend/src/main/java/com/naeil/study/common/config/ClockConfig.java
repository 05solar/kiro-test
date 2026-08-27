package com.naeil.study.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 의존성을 빈으로 분리한다.
 *
 * <p>세션 만료(lastAccessedAt / expiresAt) 계산이 서비스 로직의 핵심이므로,
 * 테스트에서 고정된 시각을 주입할 수 있도록 {@link Clock}을 직접 주입받아 사용한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
