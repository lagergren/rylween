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
