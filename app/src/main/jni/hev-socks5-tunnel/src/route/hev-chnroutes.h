/*
 * HEV SOCKS5 Tunnel China Routes
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_CHNROUTES_H__
#define __HEV_CHNROUTES_H__

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* China IP range structure */
typedef struct hev_china_ip_range_t {
    uint32_t start_ip;
    uint32_t end_ip;
    struct hev_china_ip_range_t *next;
} HevChinaIPRange;

/* China routes manager */
typedef struct hev_chnroutes_t {
    HevRadixTree *ipv4_tree;  /* Use radix tree for efficient IP matching */
    int loaded;
    int route_count;
} HevChnroutes;

/* Chnroutes functions */
HevChnroutes *hev_chnroutes_new (void);
void hev_chnroutes_free (HevChnroutes *chnroutes);

/* Load China routes from file */
int hev_chnroutes_load (HevChnroutes *chnroutes, const char *file_path);

/* Load default embedded China routes */
int hev_chnroutes_load_default (HevChnroutes *chnroutes);

/* Check if IP is in China ranges */
int hev_chnroutes_is_china_ip (HevChnroutes *chnroutes, const char *ip_str);

/* Get singleton instance */
HevChnroutes *hev_chnroutes_get_instance (void);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_CHNROUTES_H__ */