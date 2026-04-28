#include "snapshot.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "log.h"
#include "party.h"
#include "res.h"
#include "runtime_cfg.h"
#include "state.h"
#include "wld.h"

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

    /* Flags as a sorted tag=state list. Slots at 0 are omitted so an
       all-fresh party emits `flags=` (empty). Deterministic order:
       walk slots 0..N, look up each slot's tag. */
    fputs("flags=", f);
    int first = 1;
    for (size_t slot = 0; slot < sizeof p->flag; slot++) {
        unsigned v = p->flag[slot];
        if (!v) {
            continue;
        }
        const char *id = res_flag_id_for_slot((int32_t)slot);
        const char *vname = id ? res_flag_value(id, (int)v) : NULL;
        fprintf(f, "%s%s=%s", first ? "" : ",",
                id ? id : "?",
                (vname && *vname) ? vname : "?");
        first = 0;
    }
    fputc('\n', f);

    fputs("inventory=", f);
    for (int i = 0; i <= INVENTORY_SLOT_MAX; i++) {
        fprintf(f, "%s%d", i ? "," : "", p->inventory[i]);
    }
    fputc('\n', f);

    for_each_party_member(m) {
        fprintf(f,
                "member[%d] name=%s gender=%u cl=%d ra=%d lv=%d dead=%d "
                "h=%d hp=%d ws=%d wi=%d ma=%d ch=%d ar=%d wp=%d exp=%u\n",
                m, p->name[m], p->gender[m], p->cl[m], p->ra[m], p->lv[m],
                p->dead[m], p->h[m], p->hp[m], p->ws[m], p->wi[m], p->ma[m],
                p->ch[m], p->ar[m], p->wp[m], p->exp[m]);
    }
    /* Journal lines as one-field-per-line so entries stay self-contained
       (commas and quotes are legal in journal prose; a delimited single
       line would be ambiguous). Skipped entirely when empty to keep the
       pre-§19.2 fixture snapshots diff-clean. */
    if (p->journal_n) {
        fprintf(f, "journal_n=%zu\n", p->journal_n);
        for (size_t j = 0; j < p->journal_n; j++) {
            fprintf(f, "journal[%zu]=%s\n", j, p->journal[j]);
        }
    }
}

/* Emit the overworld map as fixed-width hex rows so a byte-for-byte
   diff localises any terrain change to a single y. The array holds raw
   terrain glyphs plus stray \n / \0 from the wld_load_row() reads in
   driv.c; hex avoids surprises from non-printable bytes. */
static void emit_map(FILE *f) {
    for (int y = 0; y < wld_rows(); y++) {
        fprintf(f, "map[%02d]=", y);
        for (int x = 0; x < wld_cols(); x++) {
            fprintf(f, "%02x", (unsigned char)wld_tile_at(x, y));
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
        LOG_W("snapshot", "cannot open '%s' for write", snapshot_path);
        return;
    }
    fputs("# rylween state snapshot v1\n", f);
    fprintf(f, "cpl=%d\n", state.cpl);
    fprintf(f, "twn=%d\n", state.twn);
    fprintf(f, "hour_of_day=%d\n", state.hour_of_day);
    fprintf(f, "banished=%d\n", state.banished);
    fprintf(f, "last_played_day=%d\n", state.last_played_day);
    if (state.cpl >= 1 && state.cpl < PARTY_SLOT_HARD_MAX) {
        emit_party(f, state.cpl);
    }
    emit_map(f);
    fclose(f);
}

void snapshot_init(void) {
    const char *env = rcfg_get("RYL_SNAPSHOT");
    if (!env || !env[0]) {
        return;
    }
    size_t n = strlen(env);
    if (n >= sizeof snapshot_path) {
        LOG_E("snapshot", "RYL_SNAPSHOT path too long (max %zu)",
              sizeof snapshot_path - 1);
        exit(1);
    }
    memcpy(snapshot_path, env, n + 1);
    if (atexit(snapshot_write) != 0) {
        LOG_E("snapshot", "atexit registration failed");
        exit(1);
    }
}
