# Packages the prebuilt Spring Boot jar (CI runs `./mvnw -pl start -am package`
# first — tests already gated the commit, so the image build never re-runs them).
# Local usage: ./mvnw -B -DskipTests package -pl start -am && docker build -t callme .
#
# Layered extraction (jarmode=tools): dependencies / loader / snapshot-deps /
# application land in separate image layers, so a typical deploy only ships the
# few-KB application layer instead of the full ~100MB fat jar.

FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /app
COPY start/target/start-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
# Non-root (OWASP A05) — the app needs no filesystem writes beyond tmp.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=extract /app/extracted/dependencies/ ./
COPY --from=extract /app/extracted/spring-boot-loader/ ./
COPY --from=extract /app/extracted/snapshot-dependencies/ ./
COPY --from=extract /app/extracted/application/ ./
USER app
EXPOSE 8080

# Size the heap from the container's cgroup limit (set in docker-compose), not the
# droplet's total RAM — the JVM otherwise assumes it owns the whole machine.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Container-level liveness — the orchestrating side (compose/CD script) keys off
# this. /actuator/health is reachable in-network only; Caddy 404s it publicly.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
