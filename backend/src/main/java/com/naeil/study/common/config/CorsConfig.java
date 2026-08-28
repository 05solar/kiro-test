package com.naeil.study.common.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트를 다른 도메인에 올릴 때만 필요한 설정.
 *
 * <p>지금은 백엔드가 정적 파일을 함께 서빙하므로 같은 오리진이고 CORS가 필요 없다.
 * 프론트를 S3나 CloudFront 같은 다른 도메인에 올리면 그때부터 모든 API 호출이 막힌다.
 *
 * <p>{@code app.cors.allowed-origins} 가 비어 있으면 <b>아무것도 등록하지 않는다.</b>
 * 기본값을 열어 두면 배포하는 사람이 눈치채지 못한 채 아무 도메인에서나 호출할 수 있게 된다.
 * 세션 코드가 유일한 접근 키인 서비스에서 그건 위험하다.
 *
 * <p>와일드카드({@code *})를 쓰지 않는다. 도메인을 명시적으로 나열한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null || allowedOrigins.isBlank()
                ? List.of()
                : List.of(allowedOrigins.split("\\s*,\\s*"));
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            log.info("cors not configured. api is same-origin only.");
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
        log.info("cors configured for origins: {}", allowedOrigins);
    }
}
