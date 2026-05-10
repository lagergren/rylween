#ifndef RYLWEEN_NATIVE_JSON5_RES_H__
#define RYLWEEN_NATIVE_JSON5_RES_H__

#include <stddef.h>

void res_die(const char *path, int line, const char *msg)
    __attribute__((noreturn));

void res_ansi_path(const char *leaf, char *out, size_t out_sz);
void res_map_path(char *out, size_t out_sz);
void res_data_path(const char *leaf, char *out, size_t out_sz);

#endif
