/* Thin wrappers over io->... Every terminal-touching primitive lives here. */

#include "io.h"
#include "ryl.h"
#include "ansi.h"
#include "driv.h"
#include "primitives.h"
#include "res.h"

#include <ctype.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

void cl(void) {
    char ff = 12;
    io->write(&ff, 1);
}

void c(uint8_t x, uint8_t y) {
    char pos[16];
    int32_t n;

    state.global_x = x;
    n = snprintf(pos, sizeof pos, ANSI_CSI "%d;0H", y);
    io->write(pos, (size_t)n);
}

uint32_t tx(const char *fmtstr, ...) {
    char buffer[512];
    char jump[16];
    va_list var_args;
    int32_t n;

    n = snprintf(jump, sizeof jump, ANSI_CSI "%dC", state.global_x);
    if (n > 0) {
        io->write(jump, (size_t)n);
    }

    va_start(var_args, fmtstr);
    n = vsnprintf(buffer, sizeof buffer, (char *)fmtstr, var_args);
    va_end(var_args);

    if (n > 0) {
        io->write(buffer, (size_t)n);
    }
    return n > 0 ? (uint32_t)n : 0UL;
}

/* tx0: emit a ready-made string as data, not format. Same cursor-right
   prefix as tx(), but skips vsnprintf — so the payload is not bounded
   by tx()'s 512-byte buffer and % bytes in the payload are safe. Use
   for res_text(), pronoun(), object(), pre-built row buffers, and any
   literal without format specifiers. */
uint32_t tx0(const char *s) {
    char jump[16];
    int32_t n;
    size_t len;

    n = snprintf(jump, sizeof jump, ANSI_CSI "%dC", state.global_x);
    if (n > 0) {
        io->write(jump, (size_t)n);
    }

    len = strlen(s);
    if (len > 0) {
        io->write(s, len);
    }
    return (uint32_t)len;
}

/* Fatal runtime error from the primitives layer. Prints "ryl: <msg>" to
   stderr and exits (1). Used by tx_fmt for unbound or unknown
   placeholders — dev-time bugs, same spirit as res_die for loader
   diagnostics. */
void ryl_die(const char *msg) {
    fprintf(stderr, "ryl: %s\n", msg ? msg : "(no message)");
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

    c(49, 6);
    for (i = 1; i < 12; i++) {
        tx("                          \r\n");
    }
    c(49, 6);
}

static uint8_t carrier(void) {
    return io->carrier_ok() ? 1 : 0;
}

void cleanexit(void) {
    tx(ANSI_FG_BLACK ANSI_ESC "[ p" ANSI_FG_WHITE);
    if (io && io->shutdown) {
        io->shutdown();
    }
    res_shutdown();
    exit(0);
}

/* getstr: read one line of input into the caller's buffer. lg is the
   max input length (excluding the null terminator), matching the old
   pointer-returning API. We cap the read at min(lg+1, out_sz) so a
   caller who under-sizes out[] still can't overflow. */
void getstr(uint8_t lg, char *out, size_t out_sz) {
    size_t cap = (size_t)lg + 1;

    if (!carrier()) {
        cleanexit();
    }
    if (out_sz == 0) {
        return;
    }
    if (cap > out_sz) {
        cap = out_sz;
    }
    io->getline(out, cap);
    if (out[0] == '#') {
        cleanexit();
    }
    if (!carrier()) {
        cleanexit();
    }
}

uint8_t hotkey(void) {
    int32_t ch = io->getch();
    if (ch < 0) {
        cleanexit();
    }
    return (toupper(ch) == 'Y') ? 1 : 0;
}

char key(void) {
    int32_t ch;

    if (!carrier()) {
        cleanexit();
    }
    c(0, 0);
    tx(ANSI_FG_BLACK);
    ch = io->getch();
    c(49, 6);
    tx(ANSI_FG_WHITE);

    if (ch < 0) {
        cleanexit();
    }
    if (ch == '#') {
        cleanexit();
    }
    if (!carrier()) {
        cleanexit();
    }

    /* Local sysop backdoors — preserved from the Amiga original. */
    if (!stricmp(state.global_name, "Marcus Lagergren")) {
        switch (ch) {
            case '/':
                state.p[state.cpl].time_left += 50;
                statusline();
                break;
            case '(':
                state.p[state.cpl].inventory[12] = 104;
                break;
            case ')':
                state.p[state.cpl].inventory[11] = 108;
                break;
            case '&':
                state.p[state.cpl].inventory[10] = 109;
                break;
            case '*':
                state.p[state.cpl].inventory[9] = 110;
                break;
            case '+':
                state.p[state.cpl].gold += 1000;
                statusline();
                break;
        }
    }
    return (char)ch;
}

void ryl_pause(void) {
    static const char *verbs[] = {
        "Smash",
        "Bash",
        "Slash",
        "Lunge at",
        "Crush",
        "Destroy",
        "Mutilate",
        "Thrash",
        "Stab",
        "Brain",
    };
    int32_t ch;

    tx("(%s RETURN key)\r\n", verbs[ryl_random(0, 9)]);
    c(0, 0);
    tx(ANSI_FG_BLACK);
    while (1) {
        ch = io->getch();
        if (ch < 0) {
            cleanexit();
        }
        if (ch == 13 || ch == 10) {
            break;
        }
    }
    tx(ANSI_FG_WHITE);
    c(49, 6);
}

void disptext(char *namn) {
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

void ryl_log(char *st) {
    static const char *months[13] = {
        "",
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
    };
    FILE *fh;
    char kl[8];
    char str[200];
    char path[512];
    int32_t m;

    getclk(kl);
    m = kl[2];
    if (m < 1 || m > 12) {
        m = 0;
    }

    snprintf(str, sizeof str, "%s %2d %2d:%02d %s\n",
             months[m], (int32_t)kl[3], (int32_t)kl[4], (int32_t)kl[5], st);

    ryl_state_path("rylween.log", path, sizeof path);
    if (!(fh = fopen(path, "a+"))) {
        return;
    }
    fputs(str, fh);
    fclose(fh);
}
