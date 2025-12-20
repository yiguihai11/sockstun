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

/* Forward declaration */
typedef struct hev_direct_udp_session_t hev_direct_udp_session_t;

/* Direct UDP receive callback type */
typedef void (*hev_direct_udp_recv_fn)(hev_direct_udp_session_t *session,
                                       struct pbuf *p,
                                       const ip_addr_t *addr,
                                       u16_t port,
                                       void *user_data);

/* Direct UDP session state */
struct hev_direct_udp_session_t
{
    int fd;
    int is_direct;
    struct udp_pcb *pcb;
    ip_addr_t remote_addr;
    u16_t remote_port;

    /* Receive callback */
    hev_direct_udp_recv_fn recv_cb;
    void *user_data;
};

/* Direct UDP functions */
int hev_direct_udp_init (const hev_direct_udp_config_t *config);
void hev_direct_udp_fini (void);

int hev_direct_udp_create_socket (const ip_addr_t *addr, u16_t port,
                                  hev_direct_udp_session_t *session);
void hev_direct_udp_close_socket (hev_direct_udp_session_t *session);

int hev_direct_udp_send (hev_direct_udp_session_t *session,
                         struct pbuf *p, const ip_addr_t *addr, u16_t port);

void hev_direct_udp_set_receive_callback (hev_direct_udp_session_t *session,
                                          hev_direct_udp_recv_fn cb,
                                          void *user_data);

int hev_direct_udp_receive (hev_direct_udp_session_t *session);
void hev_direct_udp_input (hev_direct_udp_session_t *session,
                           const void *data, size_t len,
                           const ip_addr_t *addr, u16_t port);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_DIRECT_UDP_H__ */