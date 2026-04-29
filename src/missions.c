#include "res.h"
#include "compat.h"
#include "dungeon.h"
#include "inventory.h"
#include "missions.h"
#include "party.h"
#include "primitives.h"
#include "script.h"
#include "state.h"
#include "wld.h"
#include "ydarg.h"
#include "subs.h"

#include <ctype.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

/* §17 / A.4 slice A + Tier 0.1 lift: map-trigger dispatcher. The
   historical mission_control() was 16 ad-hoc `if (flag[N]==V &&
   <guard>) { tx(...); ... }` blocks. They decompose into a small
   number of guard shapes plus a linear / menu_branch script body;
   this dispatcher keeps the guard evaluation in C (where arithmetic
   and `state.dx[i]`/`state.dy[i]` lookups live) and hands the UI off
   to play_script.

   The trigger table itself lives in data/world/missions.json5 — see
   res.c::fold_missions for the loader. Adding a new mission is a
   data edit, not a C change.

   One block still isn't expressible in the DSL: the gem-altar at
   flag[13]==1, which branches on a runtime inventory count (>=5 gems
   of ids 39..41). It lives inline below as the one residual block
   pending a DSL widening. */

#define MAX_BINDINGS   4
#define BIND_ZONE_SIZE 48

/* C-function dispatch for `action: 'c_fn'` triggers. Each entry maps
   a slug authored in data/world/missions.json5 to a C entry point
   the dispatcher calls in lieu of play_script. The list is short by
   design — every entry is a deferred DSL lift; the long-term
   trajectory is to drain this table to zero. fold_missions calls
   mission_resolve_c_fn() at world-load and stores the resolved
   function pointer on the trigger record, so the runtime path stays
   a single indirect call. */
typedef struct {
    const char *slug;
    mission_c_fn fn;
} mission_c_fn_entry;

static const mission_c_fn_entry mission_c_fn_table[] = {
    {"ydarg", ydarg},
};

mission_c_fn mission_resolve_c_fn(const char *slug) {
    if (!slug) {
        return NULL;
    }
    for (size_t i = 0;
         i < sizeof mission_c_fn_table / sizeof *mission_c_fn_table;
         i++) {
        if (strcmp(mission_c_fn_table[i].slug, slug) == 0) {
            return mission_c_fn_table[i].fn;
        }
    }
    return NULL;
}

static int guard_matches(const mission_trigger *t) {
    int32_t x = pa_x();
    int32_t y = pa_y();
    switch (t->kind) {
        case MG_POS:
            return x == t->a && y == t->b;
        case MG_DUNGEON_IDX:
            return x == wld_location_x(t->a) && y == wld_location_y(t->a);
        case MG_TERRAIN:
            return wld_tile_at(x, y) == (char)t->a;
        case MG_CHANCE:
            return ryl_chance((int)t->a);
        case MG_TILE_AT_POS: {
            char path[64];
            snprintf(path, sizeof path, "%s.x", t->pos_path);
            int32_t want_x = res_int_path(path);
            snprintf(path, sizeof path, "%s.y", t->pos_path);
            int32_t want_y = res_int_path(path);
            if (x != want_x || y != want_y) {
                return 0;
            }
            const char *cls = res_terrain_class(wld_tile_at(x, y));
            return cls && t->tile_class && strcmp(cls, t->tile_class) == 0;
        }
        case MG_SPECIAL_EQ: {
            char path[64];
            snprintf(path, sizeof path, "%s.x", t->pos_path);
            int32_t want_x = res_int_path(path);
            snprintf(path, sizeof path, "%s.y", t->pos_path);
            int32_t want_y = res_int_path(path);
            if (x != want_x || y != want_y) {
                return 0;
            }
            const char *cls = res_terrain_class(wld_tile_at(x, y));
            if (!cls || !t->tile_class || strcmp(cls, t->tile_class) != 0) {
                return 0;
            }
            int ps = inv_special_id_for_tag(t->special_tag);
            return ps != PS_NONE && pa_special(ps) == t->a;
        }
    }
    return 0;
}

static void resolve_bindings(const mission_trigger *t, ryl_bind *out,
                             char *scratch) {
    for (size_t i = 0; i < t->nbinds; i++) {
        const mission_binding *b = &t->binds[i];
        char *zone = scratch + i * BIND_ZONE_SIZE;
        out[i].name = b->name;
        switch (b->kind) {
            case MB_NPC_NAME:
                out[i].value = res_npc_name(res_npc_role(b->npc_role));
                break;
            case MB_PLAYER_NAME:
                out[i].value = pa_name(b->arg);
                break;
            case MB_DX_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", wld_location_x(b->arg));
                out[i].value = zone;
                break;
            case MB_DY_STR:
                snprintf(zone, BIND_ZONE_SIZE, "%d", wld_location_y(b->arg));
                out[i].value = zone;
                break;
        }
    }
    out[t->nbinds] = (ryl_bind){0};
}

static void apply_post(enum mission_post_op op) {
    switch (op) {
        case POST_NONE:
            break;
        case POST_SET_JAZZYFOOT:
            party_award_special(PS_JAZZYFOOT);
            break;
    }
}

/* Returns 1 when a trigger fired and asked the caller to short-circuit
   (returns_on_skip), 0 when a trigger fired fall-through, -1 when no
   trigger matched. Filters on `where` so step-poll callers don't fire
   E-key triggers and vice versa. */
static int dispatch_triggers(enum mission_where where) {
    int n = res_missions_count();
    for (int i = 0; i < n; i++) {
        const mission_trigger *t = res_mission_at(i);
        if (!t || t->where != where) {
            continue;
        }
        if (t->flag_tag && !pa_flag_is(t->flag_tag, t->when_state)) {
            continue;
        }
        if (!guard_matches(t)) {
            continue;
        }
        ryl_bind binds[MAX_BINDINGS + 1];
        char scratch[MAX_BINDINGS * BIND_ZONE_SIZE];
        const ryl_bind *pass = NULL;
        if (t->nbinds > 0) {
            resolve_bindings(t, binds, scratch);
            pass = binds;
        }
        if (t->action == MA_C_FN) {
            t->fn();
        } else {
            play_script(t->script, pass);
        }
        apply_post(t->post);
        return t->returns_on_skip ? 1 : 0;
    }
    return -1;
}

uint8_t mission_control(void) {
    int32_t gems;
    char c;

    int handled = dispatch_triggers(MW_STEP);
    if (handled == 1) {
        return 1;
    }
    if (handled == 0) {
        /* A trigger fired but asked to fall through. The original
           blocks are all keyed on disjoint flag+position guards so
           no two fire simultaneously; nothing remains to try below
           except the gem-altar residual. */
    }

    /* Block 5: temple of Thun gem altar. Inventory-count check
       (>=5 gems of ids 39..41) isn't expressible in the DSL —
       single remaining inline block. Text records live under
       missions.thun_temple in data/world/texts/missions.json5;
       gem_prompt bundles the (Y)es/(L)eave menu inline. */
    if (pa_flag_is("bracelets.thun", "map_bought") && pa_x() == wld_location_x(2) && pa_y() == wld_location_y(2)) {
        clw();
        tx0(res_text("missions.thun_temple.temple_arrival"));
        ryl_pause();
        clw();
        tx0(res_text("missions.thun_temple.altar_inscription"));
        ryl_pause();
        clw();
        tx0(res_text("missions.thun_temple.gem_prompt"));
        c = key();
        c = toupper(c);
        if (c == 'L') {
            clw();
            return 1;
        }
        gems = inv_count_in_range(39, 41);
        if (gems < 5) {
            clw();
            tx0(res_text("missions.thun_temple.no_gems"));
            ryl_pause();
            clw();
            return 1;
        } else {
            clw();
            tx0(res_text("missions.thun_temple.altar_opens"));
            ryl_pause();
            clw();
            dungeon(18, "thun_temple");
            clr_map_window();
            update_map();
        }
    }

    return 0;
}

uint8_t mission_try_at_enter(void) {
    int handled = dispatch_triggers(MW_ENTER_KEY);
    return handled >= 0 ? 1 : 0;
}
