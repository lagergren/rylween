#include "inventory.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "res.h"
#include "ryl.h" /* state */

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
   doesn't cover every PS_* value. */
static int tag_to_which(const char *tag) {
    if (!strcmp(tag, "fireshield")) {
        return PS_FIRESHIELD;
    }
    if (!strcmp(tag, "bracelets")) {
        return PS_BRACELETS;
    }
    if (!strcmp(tag, "mastercrown")) {
        return PS_MASTERCROWN;
    }
    if (!strcmp(tag, "jazzyfoot")) {
        return PS_JAZZYFOOT;
    }
    if (!strcmp(tag, "silverkey")) {
        return PS_SILVERKEY;
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
        fprintf(stderr, "inventory: unknown special tag '%s' (id %d)\n",
                tag, id);
        exit(1);
    }
    if (special_map_n >= SPECIAL_MAP_MAX) {
        fprintf(stderr, "inventory: special_map capacity %d exceeded\n",
                SPECIAL_MAP_MAX);
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
            fprintf(stderr,
                    "inventory: no item carries the special tag for "
                    "PS_* value %d (check data/world/items/specials.json5)\n",
                    w);
            exit(1);
        }
    }
    special_map_ready = 1;
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
int item_is_armour(int id) {
    return kind_equals(id, "armour");
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
    switch (which) {
        case PS_FIRESHIELD:
            p->fireshield = 1;
            break;
        case PS_BRACELETS:
            p->bracelets = 1;
            break;
        case PS_MASTERCROWN:
            p->mastercrown = 1;
            break;
        case PS_JAZZYFOOT:
            p->jazzyfoot = 1;
            break;
        case PS_SILVERKEY:
            p->silverkey = 1;
            break;
        default:
            break;
    }
}
