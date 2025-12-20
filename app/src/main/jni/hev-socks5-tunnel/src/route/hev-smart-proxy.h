/*
 * HEV SOCKS5 Tunnel Smart Proxy
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_SMART_PROXY_H__
#define __HEV_SMART_PROXY_H__

#include "hev-router.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Forward declarations */
typedef struct hev_traffic_detector_t HevTrafficDetector;
typedef struct hev_blocked_items_manager_t HevBlockedItems;
typedef enum hev_failure_reason_e HevFailureReason;

/* Smart proxy result */
typedef struct {
    HevRouterAction action;
    HevRouterRule *rule;
    int match;
    int needs_detection;
    char hostname[256];
} HevSmartProxyResult;

/* Smart proxy structure */
typedef struct hev_smart_proxy_t {
    HevRouter *router;
    HevTrafficDetector *traffic_detector;
    HevBlockedItems *blocked_items;

    int enabled;
    int timeout_ms;
    int blacklist_expiry_minutes;
} HevSmartProxy;

/* Smart proxy functions */
HevSmartProxy *hev_smart_proxy_new (const char *config_path);
void hev_smart_proxy_free (HevSmartProxy *proxy);

int hev_smart_proxy_load_config (HevSmartProxy *proxy);
int hev_smart_proxy_save_config (HevSmartProxy *proxy);

/* Stage 1: Handle initial connection decision */
HevSmartProxyResult hev_smart_proxy_connect (HevSmartProxy *proxy,
                                            const char *addr, int port);

/* Stage 2: Handle traffic detection and final decision */
HevSmartProxyResult hev_smart_proxy_handle_traffic (HevSmartProxy *proxy,
                                                   const char *addr,
                                                   const void *data, size_t len);

/* Record connection failure for dynamic blacklisting */
void hev_smart_proxy_record_failure (HevSmartProxy *proxy, const char *addr,
                                    const char *hostname, HevFailureReason reason);

/* Global instance management */
HevSmartProxy *hev_smart_proxy_get_instance (void);
void hev_smart_proxy_set_instance (HevSmartProxy *proxy);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_SMART_PROXY_H__ */