#include "json5.h"
#include "log.h"
#include "res.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

void log_msg(log_level lvl, const char *module, const char *fmt, ...) {
    (void)lvl;
    fprintf(stderr, "%s: ", module ? module : "log");
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
}

__attribute__((noreturn)) void res_die(const char *path, int line, const char *msg) {
    fprintf(stderr, "%s:%d: %s\n", path ? path : "<json>", line, msg ? msg : "error");
    exit(1);
}
