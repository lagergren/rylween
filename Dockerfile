# Self-contained compiled-bundle runtime build of Rylween.
#
# Builder produces the Gradle install-distribution; runtime stage carries
# only the JRE plus the install dir.
#
# Usage:
#   docker build -t rylween .
#   docker run --rm -it rylween "Player Name"

# ---------------------------------------------------------------------
# Stage 1: builder — compile Kotlin and assemble the install dir.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /src

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY build-logic build-logic
RUN ./gradlew --no-daemon --version

COPY . .
RUN ./gradlew --no-daemon emitExternalCompiledBundle :compiled-runtime:installCompiledRuntime --console=plain

# ---------------------------------------------------------------------
# Stage 2: runtime — JRE plus the install distribution.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app
COPY --from=builder /src/build/compiled/rylween-bundle.json /app/rylween-bundle.json
COPY --from=builder /src/compiled-runtime/build/install/rylween-compiled-runtime /app/rylween-compiled-runtime

ENV PATH="/app/rylween-compiled-runtime/bin:${PATH}"
ENTRYPOINT ["/app/rylween-compiled-runtime/bin/rylween-compiled", "--bundle", "/app/rylween-bundle.json"]
CMD ["--screen", "title"]
