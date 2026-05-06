# Self-contained Kotlin ANSI build of Rylween.
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
RUN ./gradlew --no-daemon installKotlinAnsi --console=plain

# ---------------------------------------------------------------------
# Stage 2: runtime — JRE plus the install distribution.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app
COPY --from=builder /src/build/install/rylween-kotlin /app/rylween-kotlin

ENV PATH="/app/rylween-kotlin/bin:${PATH}"
ENTRYPOINT ["/app/rylween-kotlin/bin/rylween-app"]
CMD ["Player"]