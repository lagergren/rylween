/* MAIN entry + shared globals. Terminal I/O lives in primitives.c. */

#include "io.h"
#include "res.h"
#include "ryl.h"

struct ryl_state state;

char *_version_string = "\0 $VER: Adventurers of Rylween v3 - By Marcus Lagergren " __DATE__;

int main(int argc, char **argv) {
    int32_t i;

    if (argc < 2) {
        fprintf(stderr, "usage: %s \"Player Name\"\n", argv[0]);
        return 1;
    }

    io_init_tty();
    res_load("data/resources.jsonl");

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
