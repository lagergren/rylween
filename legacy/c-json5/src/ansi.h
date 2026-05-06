#ifndef ANSI_H__
#define ANSI_H__

/* ANSI CSI escape-sequence literals for the terminal-touching code.
   These are string macros, not enums: adjacent-string-literal
   concatenation lets call sites write
       tx(ANSI_FG_WHITE ANSI_BG_BLACK);
       snprintf(row, sz, ANSI_CSI "%dm%s", fg, glyph);
   without building the bytes by hand. No printf/sprintf required.

   For the integer SGR *number* (as stored in `color_tab[byte]` and
   returned by `res_color`), res.c keeps its own `ANSI_FG_*` enum —
   the two live side-by-side on purpose: this header is about the
   wire-format escape, res.c's enum is about the raw code. Keep both
   tables consistent if you ever renumber. */

#define ANSI_ESC "\x1b"
#define ANSI_CSI ANSI_ESC "["

#define ANSI_FG_BLACK   ANSI_CSI "30m"
#define ANSI_FG_RED     ANSI_CSI "31m"
#define ANSI_FG_GREEN   ANSI_CSI "32m"
#define ANSI_FG_YELLOW  ANSI_CSI "33m"
#define ANSI_FG_BLUE    ANSI_CSI "34m"
#define ANSI_FG_MAGENTA ANSI_CSI "35m"
#define ANSI_FG_CYAN    ANSI_CSI "36m"
#define ANSI_FG_WHITE   ANSI_CSI "37m"

#define ANSI_BG_BLACK   ANSI_CSI "40m"
#define ANSI_BG_RED     ANSI_CSI "41m"
#define ANSI_BG_GREEN   ANSI_CSI "42m"
#define ANSI_BG_YELLOW  ANSI_CSI "43m"
#define ANSI_BG_BLUE    ANSI_CSI "44m"
#define ANSI_BG_MAGENTA ANSI_CSI "45m"
#define ANSI_BG_CYAN    ANSI_CSI "46m"
#define ANSI_BG_WHITE   ANSI_CSI "47m"

#define ANSI_RESET ANSI_CSI "0m"

#endif
