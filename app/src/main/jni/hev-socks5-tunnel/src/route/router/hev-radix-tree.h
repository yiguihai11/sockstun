/*
 * HEV SOCKS5 Tunnel Radix Tree for IP Matching
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_RADIX_TREE_H__
#define __HEV_RADIX_TREE_H__

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Route entry for efficient CIDR matching */
typedef struct {
    int is_ipv6;  /* 0 for IPv4, 1 for IPv6 */
    union {
        uint32_t ipv4;      /* IPv4 address */
        uint8_t ipv6[16];   /* IPv6 address */
    } network_ip;
    int prefix_len;
    void *value;
} HevRouteEntry;

/* Radix tree - using sorted array for now */
typedef struct hev_radix_tree_t {
    HevRouteEntry *entries;
    int count;
    int capacity;
} HevRadixTree;

/* Radix tree functions */
HevRadixTree *hev_radix_tree_new (void);
void hev_radix_tree_free (HevRadixTree *tree, void (*free_func) (void *));

/* Insert IP range (IPv4) */
int hev_radix_tree_insert_ipv4 (HevRadixTree *tree, uint32_t ip, int prefix_len, void *value);

/* Insert IP range (IPv6) - placeholder for future */
int hev_radix_tree_insert_ipv6 (HevRadixTree *tree, const uint8_t *ip, int prefix_len, void *value);

/* Lookup IP */
void *hev_radix_tree_lookup (HevRadixTree *tree, uint32_t ip);
void *hev_radix_tree_lookup_ipv6 (HevRadixTree *tree, const uint8_t *ip);

/* Remove value from tree */
int hev_radix_tree_remove (HevRadixTree *tree, uint32_t ip, int prefix_len);

/* Get all values matching a prefix */
int hev_radix_tree_get_all (HevRadixTree *tree, uint32_t ip, void **values, int max_count);

/* Clear all nodes */
void hev_radix_tree_clear (HevRadixTree *tree, void (*free_func) (void *));

/* Convert string to IP */
int hev_radix_tree_str_to_ipv4 (const char *str, uint32_t *ip);
int hev_radix_tree_str_to_ipv6 (const char *str, uint8_t *ip);

/* Parse CIDR notation */
int hev_radix_tree_parse_cidr (const char *cidr, uint32_t *ip, int *prefix_len);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_RADIX_TREE_H__ */