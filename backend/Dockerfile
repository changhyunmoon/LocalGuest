# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY api-server/build.gradle ./api-server/build.gradle
COPY module-ai/build.gradle ./module-ai/build.gradle
COPY module-ai-integration/build.gradle ./module-ai-integration/build.gradle
COPY module-chat/build.gradle ./module-chat/build.gradle
COPY module-common/build.gradle ./module-common/build.gradle
COPY module-domain/build.gradle ./module-domain/build.gradle
COPY module-gemini/build.gradle ./module-gemini/build.gradle
COPY module-openai/build.gradle ./module-openai/build.gradle

RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :api-server:dependencies || true

COPY api-server/src ./api-server/src
COPY module-ai/src ./module-ai/src
COPY module-ai-integration/src ./module-ai-integration/src
COPY module-chat/src ./module-chat/src
COPY module-common/src ./module-common/src
COPY module-domain/src ./module-domain/src
COPY module-gemini/src ./module-gemini/src
COPY module-openai/src ./module-openai/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :api-server:bootJar -x test

RUN java -Djarmode=tools \
    -jar api-server/build/libs/*.jar \
    extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash spring
USER spring:spring
WORKDIR /app

COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./

ENV SERVER_PORT=8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$SERVER_PORT org.springframework.boot.loader.launch.JarLauncher"]
