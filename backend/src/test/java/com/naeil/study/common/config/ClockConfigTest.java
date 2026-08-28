package com.naeil.study.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 시간대 고정 검증.
 *
 * <p>이 서비스는 시각을 {@code LocalDateTime}(시간대 없음)으로 저장한다. 사용자가 입력한
 * 시험 시각은 한국 시각인데 서버가 UTC 로 현재 시각을 잡으면 남은 시간이 9시간 부풀려진다.
 * 예외가 나지 않고 조용히 틀린 계획을 만들므로, 회귀를 테스트로 막는다.
 */
@DisplayName("ClockConfig - 시간대 고정")
class ClockConfigTest {

    @Nested
    @SpringBootTest
    @DisplayName("기본값")
    class Default {

        @Autowired
        private Clock clock;

        @Test
        @DisplayName("설정이 없으면 Asia/Seoul 을 쓴다")
        void defaultsToSeoul() {
            assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        }

        @Test
        @DisplayName("OS 기본 시간대에 기대지 않는다")
        void doesNotUseSystemDefaultZone() {
            // Clock.systemDefaultZone() 이었다면 이 단언이 환경에 따라 흔들린다.
            assertThat(clock.getZone()).isNotEqualTo(ZoneId.of("UTC"));
        }

        /*
         * JVM 기본 시간대(TimeZone.setDefault)는 여기서 단언하지 않는다.
         * 그 값은 JVM 전역이고, 테스트는 여러 스프링 컨텍스트가 한 JVM 을 공유한다.
         * app.timezone 을 다르게 준 컨텍스트가 먼저 뜨면 결과가 뒤집혀
         * 실행 순서에 따라 통과 여부가 갈리는 테스트가 된다.
         *
         * 정확성에 필요한 것은 Clock 빈의 시간대다. 계산은 전부 그 빈을 거친다.
         * JVM 기본값은 로그 타임스탬프처럼 우리가 직접 부르지 않는 코드를 위한 보조 장치이며,
         * 컨테이너에서는 TZ 환경변수로도 함께 맞춘다.
         */
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "app.timezone=UTC")
    @DisplayName("설정으로 바꿀 수 있다")
    class Configured {

        @Autowired
        private Clock clock;

        @Test
        @DisplayName("app.timezone 값을 따른다")
        void followsProperty() {
            assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
        }
    }
}
