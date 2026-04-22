CC        ?= cc
CFLAGS    ?= -std=c99 -O0 -g -Wall -Wextra -Werror=format-security -Isrc
CPPFLAGS  :=
LDFLAGS   :=

# ASan: set ASAN_FLAGS (tuned per §12.2 Commit C) when building the
# sanitizer variant. Kept separate so the default binary stays replay-
# identical to the stock build.
ASAN_FLAGS := -fsanitize=address -fno-omit-frame-pointer -O1 -g

SRC     := src/battle.c src/compat.c src/driv.c src/dungeon.c src/eval.c \
           src/gfx.c src/interact.c src/io_tty.c src/json5.c src/missions.c \
           src/primitives.c src/res.c src/ryl.c src/speak.c \
           src/subs.c src/towne.c src/ydarg.c
OBJ     := $(SRC:.c=.o)

BIN       := rylween
BIN_ASAN  := rylween-asan

.PHONY: all build test check replay check-world asan testasan format run clean help

all: $(BIN)
build: $(BIN)

$(BIN): $(OBJ)
	$(CC) $(CFLAGS) $(LDFLAGS) $^ -o $@

# ASan binary: separate target so .o files don't clash with the stock
# build. Compiled straight from sources (no shared intermediate .o).
$(BIN_ASAN): $(SRC)
	$(CC) $(CFLAGS) $(ASAN_FLAGS) $(LDFLAGS) $(SRC) -o $@

asan: $(BIN_ASAN)

# Gate task: build + replay + world parse. One canonical command
# contributors and CI both run (§13.3).
test: $(BIN)
	tools/replay.sh --all
	tools/check_world.sh
check: test

replay: $(BIN)
	tools/replay.sh --all

check-world: $(BIN)
	tools/check_world.sh

# ASan gate: same fixtures as `make test`, but run under the sanitized
# binary with no byte-identity check — only AddressSanitizer /
# LeakSanitizer markers in the captured output count as failures.
# Linux LSan catches leaks; macOS Apple-clang ships without LSan, so
# use this as a scorecard on macOS and the authoritative gate in CI.
testasan: $(BIN_ASAN)
	tools/replay_asan.sh

# Dry-run clang-format over every .c/.h. Fails if formatting drifts.
# Matches the CI step queued in §12.1.
format:
	clang-format --dry-run --Werror src/*.c src/*.h

# make run ARGS="Player Name"
run: $(BIN)
	./$(BIN) $(ARGS)

clean:
	rm -f $(OBJ) $(BIN) $(BIN_ASAN)

help:
	@echo 'Targets:'
	@echo '  make build        — build $(BIN) (default)'
	@echo '  make test         — build + tools/replay.sh --all + tools/check_world.sh'
	@echo '  make check        — alias for test'
	@echo '  make replay       — just the replay suite'
	@echo '  make check-world  — just data/world.json5 parse check'
	@echo '  make asan         — build $(BIN_ASAN) with AddressSanitizer'
	@echo '  make testasan     — ASan gate: replay fixtures under $(BIN_ASAN), fail on ASan/LSan markers'
	@echo '  make format       — clang-format dry-run over src/'
	@echo '  make run ARGS="Player Name" — build + run'
	@echo '  make clean        — remove build artifacts'
