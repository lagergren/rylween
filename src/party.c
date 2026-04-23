#include "party.h"

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
