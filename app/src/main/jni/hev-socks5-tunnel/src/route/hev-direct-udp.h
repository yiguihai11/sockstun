/*
 * HEV SOCKS5 Tunnel Direct Connect (UDP)
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_DIRECT_UDP_H__
#define __HEV_DIRECT_UDP_H__

#include <lwip/udp.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Direct UDP configuration */
typedef struct hev_direct_udp_config_t
{
    int enabled;
    int timeout_ms;
} hev_direct_udp_config_t;

/* Direct UDP session state */
typedef struct hev_direct_udp_session_t
{
    int fd;
    int is_direct;
    struct udp_pcb *pcb;
    ip_addr_t remote_addr;
    u16_t remote_port;
} hev_direct_udp_session_t;

/* Direct UDP functions */
int hev_direct_udp_init (const hev_direct_udp_config_t *config);
void hev_direct_udp_fini (void);

int hev_direct_udp_create_socket (const ip_addr_t *addr, u16_t port,
                                  hev_direct_udp_session_t *session);
void hev_direct_udp_close_socket (hev_direct_udp_session_t *session);

int hev_direct_udp_send (hev_direct_udp_session_t *session,
                         struct pbuf *p, const ip_addr_t *addr, u16_t port);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_DIRECT_UDP_H__ */