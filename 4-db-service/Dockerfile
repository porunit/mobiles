# syntax=docker/dockerfile:1
FROM gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/src
# Warm the dependency cache first so source-only changes rebuild fast.
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon dependencies --quiet || true
COPY --chown=gradle:gradle src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
# OpenTelemetry Java agent — auto-instruments JDBC/JPA/AMQP/HTTP (pinned).
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.8.0/opentelemetry-javaagent.jar /app/otel-agent.jar
COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 10001 appuser && chown -R appuser /app
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-jar", "/app/app.jar"]
