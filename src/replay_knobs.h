#ifndef REPLAY_KNOBS_H__
#define REPLAY_KNOBS_H__

#include <stdint.h>

/* Replay-mode env knobs. Each helper reads its env var, validates,
   one-shot consumes (unsetenv) and returns. Production never sets
   these — fixtures use them to pin state that's otherwise reachable
   only through long, brittle key sequences. Centralized here so the
   inventory of test-only state is greppable in one place rather than
   spread across driv.c / subs.c / battle.c.

   Two flavours:
     - rk_*-prefixed value getters: read once, return parsed value
       (or the supplied default if the var is unset / malformed).
     - rk_apply_* mutators: apply the knob's effect to a target
       (currently a party index) in-place.

   Always-on infra knobs (RYL_FAKE_EPOCH / RYL_FAKE_SEED / RYL_NO_DELAY
   / RYL_SNAPSHOT) are NOT routed through here — they're consumed by
   the lowest-level primitives (ryl.c / io_tty.c / snapshot.c) on
   every call and never one-shot. This file owns only the one-shot
   gameplay overrides. */

int32_t rk_starting_gold(int32_t defv);
const char *rk_force_encounter(void);
int32_t rk_force_encounter_noc(int32_t defv);
void rk_apply_party_lv(int a);
void rk_apply_dead_slots(int a);
void rk_apply_pos(int a);
void rk_apply_ydarg_grants(int a);

/* Stat-roll override (createparty). RYL_STAT_ROLL_MIN /
   RYL_STAT_ROLL_MAX let `make sample-save` (or any test/demo
   harness) push the per-stat ryl_random(min, max) range above
   the world.json5 default. NOT one-shot — createparty calls
   ryl_random multiple times per character (and may re-roll on
   user request), so the env stays set for the whole flow.
   Falls back to defv on missing / malformed value. */
int32_t rk_stat_roll_min(int32_t defv);
int32_t rk_stat_roll_max(int32_t defv);

/* §23.J knob — when set, ydarg() room init fills every non-stairs
   slot with case 4 (no-op room) instead of ryl_random(1, type_max).
   Without this the confrontation fixture's keystroke stream is at
   the mercy of the room-content RNG: a single refactor that perturbs
   the seed re-flips monster/pitfall/treasure rooms and the whole
   ~100-keystroke input has to be re-authored. Returns 1 if the knob
   is set (and consumes it), 0 otherwise. */
int rk_ydarg_easy_rooms(void);

/* §23.J knobs — combat-readiness for the confrontation fixture.
   `rk_apply_party_wp` reads RYL_FORCE_PARTY_WP=<weapon_slug> and
   sets every member's weapon slot to the resolved item id. Slugs
   come from world.json5 items.weapons.<slug> via res_item_id;
   unknown slugs leave wp untouched. `rk_apply_party_hp_full` reads
   RYL_FORCE_PARTY_HP_FULL=1 and heals every alive member's hp up
   to h (matching the natural mid-game state expcheck would have
   left them in had they fought their way to lv 28 organically;
   rk_apply_party_lv intentionally leaves hp at the lv-1 roll so
   pre-existing fixtures' snapshots stay byte-identical). */
void rk_apply_party_wp(int a);
void rk_apply_party_hp_full(int a);

#endif
