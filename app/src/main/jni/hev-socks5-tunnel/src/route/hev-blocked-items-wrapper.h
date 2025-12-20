/*
 * HEV SOCKS5 Tunnel Blocked Items Wrapper
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_BLOCKED_ITEMS_WRAPPER_H__
#define __HEV_BLOCKED_ITEMS_WRAPPER_H__

#include "route/router/hev-blocked-items.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Wrapper types for smart proxy compatibility */
typedef HevBlockedItemsManager HevBlockedItems;

/* Wrapper functions */
HevBlockedItems *hev_blocked_items_new (void);
void hev_blocked_items_free (HevBlockedItems *blocked_items);
int hev_blocked_items_add (HevBlockedItems *blocked_items,
                          const char *addr, const char *hostname,
                          HevFailureReason reason, int expiry_minutes);
int hev_blocked_items_is_blocked (HevBlockedItems *blocked_items, const char *value);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_BLOCKED_ITEMS_WRAPPER_H__ */