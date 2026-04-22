/* Phase 6d evaluator. See docs/dsl.md §6 for the expression language.
 *
 * This module owns:
 *   - parse_path: "caster.hp" / "$dmg" / "target.inventory[3]" -> path_t.
 *   - compile_world_tree: recursive walk that identifies every effect /
 *     cond / expr array and rewrites N_STR nodes at path positions to
 *     N_PATH. Arity and unknown-op checks are enforced here.
 *   - eval_expr, eval_cond, eval_effect.
 *
 * The node allocation surface (node_new / node_free / obj_get) and the
 * JSON5 parser live in json5.c. Failure mode is always "fail loud":
 * any unknown op, arity mismatch, or unbound reference dies with a
 * path:line:msg message.
 */

#include "eval.h"
#include "json5.h"
#include "res.h"
#include "ryl.h"
#include "driv.h"
#include "primitives.h"

#include <ctype.h>
#include <limits.h>
#include <setjmp.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ---------------------------------------------------------------- */
/*  Error reporting                                                 */
/* ---------------------------------------------------------------- */

__attribute__((noreturn)) static void cdie(const char *path, int line, const char *msg) {
    fprintf(stderr, "res: %s:%d: %s\n", path, line, msg);
    exit(1);
}

__attribute__((noreturn)) static void rdie(int line, const char *msg) {
    fprintf(stderr, "res: eval:%d: %s\n", line, msg);
    exit(1);
}

/* ---------------------------------------------------------------- */
/*  Path parser                                                     */
/* ---------------------------------------------------------------- */

static int match_literal(const char *s, size_t n, const char *lit) {
    size_t ln = strlen(lit);
    return n == ln && memcmp(s, lit, ln) == 0;
}

static int8_t field_from(const char *s, size_t n) {
    if (match_literal(s, n, "hp")) {
        return PF_HP;
    }
    if (match_literal(s, n, "h")) {
        return PF_H;
    }
    if (match_literal(s, n, "ma")) {
        return PF_MA;
    }
    if (match_literal(s, n, "wi")) {
        return PF_WI;
    }
    if (match_literal(s, n, "lv")) {
        return PF_LV;
    }
    if (match_literal(s, n, "ws")) {
        return PF_WS;
    }
    if (match_literal(s, n, "ch")) {
        return PF_CH;
    }
    if (match_literal(s, n, "ac")) {
        return PF_AC;
    }
    if (match_literal(s, n, "dead")) {
        return PF_DEAD;
    }
    if (match_literal(s, n, "name")) {
        return PF_NAME;
    }
    if (match_literal(s, n, "cl")) {
        return PF_CL;
    }
    if (match_literal(s, n, "gold")) {
        return PF_GOLD;
    }
    if (match_literal(s, n, "visited")) {
        return PF_VISITED;
    }
    if (match_literal(s, n, "inventory")) {
        return PF_INVENTORY;
    }
    if (match_literal(s, n, "banished")) {
        return PF_BANISHED;
    }
    if (match_literal(s, n, "x")) {
        return PF_X;
    }
    if (match_literal(s, n, "y")) {
        return PF_Y;
    }
    if (match_literal(s, n, "fireshield")) {
        return PF_FIRESHIELD;
    }
    if (match_literal(s, n, "bracelets")) {
        return PF_BRACELETS;
    }
    if (match_literal(s, n, "mastercrown")) {
        return PF_MASTERCROWN;
    }
    if (match_literal(s, n, "jazzyfoot")) {
        return PF_JAZZYFOOT;
    }
    if (match_literal(s, n, "silverkey")) {
        return PF_SILVERKEY;
    }
    return PF_NONE;
}

static void die_path(const char *src, int line, const char *what) {
    fprintf(stderr, "res: path:%d: %s (in '%s')\n", line, what, src);
    exit(1);
}

/* Parse a dotted / bracketed path string into a path_t. Fatal on failure. */
static void parse_path(const char *src, int line, path_t *out) {
    memset(out, 0, sizeof *out);
    out->line = line;

    /* $ident — local binding, carries no field. */
    if (src[0] == '$') {
        if (!src[1]) {
            die_path(src, line, "empty local name");
        }
        out->actor = PA_LOCAL;
        out->local = strdup(src + 1);
        return;
    }

    /* "base_cost" is sugar for the spell-scoped local of the same name. */
    if (strcmp(src, "base_cost") == 0) {
        out->actor = PA_LOCAL;
        out->local = strdup("base_cost");
        return;
    }

    /* Head: up to the first '.' or '['. */
    const char *p = src;
    const char *dot = strchr(p, '.');
    const char *br = strchr(p, '[');
    const char *head_end = dot;
    if (br && (!head_end || br < head_end)) {
        head_end = br;
    }
    if (!head_end) {
        die_path(src, line, "missing '.' or '['");
    }
    size_t head_n = (size_t)(head_end - p);

    if (match_literal(p, head_n, "caster")) {
        out->actor = PA_CASTER;
    } else if (match_literal(p, head_n, "target")) {
        out->actor = PA_TARGET;
    } else if (match_literal(p, head_n, "speaker")) {
        out->actor = PA_SPEAKER;
    } else if (match_literal(p, head_n, "self")) {
        out->actor = PA_SELF;
    } else if (match_literal(p, head_n, "state")) {
        out->actor = PA_STATE;
    } else if (match_literal(p, head_n, "party")) {
        out->actor = PA_PARTY;
    } else if (match_literal(p, head_n, "npc")) {
        if (*head_end != '[') {
            die_path(src, line, "npc path needs '[N]'");
        }
        char *end;
        long idx = strtol(head_end + 1, &end, 10);
        if (end == head_end + 1 || *end != ']') {
            die_path(src, line, "bad npc subscript");
        }
        out->actor = PA_NPC;
        out->index = (int32_t)idx;
        head_end = end + 1;
    } else {
        die_path(src, line, "unknown path root");
    }

    /* Must be followed by ".field" or (for actor-owned arrays) ".field[N]". */
    if (*head_end != '.') {
        die_path(src, line, "expected '.' after path root");
    }
    const char *tail = head_end + 1;
    const char *tbr = strchr(tail, '[');
    size_t tail_n = tbr ? (size_t)(tbr - tail) : strlen(tail);
    int8_t f = field_from(tail, tail_n);
    if (f == PF_NONE) {
        die_path(src, line, "unknown field name");
    }
    out->field = f;

    if (tbr) {
        char *end;
        long idx = strtol(tbr + 1, &end, 10);
        if (end == tbr + 1 || *end != ']' || *(end + 1) != 0) {
            die_path(src, line, "bad '[N]' subscript");
        }
        /* PA_NPC already used `index` for the outer subscript; field-level
           subscripts belong to the actor-owned inventory[N] case. Until the
           DSL needs both at once (it currently doesn't), reuse the slot. */
        if (out->actor == PA_NPC) {
            die_path(src, line, "subscript on npc field not supported");
        }
        out->index = (int32_t)idx;
    }
}

/* Rewrite an N_STR node in-place to N_PATH by parsing its string payload. */
static void compile_string_to_path(node *n) {
    if (n->kind == N_PATH) {
        return;
    }
    if (n->kind != N_STR) {
        rdie(n->line, "expected string as path");
    }
    char *s = n->u.sv;
    path_t path;
    parse_path(s, n->line, &path);
    free(s);
    n->kind = N_PATH;
    n->u.path = path;
}

/* ---------------------------------------------------------------- */
/*  Compile pass                                                    */
/* ---------------------------------------------------------------- */

static const char *arr_op(const node *n) {
    if (!n || n->kind != N_ARR || n->u.arr.n == 0) {
        return NULL;
    }
    const node *head = n->u.arr.items[0];
    return (head && head->kind == N_STR) ? head->u.sv : NULL;
}

static void check_arity(const node *n, const char *src_path, size_t lo, size_t hi) {
    if (n->u.arr.n < lo || n->u.arr.n > hi) {
        cdie(src_path, n->line, "wrong arg count");
    }
}

static void compile_expr(node *n, const char *src_path);
static void compile_cond(node *n, const char *src_path);
static void compile_effect(node *n, const char *src_path);

static void compile_expr(node *n, const char *src_path) {
    if (!n) {
        return;
    }
    switch (n->kind) {
        case N_INT:
        case N_PATH:
            return;
        case N_STR:
            compile_string_to_path(n);
            return;
        case N_ARR:
            break;
        default:
            cdie(src_path, n->line, "expr must be int / path / array");
    }

    const char *op = arr_op(n);
    if (!op) {
        cdie(src_path, n->line, "expr array missing op");
    }
    if (strcmp(op, "+") == 0 || strcmp(op, "*") == 0 ||
        strcmp(op, "min") == 0 || strcmp(op, "max") == 0) {
        if (n->u.arr.n < 2) {
            cdie(src_path, n->line, "n-ary op needs >=1 arg");
        }
        for (size_t i = 1; i < n->u.arr.n; i++) {
            compile_expr(n->u.arr.items[i], src_path);
        }
    } else if (strcmp(op, "-") == 0 || strcmp(op, "/") == 0) {
        check_arity(n, src_path, 3, 3);
        compile_expr(n->u.arr.items[1], src_path);
        compile_expr(n->u.arr.items[2], src_path);
    } else if (strcmp(op, "clamp") == 0) {
        check_arity(n, src_path, 4, 4);
        compile_expr(n->u.arr.items[1], src_path);
        compile_expr(n->u.arr.items[2], src_path);
        compile_expr(n->u.arr.items[3], src_path);
    } else if (strcmp(op, "rand") == 0) {
        check_arity(n, src_path, 3, 3);
        compile_expr(n->u.arr.items[1], src_path);
        compile_expr(n->u.arr.items[2], src_path);
    } else if (strcmp(op, "cond") == 0) {
        check_arity(n, src_path, 4, 4);
        compile_cond(n->u.arr.items[1], src_path);
        compile_expr(n->u.arr.items[2], src_path);
        compile_expr(n->u.arr.items[3], src_path);
    } else {
        cdie(src_path, n->line, "unknown expr op");
    }
}

static void compile_cond(node *n, const char *src_path) {
    if (!n || n->kind != N_ARR) {
        cdie(src_path, n ? n->line : 0, "cond must be array");
    }
    const char *op = arr_op(n);
    if (!op) {
        cdie(src_path, n->line, "cond missing op");
    }
    if (strcmp(op, "and") == 0 || strcmp(op, "or") == 0) {
        if (n->u.arr.n < 2) {
            cdie(src_path, n->line, "and/or need >=1 arg");
        }
        for (size_t i = 1; i < n->u.arr.n; i++) {
            compile_cond(n->u.arr.items[i], src_path);
        }
    } else if (strcmp(op, "not") == 0) {
        check_arity(n, src_path, 2, 2);
        compile_cond(n->u.arr.items[1], src_path);
    } else if (strcmp(op, "flag_eq") == 0) {
        check_arity(n, src_path, 3, 3);
        if (n->u.arr.items[1]->kind != N_STR) {
            cdie(src_path, n->u.arr.items[1]->line, "flag_eq: flag must be string");
        }
        compile_expr(n->u.arr.items[2], src_path);
    } else if (strcmp(op, "has_item") == 0) {
        check_arity(n, src_path, 2, 3);
        /* arg 1: item-id (int or symbol string; leave N_STR for now). */
        if (n->u.arr.n == 3) {
            compile_expr(n->u.arr.items[2], src_path);
        }
    } else if (strcmp(op, "gold_gte") == 0) {
        check_arity(n, src_path, 2, 2);
        compile_expr(n->u.arr.items[1], src_path);
    } else if (strcmp(op, "in_dungeon") == 0 ||
               strcmp(op, "in_town") == 0 ||
               strcmp(op, "in_wilderness") == 0) {
        check_arity(n, src_path, 1, 1);
    } else if (strcmp(op, "class_is") == 0) {
        check_arity(n, src_path, 3, 3);
        compile_expr(n->u.arr.items[1], src_path); /* actor-ref as path */
        if (n->u.arr.items[2]->kind != N_STR) {
            cdie(src_path, n->u.arr.items[2]->line, "class_is: class must be string");
        }
    } else if (strcmp(op, "alive") == 0) {
        check_arity(n, src_path, 2, 2);
        compile_expr(n->u.arr.items[1], src_path);
    } else if (strcmp(op, "chance") == 0) {
        check_arity(n, src_path, 2, 2);
        compile_expr(n->u.arr.items[1], src_path);
    } else if (strcmp(op, "eq") == 0 || strcmp(op, "lt") == 0 || strcmp(op, "gte") == 0) {
        check_arity(n, src_path, 3, 3);
        compile_expr(n->u.arr.items[1], src_path);
        compile_expr(n->u.arr.items[2], src_path);
    } else {
        cdie(src_path, n->line, "unknown cond op");
    }
}

static void compile_effect(node *n, const char *src_path) {
    if (!n || n->kind != N_ARR) {
        cdie(src_path, n ? n->line : 0, "effect must be array");
    }
    const char *op = arr_op(n);
    if (!op) {
        cdie(src_path, n->line, "effect missing op");
    }
    if (strcmp(op, "seq") == 0) {
        for (size_t i = 1; i < n->u.arr.n; i++) {
            compile_effect(n->u.arr.items[i], src_path);
        }
    } else if (strcmp(op, "if") == 0) {
        check_arity(n, src_path, 3, 4);
        compile_cond(n->u.arr.items[1], src_path);
        compile_effect(n->u.arr.items[2], src_path);
        if (n->u.arr.n == 4) {
            compile_effect(n->u.arr.items[3], src_path);
        }
    } else if (strcmp(op, "say") == 0 || strcmp(op, "journal") == 0) {
        if (n->u.arr.n < 2) {
            cdie(src_path, n->line, "say/journal needs text-id arg");
        }
        if (n->u.arr.items[1]->kind != N_STR) {
            cdie(src_path, n->u.arr.items[1]->line, "say/journal: text-id must be string");
        }
        /* Extra args are printf-style substitutions; compile each as an
           expr so paths like "caster.name" get turned into N_PATH. */
        for (size_t i = 2; i < n->u.arr.n; i++) {
            compile_expr(n->u.arr.items[i], src_path);
        }
    } else if (strcmp(op, "pause") == 0 || strcmp(op, "clw") == 0) {
        check_arity(n, src_path, 1, 1);
    } else if (strcmp(op, "delay") == 0) {
        check_arity(n, src_path, 2, 2);
        compile_expr(n->u.arr.items[1], src_path);
    } else if (strcmp(op, "set_flag") == 0) {
        check_arity(n, src_path, 3, 3);
        if (n->u.arr.items[1]->kind != N_STR) {
            cdie(src_path, n->u.arr.items[1]->line, "set_flag: flag must be string");
        }
        compile_expr(n->u.arr.items[2], src_path);
    } else if (strcmp(op, "give_gold") == 0 || strcmp(op, "take_gold") == 0) {
        check_arity(n, src_path, 2, 2);
        compile_expr(n->u.arr.items[1], src_path);
    } else if (strcmp(op, "give_item") == 0 || strcmp(op, "take_item") == 0) {
        check_arity(n, src_path, 2, 3);
        /* arg 1: item-id (int or string, leave). arg 2: optional count. */
        if (n->u.arr.n == 3) {
            compile_expr(n->u.arr.items[2], src_path);
        }
    } else if (strcmp(op, "set") == 0 || strcmp(op, "add") == 0 || strcmp(op, "sub") == 0) {
        check_arity(n, src_path, 3, 3);
        compile_string_to_path(n->u.arr.items[1]);
        compile_expr(n->u.arr.items[2], src_path);
    } else if (strcmp(op, "ask_target") == 0) {
        check_arity(n, src_path, 2, 2);
        if (n->u.arr.items[1]->kind != N_STR) {
            cdie(src_path, n->u.arr.items[1]->line, "ask_target: kind must be string");
        }
    } else if (strcmp(op, "require") == 0) {
        check_arity(n, src_path, 2, 2);
        compile_cond(n->u.arr.items[1], src_path);
    } else if (strcmp(op, "spawn_encounter") == 0) {
        /* encounter-spec: id (string) or {foe,n,mandatory} object. Both
           pass through uncompiled — the engine hook resolves at dispatch. */
    } else if (strcmp(op, "call") == 0) {
        check_arity(n, src_path, 2, 2);
        if (n->u.arr.items[1]->kind != N_STR) {
            cdie(src_path, n->u.arr.items[1]->line, "call: lib-id must be string");
        }
    } else {
        cdie(src_path, n->line, "unknown effect op");
    }
}

/* Is this node an array whose first element is one of our known ops? If so,
   return the compile function; otherwise NULL. The compile pass uses this
   as a trigger to rewrite an otherwise-anonymous array. */
typedef void (*compile_fn)(node *n, const char *src_path);

static compile_fn compile_kind_for(const node *n) {
    const char *op = arr_op(n);
    if (!op) {
        return NULL;
    }
    /* effects */
    if (strcmp(op, "seq") == 0 || strcmp(op, "if") == 0 ||
        strcmp(op, "say") == 0 || strcmp(op, "journal") == 0 ||
        strcmp(op, "pause") == 0 || strcmp(op, "delay") == 0 ||
        strcmp(op, "clw") == 0 ||
        strcmp(op, "set_flag") == 0 ||
        strcmp(op, "give_gold") == 0 || strcmp(op, "take_gold") == 0 ||
        strcmp(op, "give_item") == 0 || strcmp(op, "take_item") == 0 ||
        strcmp(op, "set") == 0 || strcmp(op, "add") == 0 || strcmp(op, "sub") == 0 ||
        strcmp(op, "ask_target") == 0 || strcmp(op, "require") == 0 ||
        strcmp(op, "spawn_encounter") == 0 || strcmp(op, "call") == 0) {
        return compile_effect;
    }
    if (strcmp(op, "and") == 0 || strcmp(op, "or") == 0 || strcmp(op, "not") == 0 ||
        strcmp(op, "flag_eq") == 0 || strcmp(op, "has_item") == 0 ||
        strcmp(op, "gold_gte") == 0 || strcmp(op, "in_dungeon") == 0 ||
        strcmp(op, "in_town") == 0 || strcmp(op, "in_wilderness") == 0 ||
        strcmp(op, "class_is") == 0 || strcmp(op, "alive") == 0 ||
        strcmp(op, "chance") == 0 ||
        strcmp(op, "eq") == 0 || strcmp(op, "lt") == 0 || strcmp(op, "gte") == 0) {
        return compile_cond;
    }
    if (strcmp(op, "+") == 0 || strcmp(op, "-") == 0 ||
        strcmp(op, "*") == 0 || strcmp(op, "/") == 0 ||
        strcmp(op, "min") == 0 || strcmp(op, "max") == 0 ||
        strcmp(op, "clamp") == 0 || strcmp(op, "rand") == 0 ||
        strcmp(op, "cond") == 0) {
        return compile_expr;
    }
    return NULL;
}

static void compile_walk(node *n, const char *src_path) {
    if (!n) {
        return;
    }
    if (n->kind == N_ARR) {
        compile_fn fn = compile_kind_for(n);
        if (fn) {
            fn(n, src_path);
            return;
        }
        for (size_t i = 0; i < n->u.arr.n; i++) {
            compile_walk(n->u.arr.items[i], src_path);
        }
    } else if (n->kind == N_OBJ) {
        for (size_t i = 0; i < n->u.obj.n; i++) {
            compile_walk(n->u.obj.items[i].val, src_path);
        }
    }
}

void compile_world_tree(node *root, const char *src_path) {
    compile_walk(root, src_path);
}

/* ---------------------------------------------------------------- */
/*  Environment                                                     */
/* ---------------------------------------------------------------- */

void env_init(env_t *e) {
    memset(e, 0, sizeof *e);
}

void env_free(env_t *e) {
    for (size_t i = 0; i < e->n_locals; i++) {
        free(e->locals[i].name);
    }
    free(e->locals);
    memset(e, 0, sizeof *e);
}

void env_set_local(env_t *e, const char *name, int32_t val) {
    for (size_t i = 0; i < e->n_locals; i++) {
        if (strcmp(e->locals[i].name, name) == 0) {
            e->locals[i].val = val;
            return;
        }
    }
    if (e->n_locals == e->cap_locals) {
        size_t nc = e->cap_locals ? e->cap_locals * 2 : 4;
        void *grown = realloc(e->locals, nc * sizeof *e->locals);
        if (!grown) {
            fprintf(stderr, "eval: oom growing locals\n");
            exit(1);
        }
        e->locals = grown;
        e->cap_locals = nc;
    }
    e->locals[e->n_locals].name = strdup(name);
    e->locals[e->n_locals].val = val;
    e->n_locals++;
}

void env_bind_caster_party(env_t *e, pm_t pm) {
    e->caster.kind = AB_PARTY;
    e->caster.u.party = pm;
}

void env_bind_combat(env_t *e, int noc, int *mh, int *deadc, const char *fn) {
    e->noc = noc;
    e->mh = mh;
    e->deadc = deadc;
    e->fn = fn;
}

/* ---------------------------------------------------------------- */
/*  Path get / set                                                  */
/* ---------------------------------------------------------------- */

static actor_bind *env_actor(env_t *e, int8_t actor, int line) {
    switch (actor) {
        case PA_CASTER:
            return &e->caster;
        case PA_TARGET:
            return &e->target;
        case PA_SPEAKER:
            return &e->speaker;
        case PA_SELF:
            return &e->self;
        default:
            rdie(line, "internal: not an actor path");
    }
}

static int32_t read_party_actor_field(pm_t pm, int field, int index, int line) {
    switch (field) {
        case PF_HP:
            return pm_hp(pm);
        case PF_H:
            return pm_h(pm);
        case PF_MA:
            return pm_ma(pm);
        case PF_WI:
            return pm.party->wi[pm.m];
        case PF_LV:
            return pm_lv(pm);
        case PF_WS:
            return pm.party->ws[pm.m];
        case PF_CH:
            return pm.party->ch[pm.m];
        case PF_AC:
            return pm.party->ar[pm.m];
        case PF_DEAD:
            return pm_dead(pm);
        case PF_CL:
            return pm_cl(pm);
        case PF_INVENTORY:
            if (index < 0 || index >= 17) {
                rdie(line, "inventory index out of range");
            }
            return pm.party->inventory[index];
        default:
            rdie(line, "unsupported actor field");
    }
}

static int32_t read_monster_field(env_t *e, int idx, int field, int line) {
    if (!e->mh) {
        rdie(line, "monster field read outside combat context");
    }
    if (idx < 1 || idx > e->noc) {
        rdie(line, "monster index out of range");
    }
    switch (field) {
        case PF_HP:
            return e->mh[idx];
        case PF_DEAD:
            return e->deadc[idx];
        default:
            rdie(line, "unsupported monster field");
    }
}

static int32_t read_actor_bound(const actor_bind *ab, const path_t *p, env_t *e) {
    if (ab->kind == AB_NONE) {
        rdie(p->line, "unbound actor");
    }
    if (ab->kind == AB_PARTY) {
        return read_party_actor_field(ab->u.party, p->field, p->index, p->line);
    }
    if (ab->kind == AB_MONSTER) {
        return read_monster_field(e, ab->u.mon_index, p->field, p->line);
    }
    rdie(p->line, "unknown actor kind");
}

static int32_t read_party_field(int field, int line) {
    struct party *p = &state.p[state.cpl];
    switch (field) {
        case PF_GOLD:
            return p->gold;
        case PF_X:
            return p->x;
        case PF_Y:
            return p->y;
        case PF_FIRESHIELD:
            return p->fireshield;
        case PF_BRACELETS:
            return p->bracelets;
        case PF_MASTERCROWN:
            return p->mastercrown;
        case PF_JAZZYFOOT:
            return p->jazzyfoot;
        case PF_SILVERKEY:
            return p->silverkey;
        default:
            rdie(line, "unsupported party field");
    }
}

static int32_t read_state_field(int field, int line) {
    switch (field) {
        case PF_BANISHED:
            return state.banished;
        default:
            rdie(line, "unsupported state field");
    }
}

static int32_t eval_path_get(const path_t *p, env_t *e) {
    switch (p->actor) {
        case PA_LOCAL: {
            for (size_t i = 0; i < e->n_locals; i++) {
                if (strcmp(e->locals[i].name, p->local) == 0) {
                    return e->locals[i].val;
                }
            }
            rdie(p->line, "unbound local");
        }
        case PA_CASTER:
        case PA_TARGET:
        case PA_SPEAKER:
        case PA_SELF:
            return read_actor_bound(env_actor(e, p->actor, p->line), p, e);
        case PA_PARTY:
            return read_party_field(p->field, p->line);
        case PA_STATE:
            return read_state_field(p->field, p->line);
        case PA_NPC:
            rdie(p->line, "npc path reads not implemented yet");
        default:
            rdie(p->line, "path has no actor");
    }
}

static void write_party_actor_field(pm_t pm, int field, int index, int32_t val, int line) {
    switch (field) {
        case PF_HP:
            pm.party->hp[pm.m] = val;
            return;
        case PF_H:
            pm.party->h[pm.m] = val;
            return;
        case PF_MA:
            pm.party->ma[pm.m] = val;
            return;
        case PF_WI:
            pm.party->wi[pm.m] = val;
            return;
        case PF_WS:
            pm.party->ws[pm.m] = val;
            return;
        case PF_CH:
            pm.party->ch[pm.m] = val;
            return;
        case PF_LV:
            pm.party->lv[pm.m] = val;
            return;
        case PF_DEAD:
            pm.party->dead[pm.m] = val;
            return;
        case PF_INVENTORY:
            if (index < 0 || index >= 17) {
                rdie(line, "inventory index out of range");
            }
            pm.party->inventory[index] = val;
            return;
        default:
            rdie(line, "unsupported actor field write");
    }
}

static void write_monster_field(env_t *e, int idx, int field, int32_t val, int line) {
    if (!e->mh) {
        rdie(line, "monster field write outside combat context");
    }
    if (idx < 1 || idx > e->noc) {
        rdie(line, "monster index out of range");
    }
    switch (field) {
        case PF_HP:
            e->mh[idx] = val;
            return;
        case PF_DEAD:
            e->deadc[idx] = val;
            return;
        default:
            rdie(line, "unsupported monster field write");
    }
}

static void write_actor_bound(actor_bind *ab, const path_t *p, int32_t val, env_t *e) {
    if (ab->kind == AB_NONE) {
        rdie(p->line, "unbound actor");
    }
    if (ab->kind == AB_PARTY) {
        write_party_actor_field(ab->u.party, p->field, p->index, val, p->line);
        return;
    }
    if (ab->kind == AB_MONSTER) {
        write_monster_field(e, ab->u.mon_index, p->field, val, p->line);
        return;
    }
    rdie(p->line, "unknown actor kind");
}

static void write_party_field(int field, int32_t val, int line) {
    struct party *p = &state.p[state.cpl];
    switch (field) {
        case PF_GOLD:
            p->gold = val;
            return;
        case PF_FIRESHIELD:
            p->fireshield = (unsigned char)val;
            return;
        case PF_BRACELETS:
            p->bracelets = (unsigned char)val;
            return;
        case PF_MASTERCROWN:
            p->mastercrown = (unsigned char)val;
            return;
        case PF_JAZZYFOOT:
            p->jazzyfoot = (unsigned char)val;
            return;
        case PF_SILVERKEY:
            p->silverkey = (unsigned char)val;
            return;
        default:
            rdie(line, "unsupported party field write");
    }
}

static void write_state_field(int field, int32_t val, int line) {
    switch (field) {
        case PF_BANISHED:
            state.banished = val;
            return;
        default:
            rdie(line, "unsupported state field write");
    }
}

static void eval_path_set(const path_t *p, env_t *e, int32_t val) {
    switch (p->actor) {
        case PA_LOCAL:
            env_set_local(e, p->local, val);
            return;
        case PA_CASTER:
        case PA_TARGET:
        case PA_SPEAKER:
        case PA_SELF:
            write_actor_bound(env_actor(e, p->actor, p->line), p, val, e);
            return;
        case PA_PARTY:
            write_party_field(p->field, val, p->line);
            return;
        case PA_STATE:
            write_state_field(p->field, val, p->line);
            return;
        case PA_NPC:
            rdie(p->line, "npc path writes not implemented yet");
        default:
            rdie(p->line, "path has no actor");
    }
}

/* ---------------------------------------------------------------- */
/*  eval_expr / eval_cond                                            */
/* ---------------------------------------------------------------- */

static int32_t item_id_of(const node *n) {
    if (n->kind == N_INT) {
        return n->u.iv;
    }
    /* Symbolic item refs (e.g. "ruby") are resolved at load time into the
       items table; for has_item we would look up by symbol here. Until the
       loader exposes sym->id, symbolic references are a load-time error. */
    rdie(n->line, "symbolic item id not yet resolvable");
}

int32_t eval_expr(const node *n, env_t *e) {
    if (!n) {
        rdie(0, "eval_expr: null node");
    }
    switch (n->kind) {
        case N_INT:
            return n->u.iv;
        case N_PATH:
            return eval_path_get(&n->u.path, e);
        case N_ARR:
            break;
        default:
            rdie(n->line, "expr must be int / path / array");
    }
    const char *op = arr_op(n);
    size_t nargs = n->u.arr.n;
    if (strcmp(op, "+") == 0) {
        int32_t s = 0;
        for (size_t i = 1; i < nargs; i++) {
            s += eval_expr(n->u.arr.items[i], e);
        }
        return s;
    }
    if (strcmp(op, "-") == 0) {
        return eval_expr(n->u.arr.items[1], e) - eval_expr(n->u.arr.items[2], e);
    }
    if (strcmp(op, "*") == 0) {
        int32_t s = 1;
        for (size_t i = 1; i < nargs; i++) {
            s *= eval_expr(n->u.arr.items[i], e);
        }
        return s;
    }
    if (strcmp(op, "/") == 0) {
        int32_t a = eval_expr(n->u.arr.items[1], e);
        int32_t b = eval_expr(n->u.arr.items[2], e);
        if (b == 0) {
            rdie(n->line, "divide by zero");
        }
        return a / b;
    }
    if (strcmp(op, "min") == 0) {
        int32_t m = INT32_MAX;
        for (size_t i = 1; i < nargs; i++) {
            int32_t v = eval_expr(n->u.arr.items[i], e);
            if (v < m) {
                m = v;
            }
        }
        return m;
    }
    if (strcmp(op, "max") == 0) {
        int32_t m = INT32_MIN;
        for (size_t i = 1; i < nargs; i++) {
            int32_t v = eval_expr(n->u.arr.items[i], e);
            if (v > m) {
                m = v;
            }
        }
        return m;
    }
    if (strcmp(op, "clamp") == 0) {
        int32_t v = eval_expr(n->u.arr.items[1], e);
        int32_t lo = eval_expr(n->u.arr.items[2], e);
        int32_t hi = eval_expr(n->u.arr.items[3], e);
        return v < lo ? lo : v > hi ? hi
                                    : v;
    }
    if (strcmp(op, "rand") == 0) {
        int32_t lo = eval_expr(n->u.arr.items[1], e);
        int32_t hi = eval_expr(n->u.arr.items[2], e);
        return ryl_random(lo, hi);
    }
    if (strcmp(op, "cond") == 0) {
        return eval_cond(n->u.arr.items[1], e)
                   ? eval_expr(n->u.arr.items[2], e)
                   : eval_expr(n->u.arr.items[3], e);
    }
    rdie(n->line, "unknown expr op");
}

int eval_cond(const node *n, env_t *e) {
    if (!n || n->kind != N_ARR) {
        rdie(n ? n->line : 0, "cond must be array");
    }
    const char *op = arr_op(n);
    size_t nargs = n->u.arr.n;
    if (strcmp(op, "and") == 0) {
        for (size_t i = 1; i < nargs; i++) {
            if (!eval_cond(n->u.arr.items[i], e)) {
                return 0;
            }
        }
        return 1;
    }
    if (strcmp(op, "or") == 0) {
        for (size_t i = 1; i < nargs; i++) {
            if (eval_cond(n->u.arr.items[i], e)) {
                return 1;
            }
        }
        return 0;
    }
    if (strcmp(op, "not") == 0) {
        return !eval_cond(n->u.arr.items[1], e);
    }
    if (strcmp(op, "flag_eq") == 0) {
        const char *flag = n->u.arr.items[1]->u.sv;
        int32_t v = eval_expr(n->u.arr.items[2], e);
        int32_t slot = res_flag_slot(flag);
        if (slot < 0) {
            rdie(n->line, "unknown flag");
        }
        return state.p[state.cpl].flag[slot] == v;
    }
    if (strcmp(op, "has_item") == 0) {
        int32_t id = item_id_of(n->u.arr.items[1]);
        int32_t count = (nargs == 3) ? eval_expr(n->u.arr.items[2], e) : 1;
        int have = 0;
        for (int i = 1; i < 17; i++) {
            if (state.p[state.cpl].inventory[i] == id) {
                have++;
            }
        }
        return have >= count;
    }
    if (strcmp(op, "gold_gte") == 0) {
        return state.p[state.cpl].gold >= eval_expr(n->u.arr.items[1], e);
    }
    if (strcmp(op, "in_dungeon") == 0) {
        /* No in-dungeon state bit exists today; the only caller that would
           exercise this is priest.banish, which hasn't migrated. When it
           does, either add a state.in_dungeon bit or route the check
           through the dungeon.c entry point. */
        return 0;
    }
    if (strcmp(op, "in_town") == 0) {
        return state.twn != 0;
    }
    if (strcmp(op, "in_wilderness") == 0) {
        return state.twn == 0;
    }
    if (strcmp(op, "class_is") == 0) {
        int32_t cl = eval_expr(n->u.arr.items[1], e);
        const char *name = n->u.arr.items[2]->u.sv;
        int want = pm_class_id(name);
        return cl == want;
    }
    if (strcmp(op, "alive") == 0) {
        /* arg 1 compiled as an expr/path reading .dead of the actor — not
           used yet; invert the dead-field read when it is. */
        int32_t d = eval_expr(n->u.arr.items[1], e);
        return !d;
    }
    if (strcmp(op, "chance") == 0) {
        int pct = (int)eval_expr(n->u.arr.items[1], e);
        return ryl_chance(pct);
    }
    if (strcmp(op, "eq") == 0) {
        return eval_expr(n->u.arr.items[1], e) == eval_expr(n->u.arr.items[2], e);
    }
    if (strcmp(op, "lt") == 0) {
        return eval_expr(n->u.arr.items[1], e) < eval_expr(n->u.arr.items[2], e);
    }
    if (strcmp(op, "gte") == 0) {
        return eval_expr(n->u.arr.items[1], e) >= eval_expr(n->u.arr.items[2], e);
    }
    rdie(n->line, "unknown cond op");
}

/* ---------------------------------------------------------------- */
/*  eval_effect                                                     */
/* ---------------------------------------------------------------- */

/* Resolve the string that a say/journal %s arg should render to. Only
   PATH args with field == PF_NAME are supported today; extending to
   arbitrary text-id or local-string args is straightforward but waits
   until a caller needs it. */
static const char *eval_name_string(const node *a, env_t *e) {
    if (a->kind != N_PATH) {
        rdie(a->line, "say/journal %s arg must be an actor .name path");
    }
    const path_t *p = &a->u.path;
    if (p->field != PF_NAME) {
        rdie(p->line, "say/journal %s arg must be .name");
    }
    actor_bind *ab;
    switch (p->actor) {
        case PA_CASTER:
            ab = &e->caster;
            break;
        case PA_TARGET:
            ab = &e->target;
            break;
        case PA_SPEAKER:
            ab = &e->speaker;
            break;
        case PA_SELF:
            ab = &e->self;
            break;
        default:
            rdie(p->line, "say/journal %s arg must be an actor path");
    }
    if (ab->kind == AB_PARTY) {
        return pm_name(ab->u.party);
    }
    if (ab->kind == AB_MONSTER) {
        if (!e->fn) {
            rdie(p->line, "monster .name read with no combat fn");
        }
        return e->fn;
    }
    rdie(p->line, "unbound actor for .name");
}

/* Render a say's format string into buf by walking %s / %d, pulling args
   from the trailing node list. Caller decides how many bytes to emit. */
static void say_render(const char *fmt, const node *n, env_t *e, char *buf, size_t buf_sz) {
    size_t o = 0;
    size_t ai = 2;
    while (*fmt && o + 1 < buf_sz) {
        if (fmt[0] == '%' && (fmt[1] == 's' || fmt[1] == 'd')) {
            char spec = fmt[1];
            fmt += 2;
            if (ai >= n->u.arr.n) {
                rdie(n->line, "say: too few args for format");
            }
            const node *a = n->u.arr.items[ai++];
            if (spec == 's') {
                const char *s = eval_name_string(a, e);
                size_t len = strlen(s);
                if (o + len >= buf_sz) {
                    len = buf_sz - 1 - o;
                }
                memcpy(buf + o, s, len);
                o += len;
            } else {
                int32_t v = eval_expr(a, e);
                char tmp[16];
                int wrote = snprintf(tmp, sizeof tmp, "%d", v);
                if (wrote < 0) {
                    wrote = 0;
                }
                size_t wn = (size_t)wrote;
                if (o + wn >= buf_sz) {
                    wn = buf_sz - 1 - o;
                }
                memcpy(buf + o, tmp, wn);
                o += wn;
            }
        } else {
            buf[o++] = *fmt++;
        }
    }
    buf[o] = 0;
    if (ai != n->u.arr.n) {
        rdie(n->line, "say: extra args beyond format specifiers");
    }
}

static void eval_say(const node *n, env_t *e) {
    const char *id = n->u.arr.items[1]->u.sv;
    const char *fmt = res_text(id);
    if (n->u.arr.n == 2) {
        tx0(fmt);
        return;
    }
    char buf[512];
    say_render(fmt, n, e, buf, sizeof buf);
    tx0(buf);
}

static void eval_set_flag(const node *n, env_t *e) {
    const char *flag = n->u.arr.items[1]->u.sv;
    int32_t v = eval_expr(n->u.arr.items[2], e);
    int32_t slot = res_flag_slot(flag);
    if (slot < 0) {
        rdie(n->line, "set_flag: unknown flag");
    }
    state.p[state.cpl].flag[slot] = (unsigned char)v;
}

/* ask_target "monster_alive": reproduces battle.c:424-441 byte-for-byte so
   the DSL-driven spell path emits the same menu as the hand-rolled one.
   party_alive / party_any land when a consumer asks for them. */
static void eval_ask_target(const node *n, env_t *e) {
    const char *kind = n->u.arr.items[1]->u.sv;
    if (strcmp(kind, "monster_alive") == 0) {
        if (!e->mh) {
            rdie(n->line, "ask_target monster_alive outside combat");
        }
        char str[80], d;
        int32_t i;
        while (1) {
            clw();
            tx0(res_text("battle.prompt.who"));
            for (i = 1; i < e->noc + 1; i++) {
                if (e->deadc[i]) {
                    strcpy(str, "Dead.");
                } else {
                    strcpy(str, "Alive.");
                }
                tx("%c. %-7s (%3d hp)\r\n", 64 + i, str, e->mh[i]);
            }
            d = key();
            d = toupper(d);
            if (d >= 'A' && d <= 64 + e->noc && !e->deadc[d - 64]) {
                break;
            }
        }
        e->target.kind = AB_MONSTER;
        e->target.u.mon_index = d - 64;
        return;
    }
    rdie(n->line, "ask_target: kind not implemented");
}

static void eval_seq(const node *n, env_t *e) {
    jmp_buf buf;
    if (e->seq_top >= (int)(sizeof e->seq_stack / sizeof e->seq_stack[0])) {
        rdie(n->line, "seq nested too deep");
    }
    e->seq_stack[e->seq_top++] = &buf;
    if (setjmp(buf) == 0) {
        for (size_t i = 1; i < n->u.arr.n; i++) {
            eval_effect(n->u.arr.items[i], e);
        }
    }
    e->seq_top--;
}

void eval_effect(const node *n, env_t *e) {
    if (!n || n->kind != N_ARR) {
        rdie(n ? n->line : 0, "effect must be array");
    }
    const char *op = arr_op(n);
    if (strcmp(op, "seq") == 0) {
        eval_seq(n, e);
        return;
    }
    if (strcmp(op, "if") == 0) {
        if (eval_cond(n->u.arr.items[1], e)) {
            eval_effect(n->u.arr.items[2], e);
        } else if (n->u.arr.n == 4) {
            eval_effect(n->u.arr.items[3], e);
        }
        return;
    }
    if (strcmp(op, "say") == 0) {
        eval_say(n, e);
        return;
    }
    if (strcmp(op, "journal") == 0) {
        char buf[512];
        const char *fmt = res_text(n->u.arr.items[1]->u.sv);
        if (n->u.arr.n == 2) {
            journal((char *)fmt);
        } else {
            say_render(fmt, n, e, buf, sizeof buf);
            journal(buf);
        }
        return;
    }
    if (strcmp(op, "pause") == 0) {
        ryl_pause();
        return;
    }
    if (strcmp(op, "delay") == 0) {
        int32_t ms = eval_expr(n->u.arr.items[1], e);
        if (ms > 0) {
            ryl_delay((unsigned)ms);
        }
        return;
    }
    if (strcmp(op, "clw") == 0) {
        clw();
        return;
    }
    if (strcmp(op, "set_flag") == 0) {
        eval_set_flag(n, e);
        return;
    }
    if (strcmp(op, "give_gold") == 0) {
        state.p[state.cpl].gold += eval_expr(n->u.arr.items[1], e);
        return;
    }
    if (strcmp(op, "take_gold") == 0) {
        state.p[state.cpl].gold -= eval_expr(n->u.arr.items[1], e);
        return;
    }
    if (strcmp(op, "give_item") == 0 || strcmp(op, "take_item") == 0) {
        rdie(n->line, "give_item/take_item not yet wired up");
    }
    if (strcmp(op, "set") == 0) {
        int32_t v = eval_expr(n->u.arr.items[2], e);
        eval_path_set(&n->u.arr.items[1]->u.path, e, v);
        return;
    }
    if (strcmp(op, "add") == 0) {
        const path_t *p = &n->u.arr.items[1]->u.path;
        int32_t v = eval_path_get(p, e) + eval_expr(n->u.arr.items[2], e);
        eval_path_set(p, e, v);
        return;
    }
    if (strcmp(op, "sub") == 0) {
        const path_t *p = &n->u.arr.items[1]->u.path;
        int32_t v = eval_path_get(p, e) - eval_expr(n->u.arr.items[2], e);
        eval_path_set(p, e, v);
        return;
    }
    if (strcmp(op, "ask_target") == 0) {
        eval_ask_target(n, e);
        return;
    }
    if (strcmp(op, "require") == 0) {
        if (!eval_cond(n->u.arr.items[1], e)) {
            if (e->seq_top == 0) {
                rdie(n->line, "require outside seq");
            }
            longjmp(*e->seq_stack[e->seq_top - 1], 1);
        }
        return;
    }
    if (strcmp(op, "spawn_encounter") == 0) {
        rdie(n->line, "spawn_encounter: engine hook not wired up");
    }
    if (strcmp(op, "call") == 0) {
        rdie(n->line, "call: lib subtree not yet consulted");
    }
    rdie(n->line, "unknown effect op");
}
