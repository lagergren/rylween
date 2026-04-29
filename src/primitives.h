#ifndef PRIMITIVES_H__
#define PRIMITIVES_H__

#include <stddef.h>
#include <stdint.h>

#include "res.h"

/* Terminal primitives — thin wrappers over the io_ops vtable selected at
   boot (io_tty for local play). Every call-site used to reach these via
   the ryl.h umbrella; splitting them out makes the dependency explicit. */

void get_str(uint8_t, char *, size_t);
uint32_t tx(const char *, ...);
uint32_t tx0(const char *);

/* tx_fmt: emit a res_text() record with {placeholder} tokens resolved at
   call time via a compound-literal binding list (port-plan §16.3). Kills
   the `*_pre / inline tx() / *_post` trio that forced text layout to
   leak into C control flow. Bindings terminate on a {NULL, ...} entry.
   Unknown placeholder id or NULL-valued binding is fatal (res_die).
   Emits a single CSI-right prefix like tx0 — NOT one per original call —
   so merging a 3-call site into one tx_fmt removes intermediate CSI
   sequences from the byte stream (benign for terminal output, shifts
   replay baselines; see §16.8). */
typedef struct {
    const char *name;  /* placeholder ident; NULL sentinel terminates */
    const char *value; /* substitution string; never NULL for non-sentinel */
} ryl_bind;

uint32_t tx_fmt(const char *id, const ryl_bind *bindings);

/* §23.I: multi-screen narration helper. `acts_path` is a dotted path
   (e.g. "ydarg.narration.conf_act1") to a string array of text ids.
   For each entry: clw, tx_fmt(id, bindings), ryl_pause. No leading or
   trailing clw — call sites stay in charge of bracketing the run with
   any pre-narration state and the post-narration jump (encounter,
   save, etc.). `bindings` may be NULL; pass-through to tx_fmt for any
   per-screen `{placeholder}` interpolation. */
void tx_narrate(const char *acts_path, const ryl_bind *bindings);

/* §16.1 menu helper. Emits a res_text() screen, reads one keypress via
   key(), upper-cases it, and returns the `result` of the first opts[]
   entry whose `letter` matches. When nothing matches (or the user hit
   Esc/Bye/etc.), the sentinel entry's `result` is returned as the
   wildcard default. `opts` is terminated by a {0, 0} entry — whose
   `result` field doubles as that wildcard. Passing a NULL screen id
   skips the screen emission so callers can stack it after an earlier
   tx0/tx_fmt that laid down the prompt text. */
typedef struct {
    char letter;
    int result;
} ryl_menu_opt;

int ryl_menu(const char *screen_text_id, const ryl_menu_opt *opts);

/* Expand {ident} / {{ placeholders in `src` against `bindings`, writing
   up to out_sz-1 bytes + NUL into `out`. Same syntax as tx_fmt; same
   fatal-error semantics (ryl_die on unterminated '{', oversize ident,
   unbound / NULL binding, or output overflow). `ctx` is embedded in the
   diagnostic so callers can surface which record or script was being
   expanded. Used by tx_fmt (source from res_text) and by script.c's
   journal op (source is a literal string from the script). */
void bind_expand(const char *src, const ryl_bind *bindings,
                 char *out, size_t out_sz, const char *ctx);
void clean_exit(void);
__attribute__((noreturn)) void ryl_die(const char *msg);
void cl(void);
void c(uint8_t, uint8_t);
void screen_goto(screen_region r);
void screen_goto_map_row(int row);
void clw(void);
void ryl_pause(void);
uint8_t hot_key(void);
char key(void);
void render_at(const char *, uint8_t, uint8_t);
void disp_text(char *);

#endif
