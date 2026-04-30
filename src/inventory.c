#include "inventory.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "driv.h" /* pronoun */
#include "log.h"
#include "primitives.h" /* tx, clw, ryl_pause */
#include "res.h"

int inv_find_empty(void) {
    int smax = res_inventory_slot_max();
    for (int i = 1; i <= smax; i++) {
        if (state.p[state.cpl].inventory[i] == 0) {
            return i;
        }
    }
    return 0;
}

int inv_find_item(int id) {
    int smax = res_inventory_slot_max();
    for (int i = 1; i <= smax; i++) {
        if (state.p[state.cpl].inventory[i] == id) {
            return i;
        }
    }
    return 0;
}

int inv_find_in_range(int lo, int hi) {
    int smax = res_inventory_slot_max();
    for (int i = 1; i <= smax; i++) {
        int x = state.p[state.cpl].inventory[i];
        if (x >= lo && x <= hi) {
            return i;
        }
    }
    return 0;
}

int inv_count_in_range(int lo, int hi) {
    int smax = res_inventory_slot_max();
    int count = 0;
    for (int i = 1; i <= smax; i++) {
        int x = state.p[state.cpl].inventory[i];
        if (x >= lo && x <= hi) {
            count++;
        }
    }
    return count;
}

int inv_add(int id) {
    int slot = inv_find_empty();
    if (slot) {
        state.p[state.cpl].inventory[slot] = id;
    }
    return slot;
}

/* id -> PS_* map, built on first item_is_special call from the
   `special:` tags in data/world/items/. The PS_* enum is C-side
   (matches the flag fields on struct party) but the tag string and
   the backing ids live in JSON5 — no symbol list baked into C. An
   unknown tag string is a hard error; so is a stale map that
   doesn't cover every PS_* value.

   §20 T5: special_tab is the single source of truth that ties the
   `special:` tag in items shards to the PS_* enum AND the named
   struct-party flag field. Adding a new special is now: append a
   row here, add the matching field to struct party (state.h),
   add the PS_* enum value to inventory.h, and tag the item record.
   tag_to_which and party_award_special drain through this table —
   no per-special hardcoded branches in either function. */
static const struct {
    const char *tag;
    int which;
    size_t offset; /* byte offset into struct party */
} special_tab[] = {
    {"fireshield", PS_FIRESHIELD, offsetof(struct party, fireshield)},
    {"bracelets", PS_BRACELETS, offsetof(struct party, bracelets)},
    {"mastercrown", PS_MASTERCROWN, offsetof(struct party, mastercrown)},
    {"jazzyfoot", PS_JAZZYFOOT, offsetof(struct party, jazzyfoot)},
    {"silverkey", PS_SILVERKEY, offsetof(struct party, silverkey)},
};

static int tag_to_which(const char *tag) {
    for (size_t i = 0; i < sizeof special_tab / sizeof *special_tab; i++) {
        if (!strcmp(special_tab[i].tag, tag)) {
            return special_tab[i].which;
        }
    }
    return PS_NONE;
}

#define SPECIAL_MAP_MAX 32
static struct {
    int id;
    int which;
} special_map[SPECIAL_MAP_MAX];
static int special_map_n;
static int special_map_ready;

static int special_map_add(int id, const char *tag, void *user) {
    (void)user;
    int which = tag_to_which(tag);
    if (which == PS_NONE) {
        LOG_E("inventory", "unknown special tag '%s' (id %d)", tag, id);
        exit(1);
    }
    if (special_map_n >= SPECIAL_MAP_MAX) {
        LOG_E("inventory", "special_map capacity %d exceeded", SPECIAL_MAP_MAX);
        exit(1);
    }
    special_map[special_map_n].id = id;
    special_map[special_map_n].which = which;
    special_map_n++;
    return 0;
}

void inv_init(void) {
    if (special_map_ready) {
        return;
    }
    res_items_foreach_special(special_map_add, NULL);
    unsigned seen = 0;
    for (int i = 0; i < special_map_n; i++) {
        seen |= 1u << special_map[i].which;
    }
    for (int w = PS_NONE + 1; w < PS_LAST; w++) {
        if (!(seen & (1u << w))) {
            LOG_E("inventory",
                  "no item carries the special tag for "
                  "PS_* value %d (check data/world/items/specials.json5)",
                  w);
            exit(1);
        }
    }
    special_map_ready = 1;
}

int inv_special_id_for_tag(const char *tag) {
    if (!tag) {
        return PS_NONE;
    }
    return tag_to_which(tag);
}

int item_is_special(int id, int *which) {
    inv_init();
    for (int i = 0; i < special_map_n; i++) {
        if (special_map[i].id == id) {
            if (which) {
                *which = special_map[i].which;
            }
            return 1;
        }
    }
    return 0;
}

static int kind_equals(int id, const char *want) {
    if (id <= 0) {
        return 0;
    }
    const char *k = res_item_kind(id);
    return k && !strcmp(k, want);
}

int item_is_weapon(int id) {
    return kind_equals(id, "weapons");
}
int item_is_armor(int id) {
    return kind_equals(id, "armor");
}
int item_is_gear(int id) {
    return kind_equals(id, "gear");
}
int item_is_potion(int id) {
    return kind_equals(id, "potions");
}
int item_is_treasure(int id) {
    return kind_equals(id, "treasures");
}
int item_is_magic_item(int id) {
    return kind_equals(id, "magic_items");
}

void party_award_special(int which) {
    struct party *p = &state.p[state.cpl];
    for (size_t i = 0; i < sizeof special_tab / sizeof *special_tab; i++) {
        if (special_tab[i].which == which) {
            *(unsigned char *)((char *)p + special_tab[i].offset) = 1;
            return;
        }
    }
}

/* §20 S4 dispatch table — branches on res_item(id)->effect_kind. The
   per-kind narration is hardcoded here for now; a follow-up may push
   it into texts (one text-id per kind would let new kinds be data-
   only). Each kind owns its own ryl_pause + clw beats so the caller
   sees a single "use this potion on this member" entry point. */
// TODO(dsl-port): lift the per-kind narration below into texts. Target
//   shape in full.dsl.kts: each potion's `effect { say(...); ... }` body
//   already names the text ids (subs.apply_potion.heal / .poison /
//   .restore_mana / .no_effect). When that lands, replace the strcmp
//   ladder with a tx0(res_text(it->effect_text)) lookup driven by a new
//   `effect_text` field on ryl_item.
void apply_potion(int item_id, pm_t pm) {
    const ryl_item *it = res_item(item_id);
    if (!it || !it->effect_kind) {
        return;
    }
    if (!strcmp(it->effect_kind, "heal")) {
        tx("The healing brew warms\r\n");
        tx("%s inside...\r\n\r\n", pronoun(pm.m, 2));
        ryl_pause();
        clw();
        pm_add_hit_points(pm, it->effect_amount + pm_level(pm));
        if (pm_hit_points(pm) > pm_health(pm)) {
            pm_set_hit_points(pm, pm_health(pm));
        }
        return;
    }
    if (!strcmp(it->effect_kind, "poison")) {
        tx("Horrible pain sears %s\r\n", pronoun(pm.m, 3));
        tx("entrails as the poison\r\n");
        tx("begins to work.\r\n\r\n");
        ryl_pause();
        clw();
        pm_add_hit_points(pm, -(it->effect_amount + pm_level(pm)));
        pm_hit_points_floor(pm, 1);
        return;
    }
    if (!strcmp(it->effect_kind, "restore_mana")) {
        if (pm_is_spellcaster(pm)) {
            tx("It tingles slightly in\r\n");
            tx("%s body, as all %s\r\n", pronoun(pm.m, 3), pronoun(pm.m, 3));
            tx("spell points return.\r\n\r\n");
            ryl_pause();
            clw();
            pm_set_magic(pm, pm_wisdom(pm));
        } else {
            tx("The potion has no special\r\n");
            tx("effect on this character.\r\n\r\n");
            ryl_pause();
            clw();
        }
        return;
    }
}
