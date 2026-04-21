#ifndef IO_H__
#define IO_H__

#include <stddef.h>
#include <stdint.h>

typedef struct io_backend {
    const char *name;
    void (*write)(const char *buf, size_t n);
    int32_t (*getch)(void);
    int32_t (*getline)(char *buf, size_t n);
    int32_t (*carrier_ok)(void);
    void (*dump_file)(const char *path);
    void (*shutdown)(void);
} io_backend;

extern io_backend *io;

void io_init_tty(void);

#endif
