#include "party.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "compat.h"    /* stricmp */
#include "inventory.h" /* PS_* */
#include "log.h"
#include "res.h"
#include "ryl.h" /* state */

static int in_range(int m) {
    return m >= 0 && m < res_party_member_max();
}

int pa_is_alive(int m) {
    return in_range(m) && !state.p[state.cpl].dead[m];
}

int pa_is_dead(int m) {
    return in_range(m) && state.p[state.cpl].dead[m];
}

int pa_is_class(int m, int cl) {
    return in_range(m) && state.p[state.cpl].cl[m] == cl;
}

/* §24bis phase 1 — slug-form siblings to pa_is_class. The current-party
   variants of pm_class_sym / pm_race_sym; bound-check m and return ""
   for out-of-range slots so caller string compares can't match by
   accident. */
const char *pa_class_sym(int m) {
    if (!in_range(m)) {
        return "";
    }
    return res_enum_sym("pc_class", state.p[state.cpl].cl[m]);
}

const char *pa_race_sym(int m) {
    if (!in_range(m)) {
        return "";
    }
    /* §24bis phase 3: race storage is the slug pointer; passthrough. */
    const char *s = state.p[state.cpl].ra[m];
    return s ? s : "";
}

int pa_class_is_sym(int m, const char *sym) {
    const char *s = pa_class_sym(m);
    return s && sym && s[0] && strcmp(s, sym) == 0;
}

int pa_race_is_sym(int m, const char *sym) {
    const char *s = pa_race_sym(m);
    return s && sym && s[0] && strcmp(s, sym) == 0;
}

int pa_is_spellcaster(int m) {
    if (!in_range(m)) {
        return 0;
    }
    int c = state.p[state.cpl].cl[m];
    return c == PC_MAGE || c == PC_PRIEST;
}

int pa_first_alive(void) {
    for_each_party_member(i) {
        if (!state.p[state.cpl].dead[i]) {
            return i;
        }
    }
    return -1;
}

int pa_count_class(int cl) {
    int count = 0;
    for_each_party_member(i) {
        if (state.p[state.cpl].cl[i] == cl) {
            count++;
        }
    }
    return count;
}

int pa_count_alive_class(int cl) {
    int count = 0;
    for_each_party_member(i) {
        if (state.p[state.cpl].cl[i] == cl && !state.p[state.cpl].dead[i]) {
            count++;
        }
    }
    return count;
}

int pa_alive_count(void) {
    int count = 0;
    for_each_party_member(i) {
        if (!state.p[state.cpl].dead[i]) {
            count++;
        }
    }
    return count;
}

int32_t pa_x(void) {
    return state.p[state.cpl].x;
}

int32_t pa_y(void) {
    return state.p[state.cpl].y;
}

void pa_set_x(int32_t v) {
    state.p[state.cpl].x = v;
}

void pa_set_y(int32_t v) {
    state.p[state.cpl].y = v;
}

void pa_add_x(int32_t delta) {
    state.p[state.cpl].x += delta;
}

void pa_add_y(int32_t delta) {
    state.p[state.cpl].y += delta;
}

int32_t pa_gold(void) {
    return state.p[state.cpl].gold;
}

int pa_first_level(void) {
    return state.p[state.cpl].lv[0];
}

int32_t pa_lv_avg(void) {
    int32_t sum = 0;
    for_each_party_member(m) {
        sum += state.p[state.cpl].lv[m];
    }
    return sum / res_party_member_max();
}

static int pa_flag_slot(const char *tag) {
    int32_t slot = res_flag_slot(tag);
    if (slot < 0) {
        LOG_E("party",
              "pa_flag: unknown tag '%s' — add it to data/world.json5 flags:",
              tag);
        exit(1);
    }
    if (slot >= (int32_t)sizeof state.p[state.cpl].flag) {
        LOG_E("party", "pa_flag: tag '%s' slot %d out of range", tag, slot);
        exit(1);
    }
    return slot;
}

int pa_flag_get(const char *tag) {
    return state.p[state.cpl].flag[pa_flag_slot(tag)];
}

int pa_flag_is(const char *tag, const char *state_name) {
    int32_t want = res_flag_value_id(tag, state_name);
    if (want < 0) {
        LOG_E("party", "pa_flag_is: '%s' not in flag '%s' values[]",
              state_name, tag);
        exit(1);
    }
    return state.p[state.cpl].flag[pa_flag_slot(tag)] == want;
}

void pa_flag_set(const char *tag, const char *state_name) {
    int32_t want = res_flag_value_id(tag, state_name);
    if (want < 0) {
        LOG_E("party", "pa_flag_set: '%s' not in flag '%s' values[]",
              state_name, tag);
        exit(1);
    }
    state.p[state.cpl].flag[pa_flag_slot(tag)] = (unsigned char)want;
}

int pa_flag_count(void) {
    return (int)(sizeof state.p[state.cpl].flag /
                 sizeof state.p[state.cpl].flag[0]);
}

static void pa_check_flag_slot(const char *who, int slot) {
    if (slot < 0 || slot >= pa_flag_count()) {
        LOG_E("party", "%s: slot %d out of range [0, %d)", who, slot,
              pa_flag_count());
        exit(1);
    }
}

int pa_flag(int slot) {
    pa_check_flag_slot("pa_flag", slot);
    return state.p[state.cpl].flag[slot];
}

void pa_set_flag(int slot, int v) {
    pa_check_flag_slot("pa_set_flag", slot);
    state.p[state.cpl].flag[slot] = (unsigned char)v;
}

int32_t pa_torches(void) {
    return (int32_t)state.p[state.cpl].torches;
}

void pa_set_torches(int32_t v) {
    state.p[state.cpl].torches = (uint32_t)v;
}

void pa_add_torches(int32_t delta) {
    state.p[state.cpl].torches += (uint32_t)delta;
}

int32_t pa_food(void) {
    return state.p[state.cpl].food;
}

void pa_set_food(int32_t v) {
    state.p[state.cpl].food = v;
}

void pa_add_food(int32_t delta) {
    state.p[state.cpl].food += delta;
}

int pa_pl(void) {
    return (int)state.p[state.cpl].pl;
}

void pa_set_pl(int v) {
    state.p[state.cpl].pl = (unsigned char)v;
}

void pa_add_pl(int delta) {
    state.p[state.cpl].pl = (unsigned char)((int)state.p[state.cpl].pl + delta);
}

int32_t pa_hours_rested(void) {
    return state.p[state.cpl].hours_rested;
}

void pa_set_hours_rested(int32_t v) {
    state.p[state.cpl].hours_rested = v;
}

void pa_add_hours_rested(int32_t delta) {
    state.p[state.cpl].hours_rested += delta;
}

int32_t pa_time_left(void) {
    return state.p[state.cpl].time_left;
}

void pa_set_time_left(int32_t v) {
    state.p[state.cpl].time_left = v;
}

void pa_add_time_left(int32_t delta) {
    state.p[state.cpl].time_left += delta;
}

void pa_set_gold(int32_t v) {
    state.p[state.cpl].gold = v;
}

void pa_add_gold(int32_t delta) {
    state.p[state.cpl].gold += delta;
}

void pa_add_kills(uint32_t delta) {
    state.p[state.cpl].kills += delta;
}

static void pa_check_inv_slot(const char *who, int slot) {
    if (slot < 1 || slot > INVENTORY_SLOT_MAX) {
        LOG_E("party", "%s: slot %d out of range [1, %d]", who, slot,
              INVENTORY_SLOT_MAX);
        exit(1);
    }
}

int32_t pa_inventory(int slot) {
    pa_check_inv_slot("pa_inventory", slot);
    return state.p[state.cpl].inventory[slot];
}

void pa_set_inventory(int slot, int32_t item_id) {
    pa_check_inv_slot("pa_set_inventory", slot);
    state.p[state.cpl].inventory[slot] = item_id;
}

const char *pa_name(int m) {
    if (m < 0 || m >= res_party_member_max()) {
        return NULL;
    }
    return state.p[state.cpl].name[m];
}

const char *pa_party_name(void) {
    return state.p[state.cpl].party_name;
}

size_t pa_journal_n(void) {
    return state.p[state.cpl].journal_n;
}

const char *pa_journal_line(size_t i) {
    const struct party *p = &state.p[state.cpl];
    if (i >= p->journal_n) {
        return NULL;
    }
    return p->journal[i];
}

/* Special-item flag accessors. The five quest items live as
   unsigned-char fields on struct party (state.h). silverkey is a
   small state machine (0/1/2 for none/owned/returned-to-ydarg);
   the others are 0/1. The PS_* enum lives in inventory.h. */
static unsigned char *pa_special_ptr(int which) {
    struct party *p = &state.p[state.cpl];
    switch (which) {
        case PS_FIRESHIELD:
            return &p->fireshield;
        case PS_BRACELETS:
            return &p->bracelets;
        case PS_MASTERCROWN:
            return &p->mastercrown;
        case PS_JAZZYFOOT:
            return &p->jazzyfoot;
        case PS_SILVERKEY:
            return &p->silverkey;
        default:
            LOG_E("party",
                  "pa_special: unknown which=%d (PS_NONE..PS_LAST)",
                  which);
            exit(1);
    }
}

int pa_special(int which) {
    return *pa_special_ptr(which);
}

void pa_set_special(int which, int v) {
    *pa_special_ptr(which) = (unsigned char)v;
}

struct party *party_at(int idx) {
    if (idx < 0 || idx >= PARTY_SLOT_HARD_MAX) {
        LOG_E("party", "party_at(%d) out of range [0, %d)", idx,
              PARTY_SLOT_HARD_MAX);
        exit(1);
    }
    return &state.p[idx];
}

void party_journal_add(struct party *p, const char *line) {
    if (!p || !line || !line[0]) {
        return;
    }
    for (size_t i = 0; i < p->journal_n; i++) {
        if (p->journal[i] && !stricmp(p->journal[i], line)) {
            return;
        }
    }
    if (p->journal_n == p->journal_cap) {
        size_t cap = p->journal_cap ? p->journal_cap * 2 : 8;
        char **grown = realloc(p->journal, cap * sizeof *grown);
        if (!grown) {
            LOG_E("party", "party_journal_add: realloc failed");
            exit(1);
        }
        p->journal = grown;
        p->journal_cap = cap;
    }
    char *dup = strdup(line);
    if (!dup) {
        LOG_E("party", "party_journal_add: strdup failed");
        exit(1);
    }
    p->journal[p->journal_n++] = dup;
}

void free_journal(struct party *p) {
    if (!p) {
        return;
    }
    for (size_t i = 0; i < p->journal_n; i++) {
        free(p->journal[i]);
    }
    free(p->journal);
    p->journal = NULL;
    p->journal_n = 0;
    p->journal_cap = 0;
}

/* Grow a single zeroed heap buffer of `bytes` bytes, or die.
   Private to this file since every member-array alloc is "size =
   res_party_member_max() * sizeof T, memset 0" — the helper keeps
   the realize path readable instead of 14 repeated malloc / memset /
   null-check blocks. */
static void *alloc_zero(size_t bytes) {
    void *p = malloc(bytes);
    if (!p) {
        LOG_E("party", "party_realize: malloc(%zu) failed", bytes);
        exit(1);
    }
    memset(p, 0, bytes);
    return p;
}

void party_realize(struct party *p) {
    if (!p) {
        return;
    }
    /* Idempotent: free any previous allocation first so a re-realize
       after a load into an already-populated slot doesn't leak. The
       journal is NOT touched here — it's managed separately through
       free_journal / party_journal_add so load/save can read-clear-
       reappend independent of member-array lifetime. */
    party_release_members(p);

    size_t slots = (size_t)res_party_member_max();
    p->name = alloc_zero(slots * sizeof *p->name);
    p->ws = alloc_zero(slots * sizeof *p->ws);
    p->h = alloc_zero(slots * sizeof *p->h);
    p->hp = alloc_zero(slots * sizeof *p->hp);
    p->wi = alloc_zero(slots * sizeof *p->wi);
    p->ma = alloc_zero(slots * sizeof *p->ma);
    p->ch = alloc_zero(slots * sizeof *p->ch);
    p->cl = alloc_zero(slots * sizeof *p->cl);
    p->ra = alloc_zero(slots * sizeof *p->ra);
    p->lv = alloc_zero(slots * sizeof *p->lv);
    p->ar = alloc_zero(slots * sizeof *p->ar);
    p->wp = alloc_zero(slots * sizeof *p->wp);
    p->exp = alloc_zero(slots * sizeof *p->exp);
    p->dead = alloc_zero(slots * sizeof *p->dead);
    p->gender = alloc_zero(slots * sizeof *p->gender);
}

void party_release_members(struct party *p) {
    if (!p) {
        return;
    }
    free(p->name);
    free(p->ws);
    free(p->h);
    free(p->hp);
    free(p->wi);
    free(p->ma);
    free(p->ch);
    free(p->cl);
    free(p->ra);
    free(p->lv);
    free(p->ar);
    free(p->wp);
    free(p->exp);
    free(p->dead);
    free(p->gender);
    p->name = NULL;
    p->ws = p->h = p->hp = p->wi = p->ma = p->ch = NULL;
    p->cl = p->lv = p->ar = p->wp = NULL;
    p->ra = NULL; /* const char ** — separate to keep types coherent */
    p->exp = NULL;
    p->dead = NULL;
    p->gender = NULL;
}

void party_clear_heap_pointers(struct party *p) {
    if (!p) {
        return;
    }
    p->name = NULL;
    p->ws = p->h = p->hp = p->wi = p->ma = p->ch = NULL;
    p->cl = p->lv = p->ar = p->wp = NULL;
    p->ra = NULL; /* const char ** — separate to keep types coherent */
    p->exp = NULL;
    p->dead = NULL;
    p->gender = NULL;
    p->journal = NULL;
    p->journal_n = 0;
    p->journal_cap = 0;
}
