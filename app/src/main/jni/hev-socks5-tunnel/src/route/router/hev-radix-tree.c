/*
 * HEV SOCKS5 Tunnel Radix Tree for IP Matching
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>

#include "hev-radix-tree.h"
#include "hev-logger.h"


/* Create new radix tree */
HevRadixTree *
hev_radix_tree_new (void)
{
    HevRadixTree *tree = malloc (sizeof (HevRadixTree));
    if (!tree)
        return NULL;

    tree->capacity = 16;
    tree->entries = malloc (sizeof (HevRouteEntry) * tree->capacity);
    if (!tree->entries) {
        free (tree);
        return NULL;
    }

    tree->count = 0;
    return tree;
}

/* Free radix tree */
void
hev_radix_tree_free (HevRadixTree *tree, void (*free_func) (void *))
{
    if (!tree)
        return;

    hev_radix_tree_clear (tree, free_func);
    free (tree->entries);
    free (tree);
}

/* Clear all entries */
void
hev_radix_tree_clear (HevRadixTree *tree, void (*free_func) (void *))
{
    if (!tree || !tree->entries)
        return;

    /* Free values if needed */
    if (free_func) {
        for (int i = 0; i < tree->count; i++) {
            if (tree->entries[i].value)
                free_func (tree->entries[i].value);
        }
    }

    tree->count = 0;
}

/* Insert IPv4 range */
int
hev_radix_tree_insert_ipv4 (HevRadixTree *tree, uint32_t ip, int prefix_len, void *value)
{
    if (!tree || prefix_len < 0 || prefix_len > 32)
        return -1;

    /* Apply mask to IP to get network address */
    if (prefix_len > 0 && prefix_len < 32) {
        uint32_t mask = 0xFFFFFFFF << (32 - prefix_len);
        ip &= mask;
    }

    /* Resize if needed */
    if (tree->count >= tree->capacity) {
        tree->capacity *= 2;
        tree->entries = realloc (tree->entries,
                                sizeof (HevRouteEntry) * tree->capacity);
        if (!tree->entries)
            return -1;
    }

    /* Insert in order of decreasing specificity (more specific first) */
    int i = tree->count;
    while (i > 0 && prefix_len > tree->entries[i-1].prefix_len) {
        tree->entries[i] = tree->entries[i-1];
        i--;
    }

    tree->entries[i].is_ipv6 = 0;
    tree->entries[i].network_ip.ipv4 = ip;
    tree->entries[i].prefix_len = prefix_len;
    tree->entries[i].value = value;
    tree->count++;

    LOG_D ("Inserted IPv4 range: %08x/%d", ip, prefix_len);
    return 0;
}

/* Insert IPv6 range */
int
hev_radix_tree_insert_ipv6 (HevRadixTree *tree, const uint8_t *ip, int prefix_len, void *value)
{
    if (!tree || !ip || prefix_len < 0 || prefix_len > 128)
        return -1;

    /* Resize if needed */
    if (tree->count >= tree->capacity) {
        tree->capacity *= 2;
        tree->entries = realloc (tree->entries,
                                sizeof (HevRouteEntry) * tree->capacity);
        if (!tree->entries)
            return -1;
    }

    /* Insert in order of decreasing specificity (more specific first) */
    int i = tree->count;
    while (i > 0 && prefix_len > tree->entries[i-1].prefix_len) {
        tree->entries[i] = tree->entries[i-1];
        i--;
    }

    tree->entries[i].is_ipv6 = 1;
    memcpy(tree->entries[i].network_ip.ipv6, ip, 16);
    tree->entries[i].prefix_len = prefix_len;
    tree->entries[i].value = value;
    tree->count++;

    LOG_D ("Inserted IPv6 range: prefix_len=%d", prefix_len);
    return 0;
}

/* Check if IPv4 matches a network */
static int
ipv4_matches_network (uint32_t ip, uint32_t network_ip, int prefix_len)
{
    if (prefix_len >= 32)
        return ip == network_ip;

    if (prefix_len <= 0)
        return 1;  /* /0 matches everything */

    uint32_t mask = 0xFFFFFFFF << (32 - prefix_len);
    return (ip & mask) == (network_ip & mask);
}

/* Check if IPv6 matches a network */
static int
ipv6_matches_network (const uint8_t *ip, const uint8_t *network_ip, int prefix_len)
{
    if (prefix_len >= 128)
        return memcmp(ip, network_ip, 16) == 0;

    if (prefix_len <= 0)
        return 1;  /* /0 matches everything */

    int bytes = prefix_len / 8;
    int bits = prefix_len % 8;

    /* Compare full bytes */
    if (bytes > 0 && memcmp(ip, network_ip, bytes) != 0)
        return 0;

    /* Compare remaining bits */
    if (bits > 0) {
        uint8_t mask = 0xFF << (8 - bits);
        if ((ip[bytes] & mask) != (network_ip[bytes] & mask))
            return 0;
    }

    return 1;
}

/* Lookup IPv4 */
void *
hev_radix_tree_lookup (HevRadixTree *tree, uint32_t ip)
{
    if (!tree || !tree->entries)
        return NULL;

    /* Find first (most specific) matching IPv4 route */
    for (int i = 0; i < tree->count; i++) {
        if (!tree->entries[i].is_ipv6 &&
            ipv4_matches_network (ip, tree->entries[i].network_ip.ipv4,
                                  tree->entries[i].prefix_len)) {
            return tree->entries[i].value;
        }
    }

    return NULL;
}

/* Lookup IPv6 */
void *
hev_radix_tree_lookup_ipv6 (HevRadixTree *tree, const uint8_t *ip)
{
    if (!tree || !tree->entries || !ip)
        return NULL;

    /* Find first (most specific) matching IPv6 route */
    for (int i = 0; i < tree->count; i++) {
        if (tree->entries[i].is_ipv6 &&
            ipv6_matches_network (ip, tree->entries[i].network_ip.ipv6,
                                  tree->entries[i].prefix_len)) {
            return tree->entries[i].value;
        }
    }

    return NULL;
}

/* Convert IPv4 string to uint32_t */
int
hev_radix_tree_str_to_ipv4 (const char *str, uint32_t *ip)
{
    struct in_addr addr;
    if (inet_pton (AF_INET, str, &addr) != 1)
        return -1;
    *ip = ntohl (addr.s_addr);
    return 0;
}

/* Parse CIDR notation */
int
hev_radix_tree_parse_cidr (const char *cidr, uint32_t *ip, int *prefix_len)
{
    char *slash = strchr (cidr, '/');
    if (!slash)
        return -1;

    int len = slash - cidr;
    if (len > 15)
        return -1;

    char ip_str[16];
    strncpy (ip_str, cidr, len);
    ip_str[len] = '\0';

    if (hev_radix_tree_str_to_ipv4 (ip_str, ip) < 0)
        return -1;

    *prefix_len = atoi (slash + 1);
    if (*prefix_len < 0 || *prefix_len > 32)
        return -1;

    return 0;
}