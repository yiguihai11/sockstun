/*
 * HEV SOCKS5 Tunnel Blocked Items Manager
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <arpa/inet.h>

#include "hev-blocked-items.h"
#include "hev-logger.h"

/* Simple hash function */
static unsigned int
key_hash (const char *str)
{
    unsigned int hash = 5381;
    int c;

    while ((c = *str++))
        hash = ((hash << 5) + hash) + c;

    return hash % 1024;
}

/* Convert string to lowercase */
static void
to_lower (char *str)
{
    while (*str) {
        *str = tolower (*str);
        str++;
    }
}

/* Create new blocked items manager */
HevBlockedItemsManager *
hev_blocked_items_manager_new (int expiry_minutes)
{
    HevBlockedItemsManager *manager = malloc (sizeof (HevBlockedItemsManager));
    if (!manager)
        return NULL;

    memset (manager, 0, sizeof (HevBlockedItemsManager));
    manager->expiry_minutes = expiry_minutes;
    manager->max_items = 10000; /* Default limit */

    return manager;
}

/* Free blocked items manager */
void
hev_blocked_items_manager_free (HevBlockedItemsManager *manager)
{
    if (!manager)
        return;

    /* Free all items */
    for (int i = 0; i < 1024; i++) {
        HevBlockedItem *item = manager->items[i];
        while (item) {
            HevBlockedItem *next = item->next;

            /* Free port infos */
            HevPortInfo *port = item->ports;
            while (port) {
                HevPortInfo *next_port = port->next;
                free (port);
                port = next_port;
            }

            free (item);
            item = next;
        }
    }

    free (manager);
}

/* Check if item is blocked */
int
hev_blocked_items_manager_is_blocked (HevBlockedItemsManager *manager,
                                       const char *key)
{
    if (!manager || !key)
        return 0;

    char key_lower[256];
    strncpy (key_lower, key, 255);
    key_lower[255] = '\0';
    to_lower (key_lower);

    unsigned int hash = key_hash (key_lower);
    HevBlockedItem *item = manager->items[hash];

    while (item) {
        if (strcmp (item->key, key_lower) == 0) {
            time_t now = time (NULL);
            /* Check if expired */
            if ((now - item->last_updated) > (manager->expiry_minutes * 60)) {
                LOG_D ("Blocked item %s expired", key_lower);
                return 0;
            }
            return 1;
        }
        item = item->next;
    }

    return 0;
}

/* Add blocked item */
int
hev_blocked_items_manager_add_item (HevBlockedItemsManager *manager,
                                    const char *key, HevItemType type,
                                    const char *target_addr, int target_port,
                                    HevFailureReason reason)
{
    if (!manager || !key)
        return -1;

    char key_lower[256];
    strncpy (key_lower, key, 255);
    key_lower[255] = '\0';
    to_lower (key_lower);

    unsigned int hash = key_hash (key_lower);
    HevBlockedItem *item = manager->items[hash];

    /* Check if item already exists */
    while (item) {
        if (strcmp (item->key, key_lower) == 0) {
            /* Update existing item */
            time_t now = time (NULL);
            item->last_updated = now;
            item->total_attempts++;
            item->failure_reasons[reason]++;

            /* Update port info */
            HevPortInfo *port = item->ports;
            while (port) {
                if (port->port == target_port) {
                    port->attempt_count++;
                    port->last_attempt = now;
                    if (reason != HEV_FAILURE_REASON_UNKNOWN) {
                        port->last_failure_reason = reason;
                    }
                    return 0;
                }
                port = port->next;
            }

            /* Add new port info */
            HevPortInfo *new_port = malloc (sizeof (HevPortInfo));
            if (new_port) {
                memset (new_port, 0, sizeof (HevPortInfo));
                new_port->port = target_port;
                new_port->attempt_count = 1;
                new_port->last_attempt = now;
                new_port->first_failure_time = now;
                new_port->last_failure_reason = reason;
                new_port->next = item->ports;
                item->ports = new_port;
            }

            LOG_W ("Updated blocked item: %s (reason: %s)", key_lower,
                   hev_failure_reason_to_string (reason));
            return 0;
        }
        item = item->next;
    }

    /* Create new item */
    HevBlockedItem *new_item = malloc (sizeof (HevBlockedItem));
    if (!new_item)
        return -1;

    memset (new_item, 0, sizeof (HevBlockedItem));
    strcpy (new_item->key, key_lower);
    new_item->type = type;
    new_item->first_blocked = time (NULL);
    new_item->last_updated = new_item->first_blocked;
    new_item->total_attempts = 1;
    new_item->failure_reasons[reason]++;

    /* Add port info */
    HevPortInfo *new_port = malloc (sizeof (HevPortInfo));
    if (new_port) {
        memset (new_port, 0, sizeof (HevPortInfo));
        new_port->port = target_port;
        new_port->attempt_count = 1;
        new_port->last_attempt = new_item->first_blocked;
        new_port->first_failure_time = new_item->first_blocked;
        new_port->last_failure_reason = reason;
        new_item->ports = new_port;
    }

    /* Add to hash table */
    new_item->next = manager->items[hash];
    manager->items[hash] = new_item;

    LOG_I ("Added new blocked item: %s (reason: %s)", key_lower,
           hev_failure_reason_to_string (reason));

    return 0;
}

/* Update timestamp */
int
hev_blocked_items_manager_update_timestamp (HevBlockedItemsManager *manager,
                                            const char *key)
{
    if (!manager || !key)
        return -1;

    char key_lower[256];
    strncpy (key_lower, key, 255);
    key_lower[255] = '\0';
    to_lower (key_lower);

    unsigned int hash = key_hash (key_lower);
    HevBlockedItem *item = manager->items[hash];

    while (item) {
        if (strcmp (item->key, key_lower) == 0) {
            item->last_updated = time (NULL);
            LOG_D ("Updated timestamp for blocked item: %s", key_lower);
            return 0;
        }
        item = item->next;
    }

    return -1;
}

/* Increment total attempts */
int
hev_blocked_items_manager_increment_total_attempts (HevBlockedItemsManager *manager)
{
    if (!manager)
        return -1;

    manager->total_attempts++;
    return 0;
}

/* Cleanup expired items */
void
hev_blocked_items_manager_cleanup_expired (HevBlockedItemsManager *manager)
{
    if (!manager)
        return;

    time_t now = time (NULL);
    time_t expiry_time = manager->expiry_minutes * 60;

    for (int i = 0; i < 1024; i++) {
        HevBlockedItem **prev = &manager->items[i];
        HevBlockedItem *item = manager->items[i];

        while (item) {
            if ((now - item->last_updated) > expiry_time) {
                /* Remove expired item */
                *prev = item->next;

                /* Free port infos */
                HevPortInfo *port = item->ports;
                while (port) {
                    HevPortInfo *next_port = port->next;
                    free (port);
                    port = next_port;
                }

                LOG_D ("Removed expired blocked item: %s", item->key);
                HevBlockedItem *next = item->next;
                free (item);
                item = next;
            } else {
                prev = &item->next;
                item = item->next;
            }
        }
    }
}

/* Convert failure reason to string */
const char *
hev_failure_reason_to_string (HevFailureReason reason)
{
    switch (reason) {
    case HEV_FAILURE_REASON_RST:
        return "RST";
    case HEV_FAILURE_REASON_TIMEOUT:
        return "Timeout";
    case HEV_FAILURE_REASON_HANDSHAKE_FAILURE:
        return "HandshakeFailure";
    case HEV_FAILURE_REASON_DNS_FAILURE:
        return "DNSFailure";
    case HEV_FAILURE_REASON_CONN_REFUSED:
        return "ConnectionRefused";
    case HEV_FAILURE_REASON_HOST_UNREACHABLE:
        return "HostUnreachable";
    default:
        return "Unknown";
    }
}

/* Convert item type to string */
const char *
hev_item_type_to_string (HevItemType type)
{
    switch (type) {
    case HEV_ITEM_TYPE_DOMAIN:
        return "Domain";
    case HEV_ITEM_TYPE_IPV4:
        return "IPv4";
    case HEV_ITEM_TYPE_IPV6:
        return "IPv6";
    default:
        return "Unknown";
    }
}

