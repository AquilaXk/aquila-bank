FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY config config
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

RUN groupadd --system aquila && useradd --system --gid aquila aquila

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

USER aquila
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
