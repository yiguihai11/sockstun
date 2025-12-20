/*
 * HEV SOCKS5 Tunnel China Routes
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include <sys/stat.h>

#include "hev-chnroutes.h"
#include "hev-logger.h"
#include "hev-config.h"
#include "route/router/hev-radix-tree.h"

/* Global instance */
static HevChnroutes *g_chnroutes = NULL;

/* Embedded China IP ranges (partial list) */
static const char *embedded_china_ips[] = {
    /* Major China network segments */
    "1.0.1.0/24",
    "1.0.2.0/23",
    "1.0.8.0/21",
    "1.0.32.0/19",
    "1.0.64.0/18",
    "1.0.128.0/17",
    "1.1.0.0/24",
    "1.1.2.0/23",
    "1.1.4.0/22",
    "1.1.8.0/21",
    "1.1.16.0/20",
    "1.1.32.0/19",
    "1.1.64.0/18",
    "1.1.128.0/17",
    "1.2.0.0/23",
    "1.2.2.0/24",
    "1.2.4.0/22",
    "1.2.8.0/21",
    "1.2.16.0/20",
    "1.2.32.0/19",
    "1.2.64.0/18",
    "1.2.128.0/17",
    "1.3.0.0/16",
    "1.4.0.0/15",
    "1.8.0.0/16",
    "1.10.0.0/15",
    "1.12.0.0/14",
    "1.16.0.0/12",
    "1.32.0.0/11",
    "1.64.0.0/10",
    "1.128.0.0/9",
    /* Add more as needed... */
    "14.0.0.0/8",
    "27.0.0.0/8",
    "36.0.0.0/8",
    "39.0.0.0/8",
    "42.0.0.0/8",
    "49.0.0.0/8",
    "58.0.0.0/8",
    "59.0.0.0/8",
    "60.0.0.0/8",
    "61.0.0.0/8",
    "101.0.0.0/8",
    "103.0.0.0/8",
    "106.0.0.0/8",
    "110.0.0.0/8",
    "111.0.0.0/8",
    "112.0.0.0/8",
    "113.0.0.0/8",
    "114.0.0.0/8",
    "115.0.0.0/8",
    "116.0.0.0/8",
    "117.0.0.0/8",
    "118.0.0.0/8",
    "119.0.0.0/8",
    "120.0.0.0/8",
    "121.0.0.0/8",
    "122.0.0.0/8",
    "123.0.0.0/8",
    "124.0.0.0/8",
    "125.0.0.0/8",
    NULL
};


/* Create new chnroutes instance */
HevChnroutes *
hev_chnroutes_new (void)
{
    HevChnroutes *chnroutes = malloc (sizeof (HevChnroutes));
    if (!chnroutes)
        return NULL;

    memset (chnroutes, 0, sizeof (HevChnroutes));

    /* Initialize radix tree for IPv4 */
    chnroutes->ipv4_tree = hev_radix_tree_new ();
    if (!chnroutes->ipv4_tree) {
        free (chnroutes);
        return NULL;
    }

    return chnroutes;
}

/* Free chnroutes instance */
void
hev_chnroutes_free (HevChnroutes *chnroutes)
{
    if (!chnroutes)
        return;

    /* Free radix tree */
    if (chnroutes->ipv4_tree)
        hev_radix_tree_free (chnroutes->ipv4_tree, NULL);

    free (chnroutes);
}

/* Load China routes from file */
int
hev_chnroutes_load (HevChnroutes *chnroutes, const char *file_path)
{
    if (!chnroutes || !file_path)
        return -1;

    FILE *fp = fopen (file_path, "r");
    if (!fp) {
        LOG_E ("Failed to open chnroutes file: %s", file_path);
        return -1;
    }

    char line[256];
    int loaded = 0;

    while (fgets (line, sizeof (line), fp)) {
        /* Remove newline and comments */
        char *newline = strchr (line, '\n');
        if (newline)
            *newline = '\0';
        char *comment = strchr (line, '#');
        if (comment)
            *comment = '\0';

        /* Skip empty lines */
        if (line[0] == '\0')
            continue;

        /* Parse CIDR and insert into radix tree */
        uint32_t ip;
        int prefix_len;
        if (hev_radix_tree_parse_cidr (line, &ip, &prefix_len) == 0) {
            if (hev_radix_tree_insert_ipv4 (chnroutes->ipv4_tree, ip, prefix_len, chnroutes) == 0) {
                loaded++;
                chnroutes->route_count++;
            }
        }
    }

    fclose (fp);
    chnroutes->loaded = 1;

    LOG_D ("Loaded %d China IP ranges from file: %s", loaded, file_path);
    return 0;
}

/* Load default embedded China routes */
int
hev_chnroutes_load_default (HevChnroutes *chnroutes)
{
    if (!chnroutes)
        return -1;

    int loaded = 0;
    for (int i = 0; embedded_china_ips[i]; i++) {
        uint32_t ip;
        int prefix_len;
        if (hev_radix_tree_parse_cidr (embedded_china_ips[i], &ip, &prefix_len) == 0) {
            if (hev_radix_tree_insert_ipv4 (chnroutes->ipv4_tree, ip, prefix_len, chnroutes) == 0) {
                loaded++;
                chnroutes->route_count++;
            }
        }
    }

    chnroutes->loaded = 1;
    LOG_D ("Loaded %d default China IP ranges", loaded);
    return 0;
}

/* Check if IP is in China ranges */
int
hev_chnroutes_is_china_ip (HevChnroutes *chnroutes, const char *ip_str)
{
    if (!chnroutes || !chnroutes->loaded || !ip_str)
        return 0;

    struct in_addr addr;
    if (inet_aton (ip_str, &addr) == 0)
        return 0;

    uint32_t ip = ntohl (addr.s_addr);

    /* Use radix tree for efficient lookup */
    void *result = hev_radix_tree_lookup (chnroutes->ipv4_tree, ip);
    if (result) {
        LOG_D ("IP %s matches China routes", ip_str);
        return 1;
    }

    return 0;
}

/* Get singleton instance */
HevChnroutes *
hev_chnroutes_get_instance (void)
{
    if (!g_chnroutes) {
        /* Check if chnroutes is enabled */
        if (!hev_config_get_chnroutes_enabled ()) {
            LOG_D ("Chnroutes is disabled");
            return NULL;
        }

        g_chnroutes = hev_chnroutes_new ();
        if (g_chnroutes) {
            /* Try to load from config file first */
            const char *chnroutes_file = hev_config_get_chnroutes_file ();
            if (chnroutes_file && hev_chnroutes_load (g_chnroutes, chnroutes_file) == 0) {
                LOG_D ("Loaded chnroutes from config file");
            } else {
                /* Use embedded data */
                hev_chnroutes_load_default (g_chnroutes);
                LOG_D ("Using embedded chnroutes");
            }
        }
    }
    return g_chnroutes;
}