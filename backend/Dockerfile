# ── 빌드 ────────────────────────────────────────────────────────────────────
# JDK 21. build.gradle 의 toolchain 과 맞춘다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 래퍼와 의존성 정의를 먼저 복사한다. 소스만 바뀌면 이 레이어는 캐시가 살아 있어
# 매번 의존성을 다시 받지 않는다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src

# gradle.properties 의 file.encoding=MS949 는 Windows 개발 환경 전용이다.
# 리눅스에서는 빌드 로그의 한글이 깨지므로 명령행에서 UTF-8 로 덮어쓴다.
# 테스트는 CI 에서 따로 돌린다. 이미지 빌드는 산출물만 만든다.
RUN ./gradlew --no-daemon \
      -Dorg.gradle.jvmargs="-Dfile.encoding=UTF-8 -Xmx1g" \
      bootJar -x test

# ── 실행 ────────────────────────────────────────────────────────────────────
# JRE 만 담는다. 빌드 도구와 소스는 최종 이미지에 남기지 않는다.
FROM eclipse-temurin:21-jre
WORKDIR /app

# root 로 돌리지 않는다.
RUN groupadd --system app && useradd --system --gid app --home /app app

# 시간대. 애플리케이션은 app.timezone 으로도 고정하지만, JVM 밖(로그 타임스탬프 등)까지
# 맞추려면 컨테이너 시간대도 함께 맞춰야 한다.
ENV TZ=Asia/Seoul
ENV APP_TIMEZONE=Asia/Seoul

# 로그와 파일 이름의 한글이 깨지지 않게 한다.
ENV LANG=C.UTF-8
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

# 컨테이너에 준 메모리에 맞춰 힙을 잡는다. 고정 -Xmx 보다 안전하다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENV SPRING_PROFILES_ACTIVE=prod

# 업로드 파일이 담기는 곳. 컨테이너를 다시 만들면 사라지므로
# 운영에서는 볼륨을 붙이거나 S3 구현으로 바꾼다. docs/deployment 참고.
RUN mkdir -p /app/uploads && chown -R app:app /app
ENV STORAGE_ROOT_PATH=/app/uploads
VOLUME ["/app/uploads"]

COPY --from=build --chown=app:app /workspace/build/libs/*-SNAPSHOT.jar app.jar

USER app
EXPOSE 8080

# 로드밸런서와 별개로 컨테이너 자체의 상태도 확인한다.
# DB 가 죽으면 actuator 가 503 을 준다.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
