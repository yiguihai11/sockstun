/*
 * HEV SOCKS5 Tunnel Smart Proxy
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "hev-config.h"
#include "hev-logger.h"
#include "hev-smart-proxy.h"
#include "hev-router.h"
#include "hev-traffic-detector.h"
#include "hev-blocked-items-wrapper.h"
#include "hev-direct-tcp.h"
#include "route/router/hev-blocked-items.h"

/* Global smart proxy instance */
static HevSmartProxy *g_smart_proxy = NULL;

/* Probing ports that need traffic detection */
static const int probing_ports[] = {
    80, 443, 25, 465, 587, 993, 995, 143, 110, 21, 22, 23, 53, 853, 8443
};
static const int probing_ports_count = sizeof(probing_ports) / sizeof(probing_ports[0]);

/* Check if port needs traffic detection */
static int
is_probing_port (int port)
{
    for (int i = 0; i < probing_ports_count; i++) {
        if (port == probing_ports[i])
            return 1;
    }
    return 0;
}

/* Create smart proxy instance */
HevSmartProxy *
hev_smart_proxy_new (const char *config_path)
{
    HevSmartProxy *proxy;

    proxy = malloc (sizeof (HevSmartProxy));
    if (!proxy)
        return NULL;

    memset (proxy, 0, sizeof (HevSmartProxy));

    /* Initialize components */
    proxy->router = hev_router_new (config_path);
    if (!proxy->router) {
        free (proxy);
        return NULL;
    }

    proxy->traffic_detector = hev_traffic_detector_new ();
    if (!proxy->traffic_detector) {
        hev_router_free (proxy->router);
        free (proxy);
        return NULL;
    }

    proxy->blocked_items = hev_blocked_items_new ();
    if (!proxy->blocked_items) {
        hev_traffic_detector_free (proxy->traffic_detector);
        hev_router_free (proxy->router);
        free (proxy);
        return NULL;
    }

    proxy->enabled = hev_config_get_smart_proxy_enabled ();
    proxy->timeout_ms = hev_config_get_smart_proxy_timeout_ms ();
    proxy->blacklist_expiry_minutes = hev_config_get_blacklist_expiry_minutes ();

    LOG_D ("Smart proxy created: enabled=%d, timeout=%dms",
           proxy->enabled, proxy->timeout_ms);

    return proxy;
}

/* Free smart proxy instance */
void
hev_smart_proxy_free (HevSmartProxy *proxy)
{
    if (!proxy)
        return;

    if (proxy->router)
        hev_router_free (proxy->router);
    if (proxy->traffic_detector)
        hev_traffic_detector_free (proxy->traffic_detector);
    if (proxy->blocked_items)
        hev_blocked_items_free (proxy->blocked_items);

    free (proxy);
}

/* Load configuration */
int
hev_smart_proxy_load_config (HevSmartProxy *proxy)
{
    if (!proxy)
        return -1;

    /* Load router configuration */
    if (hev_router_load_config (proxy->router) < 0) {
        LOG_E ("Failed to load router configuration");
        return -1;
    }

    
    LOG_D ("Smart proxy configuration loaded");

    return 0;
}

/* Save configuration */
int
hev_smart_proxy_save_config (HevSmartProxy *proxy)
{
    if (!proxy)
        return -1;

    
    LOG_D ("Smart proxy configuration saved");

    return 0;
}

/* Stage 1: Handle initial connection decision */
HevSmartProxyResult
hev_smart_proxy_connect (HevSmartProxy *proxy, const char *addr, int port)
{
    HevSmartProxyResult result = {0};
    HevRouterResult router_result;

    if (!proxy || !addr) {
        result.action = HEV_ROUTER_ACTION_DENY;  /* Default to proxy */
        return result;
    }

    /* Stage 1: Check IP/port rules */
    router_result = hev_router_match_connection (proxy->router, addr, port);

    /* If rule matched, use it */
    if (router_result.match) {
        result.action = router_result.action;
        result.rule = router_result.rule;
        LOG_D ("Stage 1 rule matched: addr=%s, port=%d, action=%d",
               addr, port, result.action);
        return result;
    }

    /* If smart proxy is disabled, default to proxy */
    if (!proxy->enabled) {
        result.action = HEV_ROUTER_ACTION_DENY;
        LOG_D ("Smart proxy disabled, using proxy: addr=%s, port=%d", addr, port);
        return result;
    }

    /* For non-probing ports, make intelligent decision */
    if (!is_probing_port (port)) {
        /* Check if in blocked items */
        if (hev_blocked_items_is_blocked (proxy->blocked_items, addr)) {
            result.action = HEV_ROUTER_ACTION_DENY;  /* Use proxy */
            LOG_D ("Address in blacklist, using proxy: addr=%s", addr);
            return result;
        }

        /* Default to direct for non-probing ports */
        result.action = HEV_ROUTER_ACTION_ALLOW;
        LOG_D ("Default direct for non-probing port: addr=%s, port=%d", addr, port);
        return result;
    }

    /* For probing ports, we need traffic detection */
    result.action = HEV_ROUTER_ACTION_ALLOW;  /* Start with direct */
    result.needs_detection = 1;
    LOG_D ("Probing port, needs traffic detection: addr=%s, port=%d", addr, port);

    return result;
}

/* Stage 2: Handle traffic detection and final decision */
HevSmartProxyResult
hev_smart_proxy_handle_traffic (HevSmartProxy *proxy, const char *addr,
                                const void *data, size_t len)
{
    HevSmartProxyResult result = {0};
    HevTrafficResult *traffic_result;
    HevRouterResult router_result;

    if (!proxy || !addr || !data || len == 0) {
        result.action = HEV_ROUTER_ACTION_DENY;
        return result;
    }

    /* Detect traffic type and extract hostname */
    traffic_result = hev_traffic_detector_detect (proxy->traffic_detector, data, len);
    if (!traffic_result || !traffic_result->hostname[0]) {
        LOG_D ("No hostname detected in traffic");
        result.action = HEV_ROUTER_ACTION_ALLOW;  /* Default to direct */
        return result;
    }

    /* Stage 2: Check domain rules */
    router_result = hev_router_match_domain (proxy->router, traffic_result->hostname, 0);

    /* If domain rule matched, use it */
    if (router_result.match) {
        result.action = router_result.action;
        result.rule = router_result.rule;
        LOG_D ("Stage 2 domain rule matched: hostname=%s, action=%d",
               traffic_result->hostname, result.action);
        goto cleanup;
    }

    /* Check if hostname is in blocked items */
    if (hev_blocked_items_is_blocked (proxy->blocked_items, traffic_result->hostname)) {
        result.action = HEV_ROUTER_ACTION_DENY;  /* Use proxy */
        LOG_D ("Hostname in blacklist, using proxy: hostname=%s", traffic_result->hostname);
        goto cleanup;
    }

    /* Default to direct for unknown domains */
    result.action = HEV_ROUTER_ACTION_ALLOW;
    LOG_D ("Default direct for unknown domain: hostname=%s", traffic_result->hostname);

cleanup:
    if (traffic_result)
        free (traffic_result);
    return result;
}

/* Record connection failure for dynamic blacklisting */
void
hev_smart_proxy_record_failure (HevSmartProxy *proxy, const char *addr,
                                const char *hostname, HevFailureReason reason)
{
    if (!proxy || (!addr && !hostname))
        return;

    hev_blocked_items_add (proxy->blocked_items, addr, hostname, reason,
                          proxy->blacklist_expiry_minutes);

    LOG_D ("Recorded failure: addr=%s, hostname=%s, reason=%d",
           addr ? addr : "NULL", hostname ? hostname : "NULL", reason);
}

/* Get global smart proxy instance */
HevSmartProxy *
hev_smart_proxy_get_instance (void)
{
    return g_smart_proxy;
}

/* Set global smart proxy instance */
void
hev_smart_proxy_set_instance (HevSmartProxy *proxy)
{
    g_smart_proxy = proxy;
}