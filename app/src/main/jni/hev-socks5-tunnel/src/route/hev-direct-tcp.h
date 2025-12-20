/*
 * HEV SOCKS5 Tunnel Direct Connect (TCP)
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_DIRECT_TCP_H__
#define __HEV_DIRECT_TCP_H__

#include <lwip/tcp.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Direct connect configuration */
typedef struct hev_direct_config_t
{
    int enabled;
    int timeout_ms;
} hev_direct_config_t;

/* Direct connect session state */
typedef struct hev_direct_session_t
{
    int fd;
    int is_direct;
    struct tcp_pcb *pcb;
    int fallback_triggered;
    int traffic_detected;
} hev_direct_session_t;

/* Direct connect functions */
int hev_direct_init (const hev_direct_config_t *config);
void hev_direct_fini (void);

/* Create direct socket connection */
int hev_direct_create_socket (const char *dest_addr, int dest_port);

/* Check if session should fallback to SOCKS5 */
int hev_direct_check_fallback (hev_direct_session_t *session, err_t tcp_err);

/* Configuration functions */
int hev_direct_load_config (void);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_DIRECT_TCP_H__ */