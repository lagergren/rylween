# Self-contained Kotlin web runtime build of Rylween.
#
# The Docker image runs the current :runtime-web Kotlin launcher. Older native,
# Rust, and probe targets are not mature playable products and are not wired
# into this image.
#
# Usage:
#   docker build -t rylween .
#   docker run --rm -p 8080:8080 -v rylween-data:/data rylween
#   ./run-server.sh --build --name Strong

# ---------------------------------------------------------------------
# Stage 1: builder - compile Kotlin and assemble the web runtime install dir.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /src

COPY gradle/wrapper gradle/wrapper
# Warm the Gradle wrapper distribution before copying project files that change
# often. This layer should only be invalidated when the wrapper itself changes.
COPY gradlew ./
RUN ./gradlew --no-daemon --version

COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY settings.gradle.kts build.gradle.kts ./
COPY build-logic build-logic
COPY . .
# Produces two runtime artifacts, and fails the image build if either the
# compiled bundle or installed web runtime cannot be built:
#   /src/build/compiled/rylween-bundle.json
#     The compiled game data bundle emitted from the Kotlin DSL.
#   /src/runtime-web/build/install/rylween-web/
#     A Gradle install image for the Kotlin web runtime. Its bin/rylween-web
#     file is a generated JVM start script whose main class serves the browser
#     ANSI POC over HTTP/WebSocket; the Kotlin jars and dependencies live beside
#     it under lib/.
RUN ./gradlew --no-daemon :tools:emitExternalCompiledBundle :runtime-web:installRuntimeWeb --console=plain

# ---------------------------------------------------------------------
# Stage 2: runtime - JRE plus the Kotlin web runtime install distribution.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

ARG RYLWEEN_DATA_DIR=/data
ENV RYLWEEN_DATA_DIR="${RYLWEEN_DATA_DIR}"
ENV RYLWEEN_BUNDLE=/app/rylween-bundle.json
ENV RYLWEEN_HOST=0.0.0.0
ENV RYLWEEN_PORT=8080
ENV RYLWEEN_SAVE_FILE=/data/saves/world.rsave

WORKDIR /app
# Copy the compiled data bundle consumed by the web runtime at startup.
COPY --from=builder /src/build/compiled/rylween-bundle.json /app/rylween-bundle.json
# Copy the installed Kotlin web runtime distribution. /app/rylween-web/bin/rylween-web
# is the generated launcher script, not a native binary or the old ANSI/Rust path.
COPY --from=builder /src/runtime-web/build/install/rylween-web /app/rylween-web

ENV PATH="/app/rylween-web/bin:${PATH}"
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "mkdir -p \"$RYLWEEN_DATA_DIR/saves\" \"$RYLWEEN_DATA_DIR/logs\" && exec /app/rylween-web/bin/rylween-web --bundle \"$RYLWEEN_BUNDLE\" --data \"$RYLWEEN_DATA_DIR\" --host \"$RYLWEEN_HOST\" --port \"$RYLWEEN_PORT\" \"$@\"", "rylween-web"]
