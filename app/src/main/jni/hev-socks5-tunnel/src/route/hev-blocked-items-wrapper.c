/*
 * HEV SOCKS5 Tunnel Blocked Items Wrapper
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include "hev-blocked-items-wrapper.h"
#include "hev-config.h"
#include "hev-logger.h"

/* Create new blocked items wrapper */
HevBlockedItems *
hev_blocked_items_new (void)
{
    int expiry_minutes = hev_config_get_blacklist_expiry_minutes ();
    if (expiry_minutes <= 0)
        expiry_minutes = 360;  /* Default 6 hours */

    return hev_blocked_items_manager_new (expiry_minutes);
}

/* Free blocked items wrapper */
void
hev_blocked_items_free (HevBlockedItems *blocked_items)
{
    hev_blocked_items_manager_free (blocked_items);
}

/* Add blocked item with wrapper interface */
int
hev_blocked_items_add (HevBlockedItems *blocked_items,
                      const char *addr, const char *hostname,
                      HevFailureReason reason, int expiry_minutes)
{
    if (!blocked_items)
        return -1;

    /* Determine item type */
    HevItemType type = HEV_ITEM_TYPE_UNKNOWN;
    const char *key = NULL;

    if (hostname && hostname[0]) {
        type = HEV_ITEM_TYPE_DOMAIN;
        key = hostname;
    } else if (addr && addr[0]) {
        /* Simple check for IPv4 */
        int dots = 0;
        for (const char *p = addr; *p; p++) {
            if (*p == '.')
                dots++;
            else if (!isdigit (*p))
                break;
        }
        if (dots == 3 && strlen (addr) < 16) {
            type = HEV_ITEM_TYPE_IPV4;
        }
        key = addr;
    }

    if (!key || type == HEV_ITEM_TYPE_UNKNOWN) {
        LOG_W ("Invalid parameters for blocked item: addr=%s, hostname=%s",
               addr ? addr : "NULL", hostname ? hostname : "NULL");
        return -1;
    }

    return hev_blocked_items_manager_add_item (blocked_items, key, type,
                                             NULL, 0, reason);
}

/* Check if item is blocked with wrapper interface */
int
hev_blocked_items_is_blocked (HevBlockedItems *blocked_items, const char *value)
{
    return hev_blocked_items_manager_is_blocked (blocked_items, value);
}

