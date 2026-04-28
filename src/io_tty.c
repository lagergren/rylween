#include "io.h"

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>

volatile sig_atomic_t io_winch_pending = 0;

static struct termios saved_termios;
static int32_t termios_saved = 0;

static void tty_winch_handler(int sig) {
    (void)sig;
    io_winch_pending = 1;
}

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
        off += (size_t)w;
    }
}

/* Read one byte with a short timeout. Returns 1 on success, 0 on
   timeout/EOF/error. Used to peek the tail of a CSI escape sequence
   without blocking forever when the user pressed ESC alone. */
static int peek_byte_timed(int timeout_ms, unsigned char *out) {
    struct pollfd pfd;
    pfd.fd = STDIN_FILENO;
    pfd.events = POLLIN;
    int r;
    do {
        r = poll(&pfd, 1, timeout_ms);
    } while (r < 0 && errno == EINTR);
    if (r <= 0) {
        return 0;
    }
    ssize_t rr = read(STDIN_FILENO, out, 1);
    return (rr == 1) ? 1 : 0;
}

static int32_t tty_getch(void) {
    unsigned char ch;
    while (1) {
        ssize_t r = read(STDIN_FILENO, &ch, 1);
        if (r == 1) {
            /* Translate ANSI arrow keys (ESC [ A/B/C/D) into the
               numpad digits the game already understands as movement.
               Piped replays use the digit literals directly, so the
               escape-sequence path never fires there. */
            if (ch == 27) {
                unsigned char b1, b2;
                if (peek_byte_timed(50, &b1) && b1 == '[' &&
                    peek_byte_timed(50, &b2)) {
                    switch (b2) {
                        case 'A':
                            return '8';
                        case 'B':
                            return '2';
                        case 'C':
                            return '6';
                        case 'D':
                            return '4';
                        default:
                            break;
                    }
                }
            }
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

    if (!fgets(buf, (int32_t)n, stdin)) {
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

    return (int32_t)len;
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
        tty_write(buf, (size_t)r);
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
    /* get_str() uses fgets() while key()/hot_key()/ryl_pause() use raw read().
       Without this, fgets buffers everything past the requested line and
       subsequent raw reads see EOF on piped input — replays die at the
       first hot_key after a get_str. */
    setvbuf(stdin, NULL, _IONBF, 0);

    if (isatty(STDIN_FILENO) && tcgetattr(STDIN_FILENO, &saved_termios) == 0) {
        termios_saved = 1;
        raw = saved_termios;
        raw.c_lflag &= ~(ICANON | ECHO);
        raw.c_cc[VMIN] = 1;
        raw.c_cc[VTIME] = 0;
        tcsetattr(STDIN_FILENO, TCSANOW, &raw);
        atexit(tty_restore);
    }

    struct sigaction sa;
    sa.sa_handler = tty_winch_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGWINCH, &sa, NULL);

    io = &tty_backend;
}
