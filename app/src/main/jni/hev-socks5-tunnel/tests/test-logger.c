/*
 * Minimal Logger Implementation for Tests
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdarg.h>

/* Logger levels */
typedef enum {
    HEV_LOGGER_DEBUG = 0,
    HEV_LOGGER_INFO,
    HEV_LOGGER_WARN,
    HEV_LOGGER_ERROR
} HevLoggerLevel;

/* Minimal logger implementation */
void hev_logger_log (HevLoggerLevel level, const char *fmt, ...)
{
    const char *level_str[] = {"DEBUG", "INFO", "WARN", "ERROR"};
    va_list args;

    if (level < HEV_LOGGER_DEBUG || level > HEV_LOGGER_ERROR)
        return;

    fprintf (stderr, "[%s] ", level_str[level]);

    va_start (args, fmt);
    vfprintf (stderr, fmt, args);
    va_end (args);

    fprintf (stderr, "\n");
}

/* Stub implementation for other logger functions if needed */
void hev_logger_init (void) {}
void hev_logger_fini (void) {}