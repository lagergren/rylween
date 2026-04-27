#include "replay_knobs.h"

#include <stdlib.h>
#include <string.h>

#include "res.h"
#include "state.h"

/* Owns every one-shot, replay-only env knob the engine reads.
   Each helper validates, consumes (unsetenv) so subsequent moves
   resume the natural path, and either returns the parsed value or
   applies the side effect in-place. See replay_knobs.h for the
   division of duty. Production builds never set these vars; the
   helpers are no-ops in that case. */

int32_t rk_starting_gold(int32_t defv) {
    /* Fixtures that exercise a high-cost purchase (silver-key map:
       50000) preload the bank to a value that defv alone can't
       reach. Clamped to >= defv so a malformed value can't under-
       fund a normal run. */
    const char *env = getenv("RYL_STARTING_GOLD");
    if (!env || !*env) {
        return defv;
    }
    char *end;
    long v = strtol(env, &end, 10);
    unsetenv("RYL_STARTING_GOLD");
    if (end == env || v < defv || v > 2000000000L) {
        return defv;
    }
    return (int32_t)v;
}

const char *rk_force_encounter(void) {
    /* Returns a pointer to a static buffer holding the monster id
       string for the next encounter, or NULL if the knob is unset.
       Static storage so the env-var unsetenv that follows the
       getenv read can't invalidate the returned pointer. The id is
       resolved by the caller (subs.c) via res_monster_id; that
       indirection stays at the call site so this helper doesn't
       need to know about combat. */
    static char buf[64];
    const char *env = getenv("RYL_FORCE_ENCOUNTER");
    if (!env || !*env) {
        return NULL;
    }
    strncpy(buf, env, sizeof buf - 1);
    buf[sizeof buf - 1] = 0;
    unsetenv("RYL_FORCE_ENCOUNTER");
    return buf;
}

int32_t rk_force_encounter_noc(int32_t defv) {
    /* Pin the foe count after the regular encounter roll so a
       fixture can target a specific (single, paired, ...) shape
       independent of the monster's n1/n2 range. Caller (battle.c)
       still consumes the natural roll first so the post-encounter
       RNG state matches the unforced path. */
    const char *env = getenv("RYL_FORCE_ENCOUNTER_NOC");
    if (!env || !*env) {
        return defv;
    }
    char *end;
    long v = strtol(env, &end, 10);
    unsetenv("RYL_FORCE_ENCOUNTER_NOC");
    if (end == env || v < 1 || v > 0x7fffffff) {
        return defv;
    }
    return (int32_t)v;
}

void rk_apply_party_lv(int a) {
    /* Fixtures that exercise spells whose cost (base + lv) exceeds
       a fresh lv-1 spellcaster's ma cap (= seed wi, max 70) raise
       every member's lv to <n> and apply the per-class levelup
       deltas (n-1 times) to ws/h/wi/ch — same shape battle.c::
       level_up_check applies on combat XP gain, fired once per
       gained level rather than once per call. Spellcasters' ma is
       reset to the bumped wi cap so the fixture can cast
       immediately. Clamped to [1, 28] (engine level ceiling). */
    const char *env = getenv("RYL_FORCE_PARTY_LV");
    if (!env || !*env) {
        return;
    }
    char *end;
    long lv_target = strtol(env, &end, 10);
    if (end == env || lv_target < 1 || lv_target > 28) {
        return;
    }
    static const char *const pc_class_key[] = {
        "", "fighter", "thief", "mage", "priest", "bard", "ranger",
    };
    for (int i = 1; i <= res_party_member_max(); i++) {
        pm_t pm = pm_of(&state.p[a], i);
        int cl_id = pm_cl(pm);
        const char *cls = (cl_id >= 1 && cl_id <= 6) ? pc_class_key[cl_id] : "";
        for (long n = 1; n < lv_target; n++) {
            pm_add_ws(pm, res_class_levelup(cls, "ws"));
            pm_add_h(pm, res_class_levelup(cls, "h"));
            pm_add_wi(pm, res_class_levelup(cls, "wi"));
            pm_add_ch(pm, res_class_levelup(cls, "ch"));
        }
        state.p[a].lv[i] = (int32_t)lv_target;
        if (pm_is_spellcaster(pm)) {
            pm_set_ma(pm, pm_wi(pm));
        }
    }
    unsetenv("RYL_FORCE_PARTY_LV");
}

void rk_apply_pos(int a) {
    /* Fixtures that exercise an entry-tile flow (castle Ydarg at
       (14,30), the Hut at random map slots) need the party to start
       on the target tile without 20+ wilderness moves on stdin —
       each move triggers a wilderness encounter roll and accumulates
       hours/food/etc. that perturb the post-arrival RNG state.
       Format: RYL_FORCE_POS=X,Y (decimal). Out-of-map values (driv's
       map is 82x42) are silently ignored so a malformed knob can't
       leave the party stranded. */
    const char *env = getenv("RYL_FORCE_POS");
    if (!env || !*env) {
        return;
    }
    char *end;
    long x = strtol(env, &end, 10);
    if (end == env || *end != ',') {
        unsetenv("RYL_FORCE_POS");
        return;
    }
    const char *yp = end + 1;
    long y = strtol(yp, &end, 10);
    unsetenv("RYL_FORCE_POS");
    if (end == yp || x < 0 || x >= 82 || y < 0 || y >= 42) {
        return;
    }
    state.p[a].x = (int32_t)x;
    state.p[a].y = (int32_t)y;
}

void rk_apply_ydarg_grants(int a) {
    /* §23.J: grant the four quest items the stairs G-prompt expects
       so a confrontation-walkthrough fixture can advance through
       all four floors without prerequisite town/cave detours.
       Sets each PS_* flag directly; party_realize already guarantees
       the struct slot exists. Silverkey is granted too so the gate
       at the entrance accepts the party. The knob is one-shot;
       fixtures stack RYL_FORCE_PARTY_LV separately for the level
       bump that gives Baron Ydarg's fight a fighting chance. */
    const char *env = getenv("RYL_FORCE_YDARG_GRANTS");
    if (!env || !*env) {
        return;
    }
    unsetenv("RYL_FORCE_YDARG_GRANTS");
    state.p[a].fireshield  = 1;
    state.p[a].mastercrown = 1;
    state.p[a].jazzyfoot   = 1;
    state.p[a].bracelets   = 1;
    state.p[a].silverkey   = 1;
}

int rk_ydarg_easy_rooms(void) {
    const char *env = getenv("RYL_FORCE_YDARG_EASY_ROOMS");
    if (!env || !*env) {
        return 0;
    }
    /* Don't unsetenv — ydarg() may be re-entered (e.g. via the
       teleport-out / re-enter path) and a fixture that walks all
       four floors expects every entry to honour the flag. Fixtures
       set the var once at boot; production builds never set it. */
    return 1;
}

void rk_apply_party_wp(int a) {
    /* §23.J: set every member's weapon to the slug-named item so a
       confrontation fixture can deal monster-scaled damage without
       a town pre-stop to buy gear. Slugs are world.json5 items.
       weapons.<slug> keys (e.g. "dancing_sword"); res_item_id
       walks every items.<section>.<slug> so the resolver does not
       care which shard the slug lives in. Unknown slugs leave wp
       untouched — fixtures that mistype the slug see their attacks
       deal obdamage(0) damage and the bless surfaces the mistake. */
    const char *env = getenv("RYL_FORCE_PARTY_WP");
    if (!env || !*env) {
        return;
    }
    char buf[64];
    strncpy(buf, env, sizeof buf - 1);
    buf[sizeof buf - 1] = 0;
    unsetenv("RYL_FORCE_PARTY_WP");
    int32_t id = res_item_id(buf);
    if (id < 0) {
        return;
    }
    for (int i = 1; i <= res_party_member_max(); i++) {
        state.p[a].wp[i] = (int32_t)id;
    }
}

void rk_apply_party_hp_full(int a) {
    /* §23.J: heal every alive member's hp up to h. Pairs with
       rk_apply_party_lv, which (deliberately) leaves hp at the
       lv-1 roll so pre-existing fixtures' snapshots stay byte-
       identical. A confrontation-walkthrough fixture that climbs
       to the engine's level ceiling needs hp scaled up alongside
       the h bumps or the first monster round one-shots the party.
       Dead members are skipped — rk_apply_dead_slots may already
       have flagged a slot dead, and an hp = h reset would undo it. */
    const char *env = getenv("RYL_FORCE_PARTY_HP_FULL");
    if (!env || !*env) {
        return;
    }
    unsetenv("RYL_FORCE_PARTY_HP_FULL");
    for (int i = 1; i <= res_party_member_max(); i++) {
        if (state.p[a].dead[i]) {
            continue;
        }
        state.p[a].hp[i] = state.p[a].h[i];
    }
}

void rk_apply_dead_slots(int a) {
    /* Fixtures that exercise the dead-arm of party-target spells
       (priest rebirth) need a member who starts dead without a
       combat to kill them. Comma-separated list of slot indices
       to flag dead — out-of-range entries silently skipped.
       Mirrors the in-combat death state battle.c writes (dead=1
       AND hp=0). */
    const char *env = getenv("RYL_FORCE_DEAD_SLOT");
    if (!env || !*env) {
        return;
    }
    const char *p = env;
    while (*p) {
        char *end;
        long slot = strtol(p, &end, 10);
        if (end != p && slot >= 1 && slot <= res_party_member_max()) {
            state.p[a].dead[slot] = 1;
            state.p[a].hp[slot] = 0;
        }
        p = (*end == ',') ? end + 1 : end;
        if (p == end && *p == 0) {
            break;
        }
    }
    unsetenv("RYL_FORCE_DEAD_SLOT");
}
