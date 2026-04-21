/* MAIN entry + shared globals. Terminal I/O lives in primitives.c. */

#include "io.h"
#include "ryl.h"

int32_t tid, cpl, antal_spelare, global_x, senaste_dagen, twn;
int32_t hours_pigg;
int32_t banished;
int32_t dx[15], dy[15];
int32_t reg;
int32_t day_at_game_start;
char map[42][82];
char global_name[30];
char last_terrain;

struct party p[26];
struct person npc[41];

char *jobname[] = {"", "Merchant", "Ranger", "Fighter", "Wizard", "Priest",
                   "Thief", "Peasant", "Bard", "Drifter", "Half-Orc", "Half-Elf",
                   "Smith", "Bargirl", "Innkeeper", "Tavernkeeper", "Hermit"};

char *name[] = {"", "Garth", "Nrim'to", "Areth", "Nevaak", "Gorm", "Bernak",
                "Matias", "Tybalt", "Romeo", "Werra", "Arenjun", "Oskar", "Harry",
                "Marraga", "Broth", "Ebond", "Bolph", "Gotharp", "Ron", "Kaal",
                "Daniel", "Leander", "Ithienn", "Azaaza", "Hassan", "Luqas", "Darth",
                "Yog", "Niklas", "Tokawa", "Miyaki", "Dargoth", "Marcus", "Deanna",
                "Jeanne", "Alex", "Galdon", "Kosch", "Ufarsin"};

char *_version_string = "\0 $VER: Adventurers of Rylween v3 - By Marcus Lagergren " __DATE__;

int main(int argc, char **argv) {
    int32_t i;

    if (argc < 2) {
        fprintf(stderr, "usage: %s \"Player Name\"\n", argv[0]);
        return 1;
    }

    io_init_tty();

    global_name[0] = 0;
    strncpy(global_name, argv[1], sizeof global_name - 1);
    for (i = 2; i < argc; i++) {
        size_t used = strlen(global_name);
        if (used + 1 >= sizeof global_name) {
            break;
        }
        global_name[used] = ' ';
        global_name[used + 1] = 0;
        strncat(global_name, argv[i], sizeof global_name - strlen(global_name) - 1);
    }

    init();
    return 0;
}
