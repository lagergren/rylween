#include "inventory.h"

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
