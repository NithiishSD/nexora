# Production image (10-DEPLOYMENT section 3): built in a JDK stage, run on a JRE only, as an unprivileged user.

# ---------- stage 1: build the jar (JDK) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# copy only the files that define dependencies first, so Docker caches the download layer
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

# now the sources (changes often) and build; tests already ran in CI before this image is built
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# split the fat jar into layers: dependencies change rarely, application code changes often
RUN java -Djarmode=tools -jar build/libs/app.jar extract --layers --destination /extracted

# ---------- stage 2: runtime (JRE only, no compiler, no build tools) ----------
FROM eclipse-temurin:21-jre
# dedicated unprivileged user: the process never runs as root
RUN groupadd --system --gid 10001 nexora && useradd --system --uid 10001 --gid nexora --no-create-home nexora \
    && mkdir -p /var/lib/nexora/documents && chown -R nexora:nexora /var/lib/nexora
# uploaded documents (a volume in production; a new volume takes over this directory's owner)
ENV DOCUMENTS_DIR=/var/lib/nexora/documents
WORKDIR /app

# order = least to most frequently changing, so a code change rebuilds only the last layer
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

USER nexora
EXPOSE 8080

# readiness = the JVM is up AND the database answers (Actuator readiness group)
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# JAVA_OPTS comes from the environment file; exec makes java PID 1 so it receives SIGTERM (graceful shutdown)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
