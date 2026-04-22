#ifndef PRIMITIVES_H__
#define PRIMITIVES_H__

#include <stddef.h>
#include <stdint.h>

/* Terminal primitives — thin wrappers over the io_ops vtable selected at
   boot (io_tty for local play). Every call-site used to reach these via
   the ryl.h umbrella; splitting them out makes the dependency explicit. */

void getstr(uint8_t, char *, size_t);
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
void cleanexit(void);
__attribute__((noreturn)) void ryl_die(const char *msg);
void cl(void);
void c(uint8_t, uint8_t);
void clw(void);
void ryl_pause(void);
uint8_t hotkey(void);
char key(void);
void render_at(const char *, uint8_t, uint8_t);
void ryl_log(char *);
void disptext(char *);

#endif
