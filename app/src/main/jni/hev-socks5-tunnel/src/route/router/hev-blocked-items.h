/*
 * HEV SOCKS5 Tunnel Blocked Items Manager
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_BLOCKED_ITEMS_H__
#define __HEV_BLOCKED_ITEMS_H__

#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Failure reasons */
typedef enum {
    HEV_FAILURE_REASON_UNKNOWN,
    HEV_FAILURE_REASON_RST,              /* Connection reset (GFW behavior) */
    HEV_FAILURE_REASON_TIMEOUT,          /* Connection timeout */
    HEV_FAILURE_REASON_HANDSHAKE_FAILURE, /* TLS handshake failed */
    HEV_FAILURE_REASON_DNS_FAILURE,      /* DNS resolution failed */
    HEV_FAILURE_REASON_CONN_REFUSED,     /* Connection refused */
    HEV_FAILURE_REASON_HOST_UNREACHABLE  /* Host unreachable */
} HevFailureReason;

/* Blocked item types */
typedef enum {
    HEV_ITEM_TYPE_UNKNOWN,
    HEV_ITEM_TYPE_DOMAIN,   /* Domain name */
    HEV_ITEM_TYPE_IPV4,     /* IPv4 address */
    HEV_ITEM_TYPE_IPV6      /* IPv6 address */
} HevItemType;

/* Port info */
typedef struct hev_port_info_t {
    int port;
    int attempt_count;
    time_t last_attempt;
    HevFailureReason last_failure_reason;
    time_t first_failure_time;
    struct hev_port_info_t *next;
} HevPortInfo;

/* Blocked item */
typedef struct hev_blocked_item_t {
    char key[256];                     /* Domain or IP */
    HevItemType type;
    time_t first_blocked;
    time_t last_updated;
    int total_attempts;
    HevPortInfo *ports;
    int failure_reasons[7];             /* Count of each failure type */
    struct hev_blocked_item_t *next;
} HevBlockedItem;

/* Blocked items manager */
typedef struct hev_blocked_items_manager_t {
    HevBlockedItem *items[1024];        /* Hash table */
    int total_attempts;
    time_t expiry_minutes;
    int max_items;
} HevBlockedItemsManager;

/* Blocked items manager functions */
HevBlockedItemsManager *hev_blocked_items_manager_new (int expiry_minutes);
void hev_blocked_items_manager_free (HevBlockedItemsManager *manager);

int hev_blocked_items_manager_is_blocked (HevBlockedItemsManager *manager,
                                          const char *key);
int hev_blocked_items_manager_add_item (HevBlockedItemsManager *manager,
                                        const char *key, HevItemType type,
                                        const char *target_addr, int target_port,
                                        HevFailureReason reason);
int hev_blocked_items_manager_update_timestamp (HevBlockedItemsManager *manager,
                                                const char *key);
int hev_blocked_items_manager_increment_total_attempts (HevBlockedItemsManager *manager);
void hev_blocked_items_manager_cleanup_expired (HevBlockedItemsManager *manager);

/* Helper functions */
const char *hev_failure_reason_to_string (HevFailureReason reason);
const char *hev_item_type_to_string (HevItemType type);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_BLOCKED_ITEMS_H__ */