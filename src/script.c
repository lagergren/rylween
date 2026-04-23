/* §16.4 + §17-E dialogue / mission script interpreter.
   Public contract: script.h. */

#include "script.h"

#include "battle.h"
#include "driv.h"
#include "dungeon.h"
#include "gfx.h"
#include "json5.h"
#include "primitives.h"
#include "res.h"
#include "ryl.h"
#include "subs.h"

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

/* Forward declaration: play_ops recurses into do_if / do_chance /
   do_menu_branch bodies. `parent` matches do_screen's derivation so
   nested screens resolve against the same res_text tree as the
   enclosing script. */
static void play_ops(const node *ops, const char *script_id, const char *parent,
                     const ryl_bind *bindings);

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

/* §17-E: { pause: N } — ryl_delay(N) ms. Replaces inline ryl_delay()
   calls between screens in the imperative flows A.4 is migrating. */
static void do_pause(const node *op, const char *script_id, size_t idx) {
    const node *ms_n = obj_get(op, "pause");
    if (!ms_n || ms_n->kind != N_INT) {
        die_at(script_id, idx, "pause: value must be int (ms)");
    }
    if (ms_n->u.iv < 0) {
        die_at(script_id, idx, "pause: ms must be non-negative");
    }
    ryl_delay((unsigned)ms_n->u.iv);
}

/* §17-E: { emit: '<name>' } — tx_fmt only, no ryl_pause, no clw. For
   flows that interleave text with engine_call side effects (encounter
   / update_map / clw) where the screen-op's bundled pause+clw would
   double-clear. */
static void do_emit(const node *op, const char *script_id, size_t idx,
                    const char *parent, const ryl_bind *bindings) {
    const node *name_n = obj_get(op, "emit");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "emit: value must be string");
    }
    char text_id[256];
    resolve_text_id(parent, name_n->u.sv, text_id, sizeof text_id,
                    script_id, idx, "emit");
    tx_fmt(text_id, bindings);
}

/* §17-E: { wait_input: true } — ryl_pause(). Separate from `pause`
   because ryl_pause blocks on a keypress ("Smash RETURN key") and
   ryl_delay is a fixed-length sleep. Both appear in the original
   control flow, sometimes back-to-back. */
static void do_wait_input(const node *op, const char *script_id, size_t idx) {
    const node *v = obj_get(op, "wait_input");
    if (!v) {
        die_at(script_id, idx, "wait_input: missing value");
    }
    (void)v;
    ryl_pause();
}

/* Engine-function registry: the whitelist of C entry points callable
   from { engine_call: '<name>', args: [int...] }. All registered fns
   take zero, one, or two int32 args — no return value. Adding a new
   entry is a line in `engine_tbl`; the DSL does NOT accept arbitrary
   symbol lookups. */
typedef void (*engine_fn)(const int32_t *args);

static void eng_dungeon(const int32_t *a) {
    dungeon(a[0], a[1]);
}
static void eng_encounter(const int32_t *a) {
    encounter(a[0], a[1]);
}
static void eng_update_map(const int32_t *a) {
    (void)a;
    update_map();
}
static void eng_clr_map_window(const int32_t *a) {
    (void)a;
    clr_map_window();
}
static void eng_blitgfx(const int32_t *a) {
    blitgfx((uint8_t)a[0]);
}
static void eng_statusline(const int32_t *a) {
    (void)a;
    statusline();
}
static void eng_clw(const int32_t *a) {
    (void)a;
    clw();
}

typedef struct {
    const char *name;
    int argc;
    engine_fn fn;
} engine_entry;

static const engine_entry engine_tbl[] = {
    {"dungeon", 2, eng_dungeon},
    {"encounter", 2, eng_encounter},
    {"update_map", 0, eng_update_map},
    {"clr_map_window", 0, eng_clr_map_window},
    {"blitgfx", 1, eng_blitgfx},
    {"statusline", 0, eng_statusline},
    {"clw", 0, eng_clw},
};

/* §17-E: { engine_call: '<name>', args: [int...] }. Literal int args
   only — no binding substitution; authors that need a value derived
   from runtime state should use a dedicated op (set_flag, reward) or
   widen the registry. */
static void do_engine_call(const node *op, const char *script_id, size_t idx) {
    const node *name_n = obj_get(op, "engine_call");
    if (!name_n || name_n->kind != N_STR) {
        die_at(script_id, idx, "engine_call: value must be string");
    }
    const engine_entry *e = NULL;
    for (size_t i = 0; i < sizeof engine_tbl / sizeof *engine_tbl; i++) {
        if (strcmp(engine_tbl[i].name, name_n->u.sv) == 0) {
            e = &engine_tbl[i];
            break;
        }
    }
    if (!e) {
        char buf[96];
        snprintf(buf, sizeof buf, "engine_call: unknown fn '%s'", name_n->u.sv);
        die_at(script_id, idx, buf);
    }
    const node *args_n = obj_get(op, "args");
    int32_t args[4] = {0};
    int got = 0;
    if (args_n) {
        if (args_n->kind != N_ARR) {
            die_at(script_id, idx, "engine_call: args must be array");
        }
        got = (int)args_n->u.arr.n;
        if (got > 4) {
            die_at(script_id, idx, "engine_call: too many args (max 4)");
        }
        for (int i = 0; i < got; i++) {
            const node *a = args_n->u.arr.items[i];
            if (!a || a->kind != N_INT) {
                die_at(script_id, idx, "engine_call: args must be ints");
            }
            args[i] = (int32_t)a->u.iv;
        }
    }
    if (got != e->argc) {
        char buf[96];
        snprintf(buf, sizeof buf, "engine_call: '%s' expects %d args, got %d",
                 e->name, e->argc, got);
        die_at(script_id, idx, buf);
    }
    e->fn(args);
}

/* §17-E: { chance: { pct: N, then:[...], else:[...] } } — ryl_chance(pct)
   gate. `then` and `else` are nested op arrays; both optional. */
static void do_chance(const node *op, const char *script_id, size_t idx,
                      const char *parent, const ryl_bind *bindings) {
    const node *ch = obj_get(op, "chance");
    if (!ch || ch->kind != N_OBJ) {
        die_at(script_id, idx, "chance: value must be object");
    }
    const node *pct_n = obj_get(ch, "pct");
    if (!pct_n || pct_n->kind != N_INT) {
        die_at(script_id, idx, "chance: pct must be int");
    }
    const node *then_n = obj_get(ch, "then");
    const node *else_n = obj_get(ch, "else");
    const node *take = ryl_chance((int)pct_n->u.iv) ? then_n : else_n;
    if (!take) {
        return;
    }
    if (take->kind != N_ARR) {
        die_at(script_id, idx, "chance: then/else must be array");
    }
    play_ops(take, script_id, parent, bindings);
}

/* Evaluate an `if` condition object:
     { flag: <int|name>, eq: <int> }        flag slot == value
     { flag: <int|name>, ne: <int> }        flag slot != value
     { gold: { gte: <int> } }               party gold >= value
     { gold: { lt: <int> } }                party gold <  value
   No combinators yet — authors who need AND/OR nest `if` ops. */
static int eval_cond(const node *cond, const char *script_id, size_t idx) {
    if (!cond || cond->kind != N_OBJ) {
        die_at(script_id, idx, "if: cond must be object");
    }
    const node *flag_n = obj_get(cond, "flag");
    const node *gold_n = obj_get(cond, "gold");
    if (flag_n) {
        int32_t slot;
        if (flag_n->kind == N_INT) {
            slot = (int32_t)flag_n->u.iv;
        } else if (flag_n->kind == N_STR) {
            slot = res_flag_slot(flag_n->u.sv);
            if (slot < 0) {
                die_at(script_id, idx, "if: unknown symbolic flag id");
            }
        } else {
            die_at(script_id, idx, "if: flag must be int or string");
        }
        if (slot < 0 || slot >= (int32_t)(sizeof state.p[state.cpl].flag)) {
            die_at(script_id, idx, "if: flag slot out of range");
        }
        int cur = state.p[state.cpl].flag[slot];
        const node *eq = obj_get(cond, "eq");
        const node *ne = obj_get(cond, "ne");
        if (eq && eq->kind == N_INT) {
            return cur == (int)eq->u.iv;
        }
        if (ne && ne->kind == N_INT) {
            return cur != (int)ne->u.iv;
        }
        die_at(script_id, idx, "if: flag needs eq or ne");
    }
    if (gold_n) {
        if (gold_n->kind != N_OBJ) {
            die_at(script_id, idx, "if: gold must be object");
        }
        int32_t cur = state.p[state.cpl].gold;
        const node *gte = obj_get(gold_n, "gte");
        const node *lt = obj_get(gold_n, "lt");
        if (gte && gte->kind == N_INT) {
            return cur >= (int32_t)gte->u.iv;
        }
        if (lt && lt->kind == N_INT) {
            return cur < (int32_t)lt->u.iv;
        }
        die_at(script_id, idx, "if: gold needs gte or lt");
    }
    die_at(script_id, idx, "if: cond must have flag or gold");
}

/* §17-E: { if: { cond, then:[...], else:[...] } } — flag / gold branch. */
static void do_if(const node *op, const char *script_id, size_t idx,
                  const char *parent, const ryl_bind *bindings) {
    const node *body = obj_get(op, "if");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "if: value must be object");
    }
    const node *cond = obj_get(body, "cond");
    const node *then_n = obj_get(body, "then");
    const node *else_n = obj_get(body, "else");
    const node *take = eval_cond(cond, script_id, idx) ? then_n : else_n;
    if (!take) {
        return;
    }
    if (take->kind != N_ARR) {
        die_at(script_id, idx, "if: then/else must be array");
    }
    play_ops(take, script_id, parent, bindings);
}

/* §17-E: { menu_branch: { prompt: '<name>'?, options: [{ key:'K',
   then:[...] }, ...] } }. Optional `prompt` names a screen text id
   (parent-relative, like `screen`) to emit before the keypress; omit
   to stack on an earlier tx_fmt / screen. Unmatched keys fall through
   to an optional `default: [...]` op array (no-op if absent). Single
   keypress — the enclosing loop (if any) is the caller's job. */
static void do_menu_branch(const node *op, const char *script_id, size_t idx,
                           const char *parent, const ryl_bind *bindings) {
    const node *body = obj_get(op, "menu_branch");
    if (!body || body->kind != N_OBJ) {
        die_at(script_id, idx, "menu_branch: value must be object");
    }
    const node *prompt_n = obj_get(body, "prompt");
    const node *opts_n = obj_get(body, "options");
    const node *default_n = obj_get(body, "default");
    if (!opts_n || opts_n->kind != N_ARR) {
        die_at(script_id, idx, "menu_branch: options must be array");
    }
    if (prompt_n) {
        if (prompt_n->kind != N_STR) {
            die_at(script_id, idx, "menu_branch: prompt must be string");
        }
        char text_id[256];
        resolve_text_id(parent, prompt_n->u.sv, text_id, sizeof text_id,
                        script_id, idx, "menu_branch prompt");
        tx_fmt(text_id, bindings);
    }
    char ch = (char)toupper((unsigned char)key());
    for (size_t i = 0; i < opts_n->u.arr.n; i++) {
        const node *o = opts_n->u.arr.items[i];
        if (!o || o->kind != N_OBJ) {
            die_at(script_id, idx, "menu_branch: option must be object");
        }
        const node *key_n = obj_get(o, "key");
        const node *then_n = obj_get(o, "then");
        if (!key_n || key_n->kind != N_STR || key_n->u.sv[0] == 0 ||
            key_n->u.sv[1] != 0) {
            die_at(script_id, idx, "menu_branch: key must be one-char string");
        }
        char want = (char)toupper((unsigned char)key_n->u.sv[0]);
        if (want != ch) {
            continue;
        }
        if (!then_n) {
            return;
        }
        if (then_n->kind != N_ARR) {
            die_at(script_id, idx, "menu_branch: then must be array");
        }
        play_ops(then_n, script_id, parent, bindings);
        return;
    }
    if (default_n) {
        if (default_n->kind != N_ARR) {
            die_at(script_id, idx, "menu_branch: default must be array");
        }
        play_ops(default_n, script_id, parent, bindings);
    }
}

static void dispatch_op(const node *op, const char *script_id, size_t idx,
                        const char *parent, const ryl_bind *bindings) {
    if (!op || op->kind != N_OBJ) {
        die_at(script_id, idx, "op must be object");
    }
    if (obj_get(op, "screen")) {
        do_screen(op, script_id, idx, parent, bindings);
    } else if (obj_get(op, "journal")) {
        do_journal(op, script_id, idx, bindings);
    } else if (obj_get(op, "set_flag")) {
        do_set_flag(op, script_id, idx);
    } else if (obj_get(op, "reward")) {
        do_reward(op, script_id, idx, parent, bindings);
    } else if (obj_get(op, "pause")) {
        do_pause(op, script_id, idx);
    } else if (obj_get(op, "emit")) {
        do_emit(op, script_id, idx, parent, bindings);
    } else if (obj_get(op, "wait_input")) {
        do_wait_input(op, script_id, idx);
    } else if (obj_get(op, "chance")) {
        do_chance(op, script_id, idx, parent, bindings);
    } else if (obj_get(op, "if")) {
        do_if(op, script_id, idx, parent, bindings);
    } else if (obj_get(op, "menu_branch")) {
        do_menu_branch(op, script_id, idx, parent, bindings);
    } else if (obj_get(op, "engine_call")) {
        do_engine_call(op, script_id, idx);
    } else {
        die_at(script_id, idx, "unknown op");
    }
}

static void play_ops(const node *ops, const char *script_id, const char *parent,
                     const ryl_bind *bindings) {
    for (size_t i = 0; i < ops->u.arr.n; i++) {
        dispatch_op(ops->u.arr.items[i], script_id, i, parent, bindings);
    }
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
    play_ops(script, script_id, parent, bindings);
}
