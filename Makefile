CC      ?= cc
CFLAGS  ?= -std=c99 -O0 -g -Wall -Wextra -Werror=format-security -Isrc

SRC     := src/compat.c src/driv.c src/dungeon.c src/gfx.c src/interact.c \
           src/io_tty.c src/missions.c src/person.c src/primitives.c \
           src/res.c src/ryl.c src/speak.c src/strid.c src/subs.c src/towne.c \
           src/ydarg.c
OBJ     := $(SRC:.c=.o)

rylween: $(OBJ)
	$(CC) $(CFLAGS) $^ -o $@

clean:
	rm -f $(OBJ) rylween

.PHONY: clean
