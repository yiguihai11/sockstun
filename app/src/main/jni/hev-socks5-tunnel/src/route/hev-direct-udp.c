/*
 * HEV SOCKS5 Tunnel Direct Connect (UDP)
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <arpa/inet.h>

#include <lwip/udp.h>
#include <lwip/ip_addr.h>

#include "hev-direct-udp.h"
#include "hev-logger.h"
#include "hev-config.h"

/* Global configuration */
static hev_direct_udp_config_t g_direct_udp_config = {0};

/* UDP receive callback */
static void
direct_udp_recv (void *arg, struct udp_pcb *pcb, struct pbuf *p,
                 const ip_addr_t *addr, u16_t port)
{
    hev_direct_udp_session_t *session = (hev_direct_udp_session_t *)arg;

    if (!session || !session->is_direct) {
        if (p)
            pbuf_free (p);
        return;
    }

    /* For direct UDP, we just forward the packet to the original destination */
    if (p) {
        /* The packet should have been sent to the original destination */
        /* This is a simplified implementation */
        pbuf_free (p);
    }
}

/* Initialize direct UDP module */
int
hev_direct_udp_init (const hev_direct_udp_config_t *config)
{
    if (!config)
        return -1;

    memcpy (&g_direct_udp_config, config, sizeof (hev_direct_udp_config_t));
    LOG_D ("Direct UDP initialized with timeout_ms=%d", config->timeout_ms);

    return 0;
}

/* Cleanup direct UDP module */
void
hev_direct_udp_fini (void)
{
    LOG_D ("Direct UDP module cleaned up");
    memset (&g_direct_udp_config, 0, sizeof (hev_direct_udp_config_t));
}

/* Create direct UDP socket */
int
hev_direct_udp_create_socket (const ip_addr_t *addr, u16_t port,
                              hev_direct_udp_session_t *session)
{
    int fd;
    struct sockaddr_in sa;
    int flags;

    if (!session)
        return -1;

    /* Create UDP socket */
    fd = socket (AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        LOG_E ("Failed to create UDP socket: %s", strerror (errno));
        return -1;
    }

    /* Set non-blocking */
    flags = fcntl (fd, F_GETFL, 0);
    if (flags < 0 || fcntl (fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        LOG_E ("Failed to set UDP socket non-blocking: %s", strerror (errno));
        close (fd);
        return -1;
    }

    /* Bind to a local port (any available) */
    memset (&sa, 0, sizeof (sa));
    sa.sin_family = AF_INET;
    sa.sin_addr.s_addr = INADDR_ANY;
    sa.sin_port = 0;

    if (bind (fd, (struct sockaddr *)&sa, sizeof (sa)) < 0) {
        LOG_E ("Failed to bind UDP socket: %s", strerror (errno));
        close (fd);
        return -1;
    }

    /* Store session info */
    memset (session, 0, sizeof (hev_direct_udp_session_t));
    session->fd = fd;
    session->is_direct = 1;

    /* Store remote address for sending */
    memcpy (&session->remote_addr, addr, sizeof (ip_addr_t));
    session->remote_port = port;

    /* Create UDP PCB for lwip integration */
    session->pcb = udp_new_ip_type (IP_GET_TYPE (addr));
    if (!session->pcb) {
        LOG_E ("Failed to create UDP PCB");
        close (fd);
        return -1;
    }

    /* Bind PCB to any local port */
    udp_bind (session->pcb, IP_ADDR_ANY, 0);

    /* Set receive callback */
    udp_recv (session->pcb, direct_udp_recv, session);

    LOG_D ("Direct UDP socket created for %s:%d",
           ipaddr_ntoa (addr), port);

    return 0;
}

/* Close direct UDP socket */
void
hev_direct_udp_close_socket (hev_direct_udp_session_t *session)
{
    if (!session)
        return;

    if (session->pcb) {
        udp_recv (session->pcb, NULL, NULL);
        udp_remove (session->pcb);
        session->pcb = NULL;
    }

    if (session->fd >= 0) {
        close (session->fd);
        session->fd = -1;
    }

    session->is_direct = 0;
    LOG_D ("Direct UDP socket closed");
}

/* Send UDP packet directly */
int
hev_direct_udp_send (hev_direct_udp_session_t *session,
                     struct pbuf *p, const ip_addr_t *addr, u16_t port)
{
    struct sockaddr_in sa;
    ssize_t sent;

    if (!session || !session->is_direct || !p || session->fd < 0) {
        LOG_E ("Invalid parameters for direct UDP send");
        return -1;
    }

    /* Prepare destination address */
    memset (&sa, 0, sizeof (sa));
    sa.sin_family = AF_INET;

    if (IP_IS_V4 (addr)) {
        sa.sin_addr.s_addr = ip4_addr_get_u32 (ip_2_ip4 (addr));
    } else {
        /* IPv6 not supported in this simple implementation */
        LOG_E ("IPv6 not supported for direct UDP");
        return -1;
    }

    sa.sin_port = htons (port);

    /* Send packet */
    sent = sendto (session->fd, p->payload, p->len, 0,
                    (struct sockaddr *)&sa, sizeof (sa));

    if (sent < 0) {
        LOG_E ("Direct UDP send failed: %s", strerror (errno));
        return -1;
    }

    LOG_D ("Direct UDP sent %zd bytes to %s:%d",
           sent, ipaddr_ntoa (addr), port);

    return 0;
}