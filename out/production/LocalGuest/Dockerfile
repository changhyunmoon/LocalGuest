FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app
ARG JAR_FILE=api-server/build/libs/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]