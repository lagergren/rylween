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
void cleanexit(void);
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
