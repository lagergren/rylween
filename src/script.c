/* §16.4 dialogue script interpreter. Public contract: script.h. */

#include "script.h"

#include "driv.h"
#include "json5.h"
#include "primitives.h"
#include "res.h"
#include "ryl.h"

#include <stdio.h>
#include <string.h>

/* Build the parent-path prefix for resolving relative {screen: 'name'}
   text ids. Script id "speak.erethor.first_visit" -> "speak.erethor".
   Writes into `out` (bounded); caller guarantees enough room. */
static void script_parent(const char *script_id, char *out, size_t out_sz) {
    const char *last = strrchr(script_id, '.');
    if (!last) {
        if (out_sz > 0) {
            out[0] = 0;
        }
        return;
    }
    size_t len = (size_t)(last - script_id);
    if (len >= out_sz) {
        len = out_sz - 1;
    }
    memcpy(out, script_id, len);
    out[len] = 0;
}

static __attribute__((noreturn)) void die_at(const char *script_id, size_t idx,
                                             const char *msg) {
    char buf[256];
    snprintf(buf, sizeof buf, "play_script '%s' op %zu: %s", script_id, idx, msg);
    ryl_die(buf);
}

static void do_screen(const node *op, const char *script_id, size_t idx,
                      const char *parent, const ryl_bind *bindings) {
    const node *name_n = obj_get(op, "screen");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "screen: value must be string");
    }
    char text_id[256];
    int n;
    if (parent[0]) {
        n = snprintf(text_id, sizeof text_id, "%s.%s", parent, name_n->u.sv);
    } else {
        n = snprintf(text_id, sizeof text_id, "%s", name_n->u.sv);
    }
    if (n < 0 || n >= (int)sizeof text_id) {
        die_at(script_id, idx, "screen: text id too long");
    }
    tx_fmt(text_id, bindings);
    ryl_pause();
    clw();
}

static void do_journal(const node *op, const char *script_id, size_t idx,
                       const ryl_bind *bindings) {
    const node *txt_n = obj_get(op, "journal");
    if (!txt_n || txt_n->kind != N_STR) {
        die_at(script_id, idx, "journal: value must be string");
    }
    char body[512];
    char ctx[320];
    snprintf(ctx, sizeof ctx, "%s:journal[%zu]", script_id, idx);
    bind_expand(txt_n->u.sv, bindings, body, sizeof body, ctx);

    char line[576];
    int n = snprintf(line, sizeof line, "%d %s\n", state.cpl, body);
    if (n < 0 || n >= (int)sizeof line) {
        die_at(script_id, idx, "journal: line too long");
    }
    journal(line);
}

static void do_set_flag(const node *op, const char *script_id, size_t idx) {
    const node *slot_n = obj_get(op, "set_flag");
    const node *val_n = obj_get(op, "value");
    if (!slot_n) {
        die_at(script_id, idx, "set_flag: missing slot");
    }
    if (!val_n || val_n->kind != N_INT) {
        die_at(script_id, idx, "set_flag: value must be int");
    }
    int32_t slot;
    if (slot_n->kind == N_INT) {
        slot = slot_n->u.iv;
    } else if (slot_n->kind == N_STR) {
        slot = res_flag_slot(slot_n->u.sv);
        if (slot < 0) {
            die_at(script_id, idx, "set_flag: unknown symbolic flag id");
        }
    } else {
        die_at(script_id, idx, "set_flag: slot must be int or string");
    }
    if (slot < 0 || slot >= (int32_t)(sizeof state.p[state.cpl].flag)) {
        die_at(script_id, idx, "set_flag: slot out of range");
    }
    state.p[state.cpl].flag[slot] = (unsigned char)val_n->u.iv;
}

void play_script(const char *script_id, const ryl_bind *bindings) {
    const node *script = res_script(script_id);
    if (!script) {
        char buf[256];
        snprintf(buf, sizeof buf, "play_script: script '%s' not found", script_id);
        ryl_die(buf);
    }
    char parent[256];
    script_parent(script_id, parent, sizeof parent);

    for (size_t i = 0; i < script->u.arr.n; i++) {
        const node *op = script->u.arr.items[i];
        if (!op || op->kind != N_OBJ) {
            die_at(script_id, i, "op must be object");
        }
        if (obj_get(op, "screen")) {
            do_screen(op, script_id, i, parent, bindings);
        } else if (obj_get(op, "journal")) {
            do_journal(op, script_id, i, bindings);
        } else if (obj_get(op, "set_flag")) {
            do_set_flag(op, script_id, i);
        } else {
            die_at(script_id, i, "unknown op (expected screen / journal / set_flag)");
        }
    }
}
