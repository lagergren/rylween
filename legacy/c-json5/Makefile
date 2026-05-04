CC        ?= cc
# Strict flag set (verified zero-warning across every TU as of the
# §A2 cleanup). -Wshadow / -Wstrict-prototypes / -Wmissing-prototypes
# / -Wpointer-arith / -Wundef / -Wcast-align / -Wnull-dereference all
# catch real bugs in this codebase's idioms; keeping them in the
# default build prevents regression.
CFLAGS    ?= -std=c99 -O0 -g -Wall -Wextra -Werror=format-security \
             -Wshadow -Wstrict-prototypes -Wmissing-prototypes \
             -Wpointer-arith -Wundef -Wcast-align -Wnull-dereference \
             -Isrc
# Emit per-object .d files so header edits (e.g. state.h) trigger
# rebuilds of every dependent TU. Without this an edit to state.h
# only rebuilt the .c file whose own source changed — see the
# §19.2.2 "HP 68/64" phantom. -MMD skips system headers; -MP adds
# phony targets so a deleted header doesn't break the rebuild.
DEPFLAGS  := -MMD -MP
# `-std=c99` puts glibc into strict ISO C mode, which hides POSIX
# extensions the codebase uses everywhere (`strdup`, `usleep`,
# `sigaction`, `getline`, …). `_DEFAULT_SOURCE` is glibc's "give me
# the historical BSD/SVID surface" knob; Apple-libc ignores it (those
# symbols are always exposed there), which is why the gap stayed
# masked until the §12.1 Linux build.
CPPFLAGS  := -D_DEFAULT_SOURCE
LDFLAGS   :=

# ASan: set ASAN_FLAGS (tuned per §12.2 Commit C) when building the
# sanitizer variant. Kept separate so the default binary stays replay-
# identical to the stock build.
ASAN_FLAGS := -fsanitize=address -fno-omit-frame-pointer -O1 -g

SRC     := src/battle.c src/compat.c src/driv.c src/dungeon.c \
           src/interact.c src/inventory.c src/io_tty.c \
           src/json5.c src/log.c src/missions.c src/party.c \
           src/primitives.c src/replay_knobs.c src/res.c \
           src/runtime_cfg.c src/ryl.c src/script.c src/snapshot.c \
           src/speak.c src/subs.c src/towne.c src/wld.c src/ydarg.c
OBJ     := $(SRC:.c=.o)

BIN       := rylween
BIN_ASAN  := rylween-asan

.PHONY: all build test check replay check-world asan testasan format run sample-save clean help

all: $(BIN)
build: $(BIN)

$(BIN): $(OBJ)
	$(CC) $(CFLAGS) $(LDFLAGS) $^ -o $@

# Pattern rule so $(DEPFLAGS) lands on every .o — the implicit rule
# would otherwise ignore it. Keeps flag ordering identical to the
# pre-§19.3 build.
%.o: %.c
	$(CC) $(CFLAGS) $(DEPFLAGS) $(CPPFLAGS) -c -o $@ $<

# ASan binary: separate target so .o files don't clash with the stock
# build. Compiled straight from sources (no shared intermediate .o).
$(BIN_ASAN): $(SRC)
	$(CC) $(CFLAGS) $(ASAN_FLAGS) $(CPPFLAGS) $(LDFLAGS) $(SRC) -o $@

# Include generated header-dep files; `-` silences the first-run miss.
-include $(OBJ:.o=.d)

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

# Generate a fresh save under tmp-sample-save/ with a pre-rolled
# 4-member party (Aragorn=Fighter/Human, Gandalf=Mage/Elf,
# Mira=Priest/Halfling, Legolas=Ranger/Elf). Writes to a tmp dir
# (NOT $HOME) so an existing real save is never clobbered. Pinned RNG
# seed keeps the rolled stats stable across runs; if the seed ever
# stops passing the class-gate min-stat checks for fighter (ws>=50),
# mage (wi>=50), or priest (wi>=50), bump RYL_FAKE_SEED until all
# four members pass on first roll.
SAMPLE_SAVE_DIR := tmp-sample-save
sample-save: $(BIN)
	@rm -rf $(SAMPLE_SAVE_DIR)
	@mkdir -p $(SAMPLE_SAVE_DIR)
	@HOME=$(PWD)/$(SAMPLE_SAVE_DIR) RYL_FAKE_EPOCH=1700000000 \
	    RYL_FAKE_SEED=1 RYL_NO_DELAY=1 \
	    RYL_STAT_ROLL_MIN=65 RYL_STAT_ROLL_MAX=85 \
	    ./$(BIN) Sample < tools/sample-save.in > /dev/null
	@echo 'Sample save generated:'
	@echo '  $(SAMPLE_SAVE_DIR)/.rylween/current-world.json5'
	@echo ''
	@echo 'To play with this party, either:'
	@echo '  (a) HOME=$(PWD)/$(SAMPLE_SAVE_DIR) ./$(BIN) Sample'
	@echo '      — runs from the fixture, leaving your real $$HOME/.rylween alone.'
	@echo '  (b) cp -r $(SAMPLE_SAVE_DIR)/.rylween "$$HOME/.rylween"'
	@echo '      then ./$(BIN) Sample  — installs the save (overwrites $$HOME/.rylween).'

clean:
	rm -f $(OBJ) $(OBJ:.o=.d) $(BIN) $(BIN_ASAN)

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
	@echo '  make sample-save  — drop a fresh 4-member party into $$HOME/.rylween/'
	@echo '  make clean        — remove build artifacts'
