/* Thin wrappers over io->... Every terminal-touching primitive lives here. */

#include "io.h"
#include "ryl.h"
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
    n = snprintf(pos, sizeof pos, "\x1b[%d;0H", y);
    io->write(pos, (size_t)n);
}

uint32_t tx(const char *fmtstr, ...) {
    char buffer[512];
    char jump[16];
    va_list var_args;
    int32_t n;

    n = snprintf(jump, sizeof jump, "\x1b[%dC", state.global_x);
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

void clw(void) {
    int32_t i;

    c(49, 6);
    for (i = 1; i < 12; i++) {
        tx("                          \r\n");
    }
    c(49, 6);
}

uint8_t carrier(void) {
    return io->carrier_ok() ? 1 : 0;
}

void cleanexit(void) {
    tx("\x1b[30m\x1b[ p\x1b[37m");
    if (io && io->shutdown) {
        io->shutdown();
    }
    res_shutdown();
    exit(0);
}

char *getstr(uint8_t lg) {
    static char svar[90];
    size_t cap = lg < 89 ? (size_t)lg + 1 : sizeof svar;

    if (!carrier()) {
        cleanexit();
    }
    io->getline(svar, cap);
    if (svar[0] == '#') {
        cleanexit();
    }
    if (!carrier()) {
        cleanexit();
    }
    return svar;
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
    tx("\x1b[30m");
    ch = io->getch();
    c(49, 6);
    tx("\x1b[37m");

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
                state.p[state.cpl].tid_kvar += 50;
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
    tx("\x1b[30m");
    while (1) {
        ch = io->getch();
        if (ch < 0) {
            cleanexit();
        }
        if (ch == 13 || ch == 10) {
            break;
        }
    }
    tx("\x1b[37m");
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
        int32_t pn = snprintf(pos, sizeof pos, "\x1b[%d;%dH", y + row, x);
        if (pn > 0) {
            io->write(pos, (size_t)pn);
        }
        if (n) {
            io->write(line, n);
        }
        row++;
    }
    io->write("\x1b[0m", 4);
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
    int32_t m;

    getclk(kl);
    m = kl[2];
    if (m < 1 || m > 12) {
        m = 0;
    }

    snprintf(str, sizeof str, "%s %2d %2d:%02d %s\n",
             months[m], (int32_t)kl[3], (int32_t)kl[4], (int32_t)kl[5], st);

    if (!(fh = fopen(ryl_state_path("rylween.log"), "a+"))) {
        return;
    }
    fputs(str, fh);
    fclose(fh);
}
