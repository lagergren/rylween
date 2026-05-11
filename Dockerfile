# Self-contained Kotlin runtime build of Rylween.
#
# The Docker image runs the current :runtime Kotlin launcher. Older ANSI,
# native, Rust, and probe targets are not mature playable products and are not
# wired into this image.
#
# Usage:
#   docker build -t rylween .
#   docker run --rm -it -v rylween-data:/data rylween

# ---------------------------------------------------------------------
# Stage 1: builder — compile Kotlin and assemble the runtime install dir.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /src

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY build-logic build-logic
RUN ./gradlew --no-daemon --version

COPY . .
# Produces two runtime artifacts, and fails the image build if either the
# compiled bundle or installed runtime cannot be built:
#   /src/build/compiled/rylween-bundle.json
#     The compiled game data bundle emitted from the Kotlin DSL.
#   /src/runtime/build/install/rylween/
#     A Gradle install image for the Kotlin runtime. Its bin/rylween file is a
#     generated JVM start script whose main class is rylween.runtime.RylweenRuntimeMain;
#     the Kotlin runtime jar and dependencies live beside it under lib/.
RUN ./gradlew --no-daemon :tools:emitExternalCompiledBundle :runtime:installRuntime --console=plain

# ---------------------------------------------------------------------
# Stage 2: runtime — JRE plus the Kotlin runtime install distribution.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

ARG RYLWEEN_DATA_DIR=/data
ENV RYLWEEN_DATA_DIR="${RYLWEEN_DATA_DIR}"
ENV RYLWEEN_SAVE_DIR="${RYLWEEN_DATA_DIR}/saves"
ENV RYLWEEN_LOG_DIR="${RYLWEEN_DATA_DIR}/logs"

WORKDIR /app
# Copy the compiled data bundle consumed by the runtime at startup.
COPY --from=builder /src/build/compiled/rylween-bundle.json /app/rylween-bundle.json
# Copy the installed Kotlin runtime distribution. /app/rylween/bin/rylween is
# the generated launcher script, not a native binary or the old ANSI/Rust path.
COPY --from=builder /src/runtime/build/install/rylween /app/rylween

ENV PATH="/app/rylween/bin:${PATH}"
VOLUME ["/data"]
ENTRYPOINT ["sh", "-c", "mkdir -p \"$RYLWEEN_SAVE_DIR\" \"$RYLWEEN_LOG_DIR\" && exec /app/rylween/bin/rylween --bundle /app/rylween-bundle.json --save \"$RYLWEEN_SAVE_DIR/runtime.rsave\" --log \"$RYLWEEN_LOG_DIR/runtime.log\" \"$@\"", "rylween"]
CMD ["--title"]
