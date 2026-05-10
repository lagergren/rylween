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

void res_ansi_path(const char *leaf, char *out, size_t out_sz) {
    snprintf(out, out_sz, "data/ansi/%s", leaf ? leaf : "");
}

void res_map_path(char *out, size_t out_sz) {
    snprintf(out, out_sz, "data/map.dat");
}

void res_data_path(const char *leaf, char *out, size_t out_sz) {
    snprintf(out, out_sz, "data/%s", leaf ? leaf : "");
}
