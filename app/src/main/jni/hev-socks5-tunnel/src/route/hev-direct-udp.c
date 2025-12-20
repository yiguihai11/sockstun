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
#include <netdb.h>

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

    /* Forward received packet to user callback */
    if (p && session->recv_cb) {
        /* Call the user's receive callback */
        session->recv_cb (session, p, addr, port, session->user_data);
    } else if (p) {
        /* No callback registered, just free the packet */
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
    int flags;
    int optval;
    socklen_t optlen;

    if (!session)
        return -1;

    /* Create UDP socket - try IPv6 first, fallback to IPv4 */
    if (IP_IS_V6 (addr)) {
        fd = socket (AF_INET6, SOCK_DGRAM, 0);
    } else {
        fd = socket (AF_INET, SOCK_DGRAM, 0);
    }
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

    /* Set socket options for better performance */

    /* Adjust buffer size based on port - optimize for DNS (port 53) */
    if (port == 53) {
        /* DNS optimization: 512 bytes (traditional DNS response size) */
        optval = 512;
    } else {
        /* General UDP: 64KB */
        optval = 65536;
    }
    optlen = sizeof (optval);
    setsockopt (fd, SOL_SOCKET, SO_RCVBUF, &optval, optlen);

    /* Use the same buffer size for sending */
    setsockopt (fd, SOL_SOCKET, SO_SNDBUF, &optval, optlen);

    /* Set TTL (Time To Live) */
    if (IP_IS_V6 (addr)) {
        int ttl = 64;  /* Default IPv6 TTL */
        setsockopt (fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, &ttl, sizeof (ttl));
    } else {
        int ttl = 64;  /* Default IPv4 TTL */
        setsockopt (fd, IPPROTO_IP, IP_TTL, &ttl, sizeof (ttl));
    }

    /* Enable broadcast if needed (for local testing) */
    int broadcast = 1;
    setsockopt (fd, SOL_SOCKET, SO_BROADCAST, &broadcast, sizeof (broadcast));

    /* Bind to a local port (any available) */
    if (IP_IS_V6 (addr)) {
        struct sockaddr_in6 sa6;
        memset (&sa6, 0, sizeof (sa6));
        sa6.sin6_family = AF_INET6;
        sa6.sin6_addr = in6addr_any;
        sa6.sin6_port = 0;
        bind (fd, (struct sockaddr *)&sa6, sizeof (sa6));
    } else {
        struct sockaddr_in sa4;
        memset (&sa4, 0, sizeof (sa4));
        sa4.sin_family = AF_INET;
        sa4.sin_addr.s_addr = INADDR_ANY;
        sa4.sin_port = 0;
        bind (fd, (struct sockaddr *)&sa4, sizeof (sa4));
    }

    /* Store session info */
    memset (session, 0, sizeof (hev_direct_udp_session_t));
    session->fd = fd;
    session->is_direct = 1;
    session->recv_cb = NULL;
    session->user_data = NULL;

    /* Store remote address for sending */
    memcpy (&session->remote_addr, addr, sizeof (ip_addr_t));
    session->remote_port = port;

    /* Create UDP PCB for lwip integration (optional) */
    session->pcb = udp_new_ip_type (IP_GET_TYPE (addr));
    if (!session->pcb) {
        LOG_W ("Failed to create UDP PCB, continuing without lwip integration");
        session->pcb = NULL;
        /* Continue without PCB - this is ok for direct UDP */
    } else {
        /* Bind PCB to any local port */
        udp_bind (session->pcb, IP_ADDR_ANY, 0);

        /* Set receive callback */
        udp_recv (session->pcb, direct_udp_recv, session);
    }

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
    ssize_t sent;

    if (!session || !session->is_direct || !p || session->fd < 0) {
        LOG_E ("Invalid parameters for direct UDP send");
        return -1;
    }

    if (IP_IS_V4 (addr)) {
        /* IPv4 send */
        struct sockaddr_in sa4;
        memset (&sa4, 0, sizeof (sa4));
        sa4.sin_family = AF_INET;
        sa4.sin_addr.s_addr = ip4_addr_get_u32 (ip_2_ip4 (addr));
        sa4.sin_port = htons (port);

        sent = sendto (session->fd, p->payload, p->len, 0,
                        (struct sockaddr *)&sa4, sizeof (sa4));
    } else {
        /* IPv6 send */
        struct sockaddr_in6 sa6;
        memset (&sa6, 0, sizeof (sa6));
        sa6.sin6_family = AF_INET6;
        memcpy (&sa6.sin6_addr, ip_2_ip6 (addr), sizeof (struct in6_addr));
        sa6.sin6_port = htons (port);

        sent = sendto (session->fd, p->payload, p->len, 0,
                        (struct sockaddr *)&sa6, sizeof (sa6));
    }

    if (sent < 0) {
        LOG_E ("Direct UDP send failed: %s", strerror (errno));
        return -1;
    }

    LOG_D ("Direct UDP sent %zd bytes to %s:%d",
           sent, ipaddr_ntoa (addr), port);

    return 0;
}

/* Set receive callback for UDP session */
void
hev_direct_udp_set_receive_callback (hev_direct_udp_session_t *session,
                                     hev_direct_udp_recv_fn cb,
                                     void *user_data)
{
    if (!session)
        return;

    session->recv_cb = cb;
    session->user_data = user_data;

    LOG_D ("UDP receive callback set for session");
}

/* Receive data from UDP socket */
int
hev_direct_udp_receive (hev_direct_udp_session_t *session)
{
    ssize_t recv_len;
    uint8_t buffer[2048];  /* MTU size */
    struct sockaddr_storage sa;
    socklen_t sa_len = sizeof (sa);
    ip_addr_t addr;
    u16_t port;

    if (!session || !session->is_direct || session->fd < 0) {
        LOG_E ("Invalid session for UDP receive");
        return -1;
    }

    /* Receive data from socket */
    recv_len = recvfrom (session->fd, buffer, sizeof (buffer), 0,
                         (struct sockaddr *)&sa, &sa_len);

    if (recv_len < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            /* No data available */
            return 0;
        }
        LOG_E ("UDP receive failed: %s", strerror (errno));
        return -1;
    }

    /* Convert sockaddr to ip_addr */
    if (sa.ss_family == AF_INET) {
        struct sockaddr_in *sa4 = (struct sockaddr_in *)&sa;
        IP_ADDR4 (&addr, sa4->sin_addr.s_addr);
        port = ntohs (sa4->sin_port);
    } else if (sa.ss_family == AF_INET6) {
        struct sockaddr_in6 *sa6 = (struct sockaddr_in6 *)&sa;
        /* Convert IPv6 address - use memcpy for simplicity */
        memcpy (ip_2_ip6 (&addr), &sa6->sin6_addr, sizeof (struct in6_addr));
        IP_SET_TYPE (&addr, IPADDR_TYPE_V6);
        port = ntohs (sa6->sin6_port);
    } else {
        LOG_E ("Unknown address family %d", sa.ss_family);
        return -1;
    }

    /* Create pbuf for received data */
    struct pbuf *p = pbuf_alloc (PBUF_TRANSPORT, recv_len, PBUF_RAM);
    if (!p) {
        LOG_E ("Failed to allocate pbuf for received data");
        return -1;
    }

    /* Copy data to pbuf */
    memcpy (p->payload, buffer, recv_len);

    /* Call receive callback if set */
    if (session->recv_cb) {
        session->recv_cb (session, p, &addr, port, session->user_data);
    } else {
        /* No callback, just free the packet */
        pbuf_free (p);
    }

    LOG_D ("UDP received %zd bytes from %s:%d",
           recv_len, ipaddr_ntoa (&addr), port);

    return recv_len;
}

/* Input raw data to UDP session (for integration with external data sources) */
void
hev_direct_udp_input (hev_direct_udp_session_t *session,
                      const void *data, size_t len,
                      const ip_addr_t *addr, u16_t port)
{
    if (!session || !data || len == 0) {
        return;
    }

    /* Create pbuf for input data */
    struct pbuf *p = pbuf_alloc (PBUF_TRANSPORT, len, PBUF_RAM);
    if (!p) {
        LOG_E ("Failed to allocate pbuf for input data");
        return;
    }

    /* Copy data to pbuf */
    memcpy (p->payload, data, len);

    /* Call receive callback if set */
    if (session->recv_cb) {
        session->recv_cb (session, p, addr, port, session->user_data);
    } else {
        /* No callback, just free the packet */
        pbuf_free (p);
    }

    LOG_D ("UDP input %zu bytes from %s:%d",
           len, ipaddr_ntoa (addr), port);
}