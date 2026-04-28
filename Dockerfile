# Multi-stage build: builder → tester → runtime.
#
# Goal: contributors without a native toolchain (or on a non-macOS
# platform) can build, run the replay suite, run the world-check, and
# run the ASan gate in a single `docker build` invocation. See §12.1
# in port-plan-incomplete.md.
#
# Usage:
#   docker build -t rylween .                  # builds + tests; runtime image tagged rylween
#   docker build --target tester -t rylween-test .   # build through the gate stage only
#   docker run --rm -it rylween "Player Name"        # play

# Pinned LLVM major. Bump when a new stable cuts; keep in lock-step
# with the GitHub workflow so the Linux gate, the Linux format leg,
# and macOS brew (which tracks current stable) all run the same major.
ARG LLVM_VERSION=22

# ---------------------------------------------------------------------
# Stage 1: builder — compile both the stock and ASan binaries.
# ---------------------------------------------------------------------
# debian:stable's apt only ships clang-19; pull current LLVM from the
# upstream apt repo instead so the image runs the same major as macOS
# brew. `llvm.sh` is the official one-shot installer.
FROM debian:stable-slim AS builder

ARG LLVM_VERSION

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        gnupg \
        lsb-release \
        make \
        wget \
    && wget -qO /tmp/llvm.sh https://apt.llvm.org/llvm.sh \
    && chmod +x /tmp/llvm.sh \
    && /tmp/llvm.sh ${LLVM_VERSION} \
    && rm /tmp/llvm.sh \
    && ln -sf /usr/bin/clang-${LLVM_VERSION} /usr/local/bin/clang \
    && ln -sf /usr/bin/clang++-${LLVM_VERSION} /usr/local/bin/clang++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

# `make clean` first so a stray host build artefact in COPY can't poison
# the image. Stock binary plus ASan binary in the same stage; the
# tester stage runs both gates against them.
RUN make clean && make build && make asan

# ---------------------------------------------------------------------
# Stage 2: tester — run the gates so `docker build` fails on regression.
# ---------------------------------------------------------------------
# Same base + same LLVM as builder so libasan / libubsan symbols
# resolve, plus clang-format ${LLVM_VERSION} for the format dry-run.
FROM debian:stable-slim AS tester

ARG LLVM_VERSION

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        gnupg \
        lsb-release \
        libc6-dev \
        make \
        wget \
    && wget -qO /tmp/llvm.sh https://apt.llvm.org/llvm.sh \
    && chmod +x /tmp/llvm.sh \
    && /tmp/llvm.sh ${LLVM_VERSION} \
    && apt-get install -y --no-install-recommends clang-format-${LLVM_VERSION} \
    && rm /tmp/llvm.sh \
    && ln -sf /usr/bin/clang-${LLVM_VERSION} /usr/local/bin/clang \
    && ln -sf /usr/bin/clang-format-${LLVM_VERSION} /usr/local/bin/clang-format \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY --from=builder /src /src

# Three-gate sequence mirrors `make test` + `make testasan` + `make
# format`. Run as separate RUN steps so a failure pinpoints which gate
# regressed in the image build log.
RUN make test
RUN make testasan
RUN make format

# ---------------------------------------------------------------------
# Stage 3: runtime — slim image with the stock binary + game data.
# ---------------------------------------------------------------------
# `tester` is referenced as a build dep (via the COPY below depending on
# the build-graph traversal) so its RUN steps execute before this stage
# is finalised. `--target tester` exists for CI legs that want only the
# gate without producing a runtime image.
FROM debian:stable-slim AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libc6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=tester /src/rylween /app/rylween
COPY --from=tester /src/data /app/data
COPY --from=tester /src/tools /app/tools

ENTRYPOINT ["/app/rylween"]
CMD ["Player"]
