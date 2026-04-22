/* MAIN entry + shared globals. Terminal I/O lives in primitives.c. */

#include "io.h"
#include "res.h"
#include "ryl.h"
#include "driv.h"

struct ryl_state state;

char *_version_string = "\0 $VER: Adventurers of Rylween v3 - By Marcus Lagergren " __DATE__;

int main(int argc, char **argv) {
    int32_t i;

    /* --check-world: parse data/world.json5 (plus every $import-reachable
       shard) through the same loader the game uses at startup and exit.
       die() inside res_load_world already terminates with a "res: path:
       line: msg" message and exit(1) on any structural error, so a clean
       return here means the entire tree parsed and folded successfully.
       Deliberately skips io_init_tty / ryl_random_init — neither is
       needed for validation and io_init_tty misbehaves under non-TTY
       stdin (CI, editor tasks, etc.). */
    if (argc >= 2 && strcmp(argv[1], "--check-world") == 0) {
        res_load_world("data/world.json5");
        fprintf(stderr, "world: ok\n");
        return 0;
    }

    if (argc < 2) {
        fprintf(stderr, "usage: %s \"Player Name\"\n", argv[0]);
        fprintf(stderr, "       %s --check-world\n", argv[0]);
        return 1;
    }

    io_init_tty();
    ryl_random_init();
    res_load_world("data/world.json5");

    state.global_name[0] = 0;
    strncpy(state.global_name, argv[1], sizeof state.global_name - 1);
    for (i = 2; i < argc; i++) {
        size_t used = strlen(state.global_name);
        if (used + 1 >= sizeof state.global_name) {
            break;
        }
        state.global_name[used] = ' ';
        state.global_name[used + 1] = 0;
        strncat(state.global_name, argv[i], sizeof state.global_name - strlen(state.global_name) - 1);
    }

    init();
    return 0;
}
