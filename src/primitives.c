/* Thin wrappers over io->... Every terminal-touching primitive lives here. */

#include "io.h"
#include "ansi.h"
#include "compat.h"
#include "driv.h"
#include "log.h"
#include "party.h"
#include "primitives.h"
#include "res.h"

#include <ctype.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

void cl(void) {
    /* Clear screen + home cursor. Form feed (0x0c) used to do this on
       BBS terminals but on Mac Terminal / iTerm2 it's treated as LF
       only (no clear), and screen.ans's own ESC[2J doesn't home the
       cursor on those terminals — leaving the picture rendered at
       wherever the prior tx left the cursor. ESC[2J + ESC[H does
       both explicitly. */
    static const char clear_home[] = "\033[2J\033[1;1H";
    io->write(clear_home, sizeof clear_home - 1);
}

void c(uint8_t x, uint8_t y) {
    char pos[16];
    int32_t n;

    state.global_x = x;
    /* Use ";1H" (column 1, the leftmost column in 1-indexed CUP)
       rather than ";0H". ECMA-48 says param 0 defaults to 1, but
       terminal behavior varies — some implementations treat col=0
       as "keep current column", which leaves the cursor mid-row and
       breaks the relative ESC[<x>C jump that tx0/tx emits next. */
    n = snprintf(pos, sizeof pos, ANSI_CSI "%d;1H", y);
    io->write(pos, (size_t)n);
}

/* Move the cursor to a named region's anchor, looking up (x, y) from
   data/world.json5 screen:. Wraps c() so the call sites read as
   intent ("park at the text window") rather than literal coords. */
void screen_goto(screen_region r) {
    int x, y;
    res_screen_xy(r, &x, &y);
    c((uint8_t)x, (uint8_t)y);
}

/* Move to row `row` (0-indexed) of map_window's anchor x. Used by
   gfx.c::clr_map_window's per-row erase loop. */
void screen_goto_map_row(int row) {
    int x, y;
    res_screen_xy(SCR_MAP_WINDOW, &x, &y);
    c((uint8_t)x, (uint8_t)(y + row));
}

/* Write `s` to the terminal, prefixing each line with a CR + cursor-
   right jump (\r ESC[<global_x>C) so multi-line content stays inside
   the right text window regardless of where the cursor was when this
   helper was entered. tx0/tx route through this helper.

   The bug we're fixing: clw() positions the cursor at the left margin
   of the right text window via c(49, 6) (state.global_x = 49). A
   single ESC[49C at the head of the buffer only positions the FIRST
   line; the embedded \r\n's that separate subsequent lines return the
   cursor to column 1, and lines 2+ render at the left margin instead
   of inside the text window.

   Why CR + ESC[<x>C and not just ESC[<x>C: the relative cursor-right
   jump is interpreted from the current column. If the cursor isn't
   already at column 1 (e.g. a prior tx left it mid-row, or c(x,y)'s
   ;0H positioning didn't reset col), ESC[49C lands the cursor far
   right of the text window. The leading \r forces the cursor back to
   column 1, then ESC[<x>C jumps right by exactly x columns. Robust.

   Empty buffers still emit one CR + jump (matching the old behavior
   so call sites relying on the side effect of "cursor was positioned,
   no content written" — e.g. tx("") — continue to work). */
static uint32_t emit_with_col_jump(const char *s) {
    char jump[24];
    int32_t jn = snprintf(jump, sizeof jump, "\r" ANSI_CSI "%dC",
                          state.global_x);
    if (jn <= 0) {
        return 0;
    }
    if (!s || !*s) {
        io->write(jump, (size_t)jn);
        return 0;
    }
    size_t total = 0;
    const char *line = s;
    while (*line) {
        const char *nl = strchr(line, '\n');
        size_t len = nl ? (size_t)(nl - line + 1) : strlen(line);
        io->write(jump, (size_t)jn);
        io->write(line, len);
        total += len;
        if (!nl || !nl[1]) {
            break;
        }
        line = nl + 1;
    }
    return (uint32_t)total;
}

uint32_t tx(const char *fmtstr, ...) {
    char buffer[512];
    va_list var_args;
    int32_t n;

    va_start(var_args, fmtstr);
    n = vsnprintf(buffer, sizeof buffer, (char *)fmtstr, var_args);
    va_end(var_args);

    if (n <= 0) {
        return 0;
    }
    return emit_with_col_jump(buffer);
}

/* tx0: emit a ready-made string as data, not format. Same per-line
   cursor-right prefix as tx(), but skips vsnprintf — so the payload
   is not bounded by tx()'s 512-byte buffer and % bytes in the payload
   are safe. Use for res_text(), pronoun(), object(), pre-built row
   buffers, and any literal without format specifiers. */
uint32_t tx0(const char *s) {
    return emit_with_col_jump(s);
}

/* Fatal runtime error from the primitives layer. Prints "ryl: <msg>" to
   stderr and exits (1). Used by tx_fmt for unbound or unknown
   placeholders — dev-time bugs, same spirit as res_die for loader
   diagnostics. */
void ryl_die(const char *msg) {
    LOG_E("ryl", "%s", msg ? msg : "(no message)");
    exit(1);
}

/* bind_expand: placeholder substitution shared by tx_fmt and script.c.
   See primitives.h for the full contract. */
void bind_expand(const char *src, const ryl_bind *bindings,
                 char *out, size_t out_sz, const char *ctx) {
    char diag[256];
    size_t o = 0;

    if (out_sz == 0) {
        snprintf(diag, sizeof diag, "bind_expand: zero-size buffer in '%s'", ctx);
        ryl_die(diag);
    }
    for (size_t i = 0; src[i];) {
        char ch = src[i];
        if (ch == '{' && src[i + 1] == '{') {
            if (o + 1 >= out_sz) {
                snprintf(diag, sizeof diag, "bind_expand: buffer overflow in '%s'", ctx);
                ryl_die(diag);
            }
            out[o++] = '{';
            i += 2;
            continue;
        }
        if (ch != '{') {
            if (o + 1 >= out_sz) {
                snprintf(diag, sizeof diag, "bind_expand: buffer overflow in '%s'", ctx);
                ryl_die(diag);
            }
            out[o++] = ch;
            i++;
            continue;
        }
        /* Scan {ident} */
        size_t name_start = i + 1;
        size_t name_end = name_start;
        while (src[name_end] && src[name_end] != '}') {
            name_end++;
        }
        if (src[name_end] != '}') {
            snprintf(diag, sizeof diag, "bind_expand: unterminated '{' in '%s'", ctx);
            ryl_die(diag);
        }
        size_t name_len = name_end - name_start;
        if (name_len == 0 || name_len >= 64) {
            snprintf(diag, sizeof diag, "bind_expand: bad placeholder in '%s'", ctx);
            ryl_die(diag);
        }
        char name[64];
        memcpy(name, src + name_start, name_len);
        name[name_len] = 0;

        const char *val = NULL;
        for (const ryl_bind *b = bindings; b && b->name; b++) {
            if (strcmp(b->name, name) == 0) {
                if (!b->value) {
                    snprintf(diag, sizeof diag,
                             "bind_expand: NULL value for '{%s}' in '%s'", name, ctx);
                    ryl_die(diag);
                }
                val = b->value;
                break;
            }
        }
        /* Engine-provided bindings — always available in every tx_fmt /
           script emit without the caller having to thread them. Caller
           bindings above take precedence (matched first). Kept to the
           data knobs that shape UI prompts: party_max / inventory_max.
           Per-placeholder static buffer — values are memcpy'd into
           `out` immediately after, so reuse across iterations is safe.

           Typed forms: {cost:<itemkey>} resolves the symbolic item key
           to its id via res_item_id, then emits res_item(id)->cost as
           decimal. Used by shop menus so the price defined in the
           items shards is the single source of truth — no per-item
           let-binding prelude in the script. */
        char engine_buf[16];
        if (!val) {
            if (strcmp(name, "party_max") == 0) {
                snprintf(engine_buf, sizeof engine_buf, "%d", res_party_member_max());
                val = engine_buf;
            } else if (strcmp(name, "inventory_max") == 0) {
                snprintf(engine_buf, sizeof engine_buf, "%d", res_inventory_slot_max());
                val = engine_buf;
            } else if (strncmp(name, "cost:", 5) == 0) {
                int32_t id = res_item_id(name + 5);
                if (id < 0) {
                    snprintf(diag, sizeof diag,
                             "bind_expand: unknown item key '{%s}' in '%s'",
                             name, ctx);
                    ryl_die(diag);
                }
                const ryl_item *it = res_item((int)id);
                if (!it) {
                    snprintf(diag, sizeof diag,
                             "bind_expand: item id %d has no record (from '{%s}' in '%s')",
                             (int)id, name, ctx);
                    ryl_die(diag);
                }
                snprintf(engine_buf, sizeof engine_buf, "%d", it->cost);
                val = engine_buf;
            }
        }
        if (!val) {
            snprintf(diag, sizeof diag,
                     "bind_expand: unbound '{%s}' in '%s'", name, ctx);
            ryl_die(diag);
        }
        size_t vlen = strlen(val);
        if (o + vlen >= out_sz) {
            snprintf(diag, sizeof diag, "bind_expand: buffer overflow in '%s'", ctx);
            ryl_die(diag);
        }
        memcpy(out + o, val, vlen);
        o += vlen;
        i = name_end + 1;
    }
    out[o] = 0;
}

/* tx_fmt: expand {placeholder} tokens in a res_text record against the
   caller's binding list, emit as one tx0(). See primitives.h for the
   full contract. Expansion buffer is stack-local (2KB); no site in the
   current corpus comes close. */
uint32_t tx_fmt(const char *id, const ryl_bind *bindings) {
    char buf[2048];
    bind_expand(res_text(id), bindings, buf, sizeof buf, id);
    return tx0(buf);
}

/* §23.I: walk a string-array of text ids and emit each as a paused
   screen (clw + tx_fmt + ryl_pause). See primitives.h for the
   contract; `bindings` is forwarded to tx_fmt verbatim so any screen
   that has a `{placeholder}` is resolved against the same set. */
void tx_narrate(const char *acts_path, const ryl_bind *bindings) {
    int n = res_str_arr_count(acts_path);
    for (int i = 0; i < n; i++) {
        const char *id = res_str_arr_at(acts_path, i);
        if (!id) {
            continue;
        }
        clw();
        tx_fmt(id, bindings);
        ryl_pause();
    }
}

int ryl_menu(const char *screen_text_id, const ryl_menu_opt *opts) {
    if (screen_text_id) {
        tx0(res_text(screen_text_id));
    }
    char ch = (char)toupper((unsigned char)key());
    for (const ryl_menu_opt *o = opts; o->letter; o++) {
        if (o->letter == ch) {
            return o->result;
        }
    }
    const ryl_menu_opt *s = opts;
    while (s->letter) {
        s++;
    }
    return s->result;
}

void clw(void) {
    int32_t i;

    screen_goto(SCR_TEXT_WINDOW);
    for (i = 1; i < 12; i++) {
        tx("                          \r\n");
    }
    screen_goto(SCR_TEXT_WINDOW);
}

static uint8_t carrier(void) {
    return io->carrier_ok() ? 1 : 0;
}

void clean_exit(void) {
    tx(ANSI_FG_BLACK ANSI_ESC "[ p" ANSI_FG_WHITE);
    if (io && io->shutdown) {
        io->shutdown();
    }
    /* res_shutdown is atexit-registered by res_load_world; do not free
       the world tree / flags_tab here, so the snapshot_write atexit
       handler (registered later → runs earlier in LIFO) can still
       read them. Without this, a snapshot_write that walks
       state.p[].flag[] for non-zero entries crashes the emit via
       freed-pointer lookups and surfaces as `flags=?=?`. */
    exit(0);
}

/* get_str: read one line of input into the caller's buffer. lg is the
   max input length (excluding the null terminator), matching the old
   pointer-returning API. We cap the read at min(lg+1, out_sz) so a
   caller who under-sizes out[] still can't overflow. */
void get_str(uint8_t lg, char *out, size_t out_sz) {
    size_t cap = (size_t)lg + 1;

    if (!carrier()) {
        clean_exit();
    }
    if (out_sz == 0) {
        return;
    }
    if (cap > out_sz) {
        cap = out_sz;
    }
    io->getline(out, cap);
    if (out[0] == '#') {
        clean_exit();
    }
    if (!carrier()) {
        clean_exit();
    }
}

uint8_t hot_key(void) {
    int32_t ch = io->getch();
    if (ch < 0) {
        clean_exit();
    }
    return (toupper(ch) == 'Y') ? 1 : 0;
}

char key(void) {
    int32_t ch;

    if (!carrier()) {
        clean_exit();
    }
    screen_goto(SCR_TOP_LEFT);
    tx(ANSI_FG_BLACK);
    ch = io->getch();
    screen_goto(SCR_TEXT_WINDOW);
    tx(ANSI_FG_WHITE);

    if (ch < 0) {
        clean_exit();
    }
    if (ch == '#') {
        clean_exit();
    }
    if (!carrier()) {
        clean_exit();
    }

    /* Local sysop backdoors — name-keyed dispatch lifted to
       data/world.json5 secrets:. The engine resolves the entry by
       global player name and yields a small action record; the
       switch below is one branch per supported action kind. */
    secret_action act;
    if (res_secrets_check_key(session_player_name(), (char)ch, &act)) {
        switch (act.kind) {
            case SECRET_ADD_TIME_LEFT:
                pa_add_time_left(act.amount);
                break;
            case SECRET_ADD_GOLD:
                pa_add_gold(act.amount);
                break;
            case SECRET_SET_INVENTORY:
                pa_set_inventory(act.slot, act.item_id);
                break;
            default:
                break;
        }
        if (act.refresh_status) {
            status_line();
        }
    }
    return (char)ch;
}

void ryl_pause(void) {
    int32_t ch;

    int n = res_pause_verb_count();
    const char *verb = (n > 0) ? res_pause_verb(ryl_random(0, n - 1)) : NULL;
    tx("(%s RETURN key)\r\n", verb ? verb : "Press");
    screen_goto(SCR_TOP_LEFT);
    tx(ANSI_FG_BLACK);
    while (1) {
        ch = io->getch();
        if (ch < 0) {
            clean_exit();
        }
        if (ch == 13 || ch == 10) {
            break;
        }
    }
    tx(ANSI_FG_WHITE);
    screen_goto(SCR_TEXT_WINDOW);
}

void disp_text(char *namn) {
    /* Home the cursor before dumping the .ans. .ans files use relative
       cursor moves (ESC[<n>C) and rely on starting at (1,1); their
       internal ESC[2J doesn't home the cursor on Mac Terminal /
       iTerm2 so the picture would render shifted to wherever the
       previous tx left the cursor. */
    static const char home[] = "\033[1;1H";
    io->write(home, sizeof home - 1);
    io->dump_file(namn);
}

void render_at(const char *path, uint8_t x, uint8_t y) {
    FILE *fh = fopen(path, "rb");
    if (!fh) {
        return;
    }

    char line[512];
    int32_t row = 0;
    while (fgets(line, sizeof line, fh)) {
        size_t n = strlen(line);
        while (n && (line[n - 1] == '\n' || line[n - 1] == '\r')) {
            line[--n] = 0;
        }
        char pos[16];
        int32_t pn = snprintf(pos, sizeof pos, ANSI_CSI "%d;%dH", y + row, x);
        if (pn > 0) {
            io->write(pos, (size_t)pn);
        }
        if (n) {
            io->write(line, n);
        }
        row++;
    }
    io->write(ANSI_RESET, 4);
    fclose(fh);
}
