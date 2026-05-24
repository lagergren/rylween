#ifndef RYLWEEN_NATIVE_JSON5_LOG_H__
#define RYLWEEN_NATIVE_JSON5_LOG_H__

typedef enum {
    LOG_LVL_DEBUG = 0,
    LOG_LVL_INFO,
    LOG_LVL_WARN,
    LOG_LVL_ERR,
    LOG_LVL_AUDIT,
} log_level;

void log_msg(log_level lvl, const char *module, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));

#define LOG_D(mod, ...) log_msg(LOG_LVL_DEBUG, mod, __VA_ARGS__)
#define LOG_I(mod, ...) log_msg(LOG_LVL_INFO, mod, __VA_ARGS__)
#define LOG_W(mod, ...) log_msg(LOG_LVL_WARN, mod, __VA_ARGS__)
#define LOG_E(mod, ...) log_msg(LOG_LVL_ERR, mod, __VA_ARGS__)
#define LOG_A(mod, ...) log_msg(LOG_LVL_AUDIT, mod, __VA_ARGS__)

#endif
