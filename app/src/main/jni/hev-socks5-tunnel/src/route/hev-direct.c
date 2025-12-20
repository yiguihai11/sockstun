/*
 * HEV SOCKS5 Tunnel Direct Connect
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/select.h>
#include <fcntl.h>
#include <hev-task.h>
#include <hev-task-io.h>
#include <lwip/err.h>

#include "hev-config.h"
#include "hev-logger.h"
#include "hev-direct.h"

/* Global state */
static hev_direct_config_t direct_config;
static int direct_initialized = 0;

/* Internal helper functions */
static int is_socket_writable (int fd, int timeout_ms)
{
    fd_set write_fds;
    struct timeval tv;
    int res;

    FD_ZERO (&write_fds);
    FD_SET (fd, &write_fds);

    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    res = select (fd + 1, NULL, &write_fds, NULL, &tv);
    if (res < 0)
        return -1;

    return res > 0 ? 0 : -1;
}

int
hev_direct_init (const hev_direct_config_t *config)
{
    if (!config || !config->enabled) {
        return 0;
    }

    memcpy (&direct_config, config, sizeof (hev_direct_config_t));
    direct_initialized = 1;

    LOG_D ("Direct connect initialized: timeout=%dms",
           direct_config.timeout_ms);

    return 0;
}

void
hev_direct_fini (void)
{
    direct_initialized = 0;
}

int
hev_direct_create_socket (const char *dest_addr, int dest_port)
{
    int sock_fd = -1;
    int ret = -1;

    if (!direct_initialized || !dest_addr || dest_port <= 0) {
        return -1;
    }

    LOG_D ("Creating direct socket to %s:%d", dest_addr, dest_port);

    sock_fd = socket (AF_INET, SOCK_STREAM, 0);
    if (sock_fd < 0) {
        LOG_W ("Failed to create socket: %s", strerror (errno));
        return -1;
    }

    /* Set socket to non-blocking for timeout */
    int flags = fcntl (sock_fd, F_GETFL, 0);
    fcntl (sock_fd, F_SETFL, flags | O_NONBLOCK);

    /* Connect with timeout */
    struct sockaddr_in addr;
    memset (&addr, 0, sizeof (addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons (dest_port);
    inet_pton (AF_INET, dest_addr, &addr.sin_addr);

    ret = connect (sock_fd, (struct sockaddr *)&addr, sizeof (addr));
    if (ret < 0) {
        if (errno == EINPROGRESS) {
            /* Wait for connection with timeout */
            if (is_socket_writable (sock_fd, direct_config.timeout_ms) < 0) {
                LOG_W ("Connection timeout to %s:%d", dest_addr, dest_port);
                close (sock_fd);
                return -1;
            } else {
                /* Check if connection succeeded */
                int error = 0;
                socklen_t len = sizeof (error);
                if (getsockopt (sock_fd, SOL_SOCKET, SO_ERROR, &error, &len) < 0) {
                    LOG_W ("Failed to get socket error: %s", strerror (errno));
                    close (sock_fd);
                    return -1;
                } else if (error != 0) {
                    if (error == ECONNREFUSED) {
                        LOG_W ("Connection refused by %s:%d", dest_addr, dest_port);
                    } else if (error == ECONNRESET) {
                        LOG_W ("Connection reset by %s:%d", dest_addr, dest_port);
                    } else {
                        LOG_W ("Connection failed to %s:%d: %s", dest_addr, dest_port, strerror (error));
                    }
                    close (sock_fd);
                    return -1;
                } else {
                    /* Connection succeeded */
                    LOG_D ("Direct socket created successfully to %s:%d", dest_addr, dest_port);

                    /* Set back to blocking mode */
                    flags = fcntl (sock_fd, F_GETFL, 0);
                    fcntl (sock_fd, F_SETFL, flags & ~O_NONBLOCK);

                    return sock_fd;
                }
            }
        } else {
            LOG_W ("Connect failed to %s:%d: %s", dest_addr, dest_port, strerror (errno));
            close (sock_fd);
            return -1;
        }
    } else {
        /* Connection succeeded immediately */
        LOG_D ("Direct socket created successfully to %s:%d", dest_addr, dest_port);

        /* Set back to blocking mode */
        flags = fcntl (sock_fd, F_GETFL, 0);
        fcntl (sock_fd, F_SETFL, flags & ~O_NONBLOCK);

        return sock_fd;
    }
}

int
hev_direct_check_fallback (hev_direct_session_t *session, err_t tcp_err)
{
    /* Check if we need to fallback to SOCKS5 */
    if (!session || !session->is_direct) {
        return 0;  /* Already using SOCKS5 */
    }

    /* Check TCP error conditions that indicate RST or other failures */
    if (tcp_err == ERR_RST) {
        LOG_W ("RST received, falling back to SOCKS5");
        return 1;
    }

    if (tcp_err == ERR_ABRT) {
        LOG_W ("Connection aborted, falling back to SOCKS5");
        return 1;
    }

    if (tcp_err == ERR_CONN) {
        LOG_W ("Connection error, falling back to SOCKS5");
        return 1;
    }

    /* Also check socket error conditions */
    if (session->fd >= 0) {
        int error = 0;
        socklen_t len = sizeof (error);
        if (getsockopt (session->fd, SOL_SOCKET, SO_ERROR, &error, &len) == 0) {
            if (error == ECONNRESET) {
                LOG_W ("Connection reset detected, falling back to SOCKS5");
                return 1;
            }
        }
    }

    return 0;
}

int
hev_direct_load_config (void)
{
    /* Configuration is now loaded directly from hev-config module */
    return 0;
}