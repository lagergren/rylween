#include "io.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>

static struct termios saved_termios;
static int32_t termios_saved = 0;

static void tty_write(const char *buf, size_t n) {
    size_t off = 0;
    while (off < n) {
        ssize_t w = write(STDOUT_FILENO, buf + off, n - off);
        if (w < 0) {
            if (errno == EINTR) {
                continue;
            }
            return;
        }
        off += (size_t) w;
    }
}

static int32_t tty_getch(void) {
    unsigned char ch;
    while (1) {
        ssize_t r = read(STDIN_FILENO, &ch, 1);
        if (r == 1) {
            return ch;
        }
        if (r == 0) {
            return -1;
        }
        if (errno == EINTR) {
            continue;
        }
        return -1;
    }
}

static int32_t tty_getline(char *buf, size_t n) {
    /* Temporarily restore cooked mode so the user gets normal line editing. */
    struct termios raw = saved_termios;
    int32_t have_raw = 0;
    if (termios_saved) {
        if (tcgetattr(STDIN_FILENO, &raw) == 0) {
            have_raw = 1;
            tcsetattr(STDIN_FILENO, TCSANOW, &saved_termios);
        }
    }

    if (!fgets(buf, (int32_t) n, stdin)) {
        buf[0] = 0;
        if (have_raw) {
            tcsetattr(STDIN_FILENO, TCSANOW, &raw);
        }
        return 0;
    }

    size_t len = strlen(buf);
    while (len && (buf[len - 1] == '\n' || buf[len - 1] == '\r')) {
        buf[--len] = 0;
    }

    if (have_raw) {
        tcsetattr(STDIN_FILENO, TCSANOW, &raw);
    }

    return (int32_t) len;
}

static int32_t tty_carrier_ok(void) {
    return 1;
}

static void tty_dump_file(const char *path) {
    int32_t fd = open(path, O_RDONLY);
    if (fd < 0) {
        return;
    }
    char buf[4096];
    while (1) {
        ssize_t r = read(fd, buf, sizeof buf);
        if (r < 0) {
            if (errno == EINTR) {
                continue;
            }
            break;
        }
        if (r == 0) {
            break;
        }
        tty_write(buf, (size_t) r);
    }
    close(fd);
}

static void tty_restore(void) {
    if (termios_saved) {
        tcsetattr(STDIN_FILENO, TCSANOW, &saved_termios);
    }
}

static void tty_shutdown(void) {
    tty_restore();
}

static io_backend tty_backend = {
    "tty",
    tty_write,
    tty_getch,
    tty_getline,
    tty_carrier_ok,
    tty_dump_file,
    tty_shutdown,
};

io_backend *io = NULL;

void io_init_tty(void) {
    struct termios raw;

    setvbuf(stdout, NULL, _IONBF, 0);

    if (isatty(STDIN_FILENO) && tcgetattr(STDIN_FILENO, &saved_termios) == 0) {
        termios_saved = 1;
        raw = saved_termios;
        raw.c_lflag &= ~(ICANON | ECHO);
        raw.c_cc[VMIN] = 1;
        raw.c_cc[VTIME] = 0;
        tcsetattr(STDIN_FILENO, TCSANOW, &raw);
        atexit(tty_restore);
    }

    io = &tty_backend;
}
