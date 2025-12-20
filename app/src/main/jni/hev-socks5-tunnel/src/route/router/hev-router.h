/*
 * HEV SOCKS5 Tunnel Router
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_ROUTER_H__
#define __HEV_ROUTER_H__

#include <lwip/ip_addr.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Router action types */
typedef enum {
    HEV_ROUTER_ACTION_ALLOW,   /* Direct connection */
    HEV_ROUTER_ACTION_DENY,    /* Use proxy (default) */
    HEV_ROUTER_ACTION_BLOCK,   /* Block connection */
    HEV_ROUTER_ACTION_PROXY    /* Through specified proxy node (not supported yet) */
} HevRouterAction;

/* Traffic types */
typedef enum {
    HEV_TRAFFIC_TYPE_UNKNOWN,
    HEV_TRAFFIC_TYPE_HTTP,
    HEV_TRAFFIC_TYPE_HTTPS,
    HEV_TRAFFIC_TYPE_OTHER
} HevTrafficType;

/* Traffic detection result */
typedef struct {
    HevTrafficType type;
    char hostname[256];
    char sni[256];
    char method[16];
    char path[1024];
} HevTrafficResult;

/* Router rule type */
typedef enum {
    HEV_RULE_TYPE_IP,        /* IP address rules */
    HEV_RULE_TYPE_PORT,      /* Port rules */
    HEV_RULE_TYPE_DOMAIN,    /* Domain rules */
    HEV_RULE_TYPE_WILDCARD   /* Wildcard domain rules */
} HevRuleType;

/* Router rule */
typedef struct hev_router_rule_t {
    HevRouterAction action;
    HevRuleType type;
    char pattern[256];
    char proxy_node[64];
    char description[256];
    struct hev_router_rule_t *next;
} HevRouterRule;

/* Proxy node */
typedef struct hev_proxy_node_t {
    char name[64];
    char type[32];
    char address[256];
    int enabled;
    char username[64];
    char password[64];
    struct hev_proxy_node_t *next;
} HevProxyNode;

/* Router match result */
typedef struct {
    HevRouterAction action;
    int match;
    HevRouterRule *rule;
    char proxy_node[64];
} HevRouterResult;

#include "hev-radix-tree.h"
#include "hev-domain-hash.h"

/* Router structure */
typedef struct hev_router_t {
    HevRouterRule *rules;
    HevProxyNode *proxy_nodes;

    /* Port rules for connection phase */
    HevRouterRule *port_rules[65536];    /* Index by port */

    /* Efficient data structures for matching */
    HevRadixTree *ip_tree;               /* For IP CIDR matching */
    HevDomainHash *exact_domains;        /* For exact domain matching */
    HevDomainHash *wildcard_domains;     /* For wildcard domain matching (*.example.com) */
    HevDomainHash *suffix_domains;        /* For suffix domain matching (.example.com) */

    char config_path[512];
    int supports_ipv4;
    int supports_ipv6;
    int rule_count;
} HevRouter;

/* Traffic detector */
typedef struct hev_traffic_detector_t {
    /* HTTP patterns */
    const char *http_methods[8];

    /* TLS patterns */
    unsigned char tls_client_hello[5];
} HevTrafficDetector;

/* Router functions */
HevRouter *hev_router_new (const char *config_path);
void hev_router_free (HevRouter *router);
int hev_router_load_config (HevRouter *router);

/* Stage 1: Match IP/port rules (connection phase) */
HevRouterResult hev_router_match_connection (HevRouter *router, const char *addr, int port);

/* Stage 2: Match domain rules after traffic detection */
HevRouterResult hev_router_match_domain (HevRouter *router, const char *hostname, int port);

/* Legacy function for backward compatibility */
HevRouterResult hev_router_match_rule (HevRouter *router, const char *addr,
                                        const char *hostname, int port);

HevProxyNode *hev_router_get_proxy_node (HevRouter *router, const char *name);
HevProxyNode *hev_router_get_default_proxy (HevRouter *router);

/* Internal helper functions (for testing) */
int hev_router_add_ip_rule (HevRouter *router, const char *ip_cidr, HevRouterRule *rule);
int hev_router_add_domain_rule (HevRouter *router, const char *domain, HevRouterRule *rule);

/* Traffic detection functions */
HevTrafficDetector *hev_traffic_detector_new (void);
void hev_traffic_detector_free (HevTrafficDetector *detector);
HevTrafficResult *hev_traffic_detector_detect (HevTrafficDetector *detector,
                                                const void *data, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_ROUTER_H__ */