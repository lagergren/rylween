#include "party.h"

#include <stdio.h>
#include <stdlib.h>

#include "res.h"
#include "ryl.h" /* state */

static int in_range(int m) {
    return m >= 1 && m <= res_party_member_max();
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

int pa_is_spellcaster(int m) {
    if (!in_range(m)) {
        return 0;
    }
    int c = state.p[state.cpl].cl[m];
    return c == PC_MAGE || c == PC_PRIEST;
}

int pa_first_alive(void) {
    int mmax = res_party_member_max();
    for (int i = 1; i <= mmax; i++) {
        if (!state.p[state.cpl].dead[i]) {
            return i;
        }
    }
    return 0;
}

int pa_count_class(int cl) {
    int mmax = res_party_member_max();
    int count = 0;
    for (int i = 1; i <= mmax; i++) {
        if (state.p[state.cpl].cl[i] == cl) {
            count++;
        }
    }
    return count;
}

int pa_count_alive_class(int cl) {
    int mmax = res_party_member_max();
    int count = 0;
    for (int i = 1; i <= mmax; i++) {
        if (state.p[state.cpl].cl[i] == cl && !state.p[state.cpl].dead[i]) {
            count++;
        }
    }
    return count;
}

static int pa_flag_slot(const char *tag) {
    int32_t slot = res_flag_slot(tag);
    if (slot < 0) {
        fprintf(stderr, "pa_flag: unknown tag '%s' — add it to "
                        "data/world.json5 flags:\n", tag);
        exit(1);
    }
    if (slot >= (int32_t)sizeof state.p[state.cpl].flag) {
        fprintf(stderr, "pa_flag: tag '%s' slot %d out of range\n", tag, slot);
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
        fprintf(stderr, "pa_flag_is: '%s' not in flag '%s' values[]\n",
                state_name, tag);
        exit(1);
    }
    return state.p[state.cpl].flag[pa_flag_slot(tag)] == want;
}

void pa_flag_set(const char *tag, const char *state_name) {
    int32_t want = res_flag_value_id(tag, state_name);
    if (want < 0) {
        fprintf(stderr, "pa_flag_set: '%s' not in flag '%s' values[]\n",
                state_name, tag);
        exit(1);
    }
    state.p[state.cpl].flag[pa_flag_slot(tag)] = (unsigned char)want;
}
