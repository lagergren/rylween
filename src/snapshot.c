#include "snapshot.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "state.h"

static char snapshot_path[512];

static void emit_party(FILE *f, int idx) {
    const struct party *p = &state.p[idx];
    fprintf(f, "[party %d]\n", idx);
    fprintf(f, "player=%s\n", p->player);
    fprintf(f, "party_name=%s\n", p->party_name);
    fprintf(f, "pl=%u\n", p->pl);
    fprintf(f, "x=%d\n", p->x);
    fprintf(f, "y=%d\n", p->y);
    fprintf(f, "gold=%d\n", p->gold);
    fprintf(f, "torches=%u\n", p->torches);
    fprintf(f, "food=%d\n", p->food);
    fprintf(f, "time_left=%d\n", p->time_left);
    fprintf(f, "hours_rested=%d\n", p->hours_rested);
    fprintf(f, "kills=%u\n", p->kills);
    fprintf(f, "specials=%u,%u,%u,%u,%u\n", p->fireshield, p->bracelets,
            p->mastercrown, p->jazzyfoot, p->silverkey);

    fputs("flag=", f);
    for (size_t i = 0; i < sizeof p->flag; i++) {
        fprintf(f, "%02x", p->flag[i]);
    }
    fputc('\n', f);

    fputs("inventory=", f);
    for (int i = 0; i <= INVENTORY_SLOT_MAX; i++) {
        fprintf(f, "%s%d", i ? "," : "", p->inventory[i]);
    }
    fputc('\n', f);

    for (int m = 1; m <= PARTY_MEMBER_MAX; m++) {
        fprintf(f,
                "member[%d] name=%s sex=%u cl=%d ra=%d lv=%d dead=%d "
                "h=%d hp=%d ws=%d wi=%d ma=%d ch=%d ar=%d wp=%d exp=%u\n",
                m, p->name[m], p->sex[m], p->cl[m], p->ra[m], p->lv[m],
                p->dead[m], p->h[m], p->hp[m], p->ws[m], p->wi[m], p->ma[m],
                p->ch[m], p->ar[m], p->wp[m], p->exp[m]);
    }
}

/* Emit the overworld map as 42 fixed-width hex rows so a byte-for-byte
   diff localises any terrain change to a single y. The array holds raw
   terrain glyphs plus stray \n / \0 from fgets() in driv.c; hex avoids
   surprises from non-printable bytes. */
static void emit_map(FILE *f) {
    for (size_t y = 0; y < sizeof state.map / sizeof *state.map; y++) {
        fprintf(f, "map[%02zu]=", y);
        for (size_t x = 0; x < sizeof state.map[y]; x++) {
            fprintf(f, "%02x", (unsigned char)state.map[y][x]);
        }
        fputc('\n', f);
    }
}

static void snapshot_write(void) {
    if (!snapshot_path[0]) {
        return;
    }
    FILE *f = fopen(snapshot_path, "w");
    if (!f) {
        fprintf(stderr, "snapshot: cannot open '%s' for write\n", snapshot_path);
        return;
    }
    fputs("# rylween state snapshot v1\n", f);
    fprintf(f, "cpl=%d\n", state.cpl);
    fprintf(f, "twn=%d\n", state.twn);
    fprintf(f, "hour_of_day=%d\n", state.hour_of_day);
    fprintf(f, "banished=%d\n", state.banished);
    fprintf(f, "last_played_day=%d\n", state.last_played_day);
    if (state.cpl >= 1 && state.cpl <= 25) {
        emit_party(f, state.cpl);
    }
    emit_map(f);
    fclose(f);
}

void snapshot_init(void) {
    const char *env = getenv("RYL_SNAPSHOT");
    if (!env || !env[0]) {
        return;
    }
    size_t n = strlen(env);
    if (n >= sizeof snapshot_path) {
        fprintf(stderr, "snapshot: RYL_SNAPSHOT path too long (max %zu)\n",
                sizeof snapshot_path - 1);
        exit(1);
    }
    memcpy(snapshot_path, env, n + 1);
    if (atexit(snapshot_write) != 0) {
        fprintf(stderr, "snapshot: atexit registration failed\n");
        exit(1);
    }
}
