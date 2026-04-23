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

/* Build "<parent>.<name>" into `out`, or just "<name>" when parent is
   empty. Used by do_screen and do_reward's cant_carry / picked_up
   fall-outs; shared helper so both take the same "text id too long"
   diagnostic. */
static void resolve_text_id(const char *parent, const char *name, char *out,
                            size_t out_sz, const char *script_id, size_t idx,
                            const char *what) {
    int n = parent[0] ? snprintf(out, out_sz, "%s.%s", parent, name)
                      : snprintf(out, out_sz, "%s", name);
    if (n < 0 || n >= (int)out_sz) {
        char buf[64];
        snprintf(buf, sizeof buf, "%s: text id too long", what);
        die_at(script_id, idx, buf);
    }
}

static void do_screen(const node *op, const char *script_id, size_t idx,
                      const char *parent, const ryl_bind *bindings) {
    const node *name_n = obj_get(op, "screen");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "screen: value must be string");
    }
    char text_id[256];
    resolve_text_id(parent, name_n->u.sv, text_id, sizeof text_id,
                    script_id, idx, "screen");
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

static int32_t resolve_reward_item(const node *e, const char *script_id,
                                   size_t idx) {
    if (!e) {
        die_at(script_id, idx, "reward: items[] entry missing");
    }
    if (e->kind == N_INT) {
        int32_t id = (int32_t)e->u.iv;
        if (id <= 0) {
            die_at(script_id, idx, "reward: item id must be positive");
        }
        if (!res_item(id)) {
            die_at(script_id, idx, "reward: item id has no record");
        }
        return id;
    }
    if (e->kind == N_STR) {
        int32_t id = res_item_id(e->u.sv);
        if (id < 0) {
            die_at(script_id, idx, "reward: unknown item symbol");
        }
        return id;
    }
    die_at(script_id, idx, "reward: items[] entry must be int or string");
}

/* §16.5: { reward: { gold: N, items: [int|str...], cant_carry_screen:
   '<name>', picked_up_screen: '<name>' } }. Adds gold to the current
   party, then walks items[]: places each into the first empty
   inventory slot (1..16). If no slot is free, emits the optional
   cant_carry_screen (tx_fmt + bindings inherited from the enclosing
   play_script), delays 800ms, calls drop_option(); on success emits
   the optional picked_up_screen, delays 800ms, and ryl_pause()s so
   the player reads the pickup line. Items may be authored as literal
   ids (matching the port-plan example) or as symbolic shard keys
   resolved via res_item_id. */
static void do_reward(const node *op, const char *script_id, size_t idx,
                      const char *parent, const ryl_bind *bindings) {
    const node *reward = obj_get(op, "reward");
    if (!reward || reward->kind != N_OBJ) {
        die_at(script_id, idx, "reward: value must be object");
    }
    const node *gold_n = obj_get(reward, "gold");
    const node *items_n = obj_get(reward, "items");
    const node *cc_n = obj_get(reward, "cant_carry_screen");
    const node *pu_n = obj_get(reward, "picked_up_screen");
    if (gold_n) {
        if (gold_n->kind != N_INT) {
            die_at(script_id, idx, "reward: gold must be int");
        }
        state.p[state.cpl].gold += gold_n->u.iv;
    }
    if (!items_n) {
        return;
    }
    if (items_n->kind != N_ARR) {
        die_at(script_id, idx, "reward: items must be array");
    }
    if (cc_n && cc_n->kind != N_STR) {
        die_at(script_id, idx, "reward: cant_carry_screen must be string");
    }
    if (pu_n && pu_n->kind != N_STR) {
        die_at(script_id, idx, "reward: picked_up_screen must be string");
    }

    for (size_t k = 0; k < items_n->u.arr.n; k++) {
        int32_t item_id = resolve_reward_item(items_n->u.arr.items[k],
                                              script_id, idx);
        int placed = 0;
        for (int i = 1; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] == 0) {
                state.p[state.cpl].inventory[i] = item_id;
                placed = 1;
                break;
            }
        }
        if (placed || !cc_n) {
            continue;
        }
        char text_id[256];
        resolve_text_id(parent, cc_n->u.sv, text_id, sizeof text_id,
                        script_id, idx, "reward cant_carry_screen");
        tx_fmt(text_id, bindings);
        ryl_delay(800);
        uint8_t carry = drop_option();
        if (carry) {
            clw();
            state.p[state.cpl].inventory[carry] = item_id;
            if (pu_n) {
                resolve_text_id(parent, pu_n->u.sv, text_id, sizeof text_id,
                                script_id, idx, "reward picked_up_screen");
                tx_fmt(text_id, bindings);
            }
            ryl_delay(800);
        }
        ryl_pause();
    }
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
        } else if (obj_get(op, "reward")) {
            do_reward(op, script_id, i, parent, bindings);
        } else {
            die_at(script_id, i, "unknown op (expected screen / journal / set_flag / reward)");
        }
    }
}
