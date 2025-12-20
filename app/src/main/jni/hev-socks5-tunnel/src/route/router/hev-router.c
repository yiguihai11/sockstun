/*
 * HEV SOCKS5 Tunnel Router
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <arpa/inet.h>
#include <yaml.h>

#include "hev-config.h"
#include "hev-logger.h"
#include "hev-router.h"

/* Radix trie node structure */
typedef struct radix_node_t {
    struct radix_node_t *left;
    struct radix_node_t *right;
    void *value;
} RadixNode;

/* Simple hash function for domain matching */
static unsigned int
domain_hash (const char *str)
{
    unsigned int hash = 5381;
    int c;

    while ((c = *str++))
        hash = ((hash << 5) + hash) + c;

    return hash % 1024;
}

/* Create new radix node */
static RadixNode *
radix_node_new (void)
{
    RadixNode *node = malloc (sizeof (RadixNode));
    if (node) {
        memset (node, 0, sizeof (RadixNode));
    }
    return node;
}

/* Insert IP range into radix trie */
static int
radix_trie_insert (RadixNode **root, const char *ip, int prefix_len, void *value)
{
    RadixNode *node = *root ? *root : radix_node_new ();
    if (!node)
        return -1;

    if (prefix_len == 0) {
        node->value = value;
        *root = node;
        return 0;
    }

    int next_bit = (ip[0] >> (7 - ((prefix_len - 1) % 8))) & 1;
    if (next_bit) {
        if (radix_trie_insert (&node->right, ip, prefix_len - 1, value) < 0)
            return -1;
    } else {
        if (radix_trie_insert (&node->left, ip, prefix_len - 1, value) < 0)
            return -1;
    }

    *root = node;
    return 0;
}

/* Lookup IP in radix trie */
static void *
radix_trie_lookup (RadixNode *root, const char *ip, int prefix_len)
{
    if (!root || prefix_len == 0)
        return root ? root->value : NULL;

    int next_bit = (ip[0] >> (7 - ((prefix_len - 1) % 8))) & 1;
    if (next_bit) {
        return radix_trie_lookup (root->right, ip + (prefix_len - 1) / 8, prefix_len - 1);
    } else {
        return radix_trie_lookup (root->left, ip + (prefix_len - 1) / 8, prefix_len - 1);
    }
}

/* Convert CIDR to IP and prefix */
static int
parse_cidr (const char *cidr, char *ip, int *prefix_len)
{
    char *slash = strchr (cidr, '/');
    if (!slash)
        return -1;

    int len = slash - cidr;
    if (len > 15)
        return -1;

    strncpy (ip, cidr, len);
    ip[len] = '\0';
    *prefix_len = atoi (slash + 1);

    struct in_addr addr;
    if (inet_aton (ip, &addr) == 0)
        return -1;

    memcpy (ip, &addr.s_addr, 4);
    return 0;
}

/* Match pattern against string */
static int
match_pattern (const char *pattern, const char *str)
{
    /* Exact match */
    if (strcmp (pattern, str) == 0)
        return 1;

    /* Wildcard match */
    if (pattern[0] == '*') {
        const char *suffix = pattern + 1;
        size_t str_len = strlen (str);
        size_t suffix_len = strlen (suffix);

        if (suffix_len > str_len)
            return 0;

        return strcmp (str + str_len - suffix_len, suffix) == 0;
    }

    /* Suffix match */
    if (pattern[0] == '.') {
        size_t str_len = strlen (str);
        size_t pattern_len = strlen (pattern);

        if (pattern_len > str_len)
            return 0;

        return strcmp (str + str_len - pattern_len, pattern) == 0;
    }

    return 0;
}

/* Create new router */
HevRouter *
hev_router_new (const char *config_path)
{
    HevRouter *router = malloc (sizeof (HevRouter));
    if (!router)
        return NULL;

    memset (router, 0, sizeof (HevRouter));
    strncpy (router->config_path, config_path, sizeof (router->config_path) - 1);
    router->supports_ipv4 = 1;
    router->supports_ipv6 = 1;

    /* Initialize efficient data structures */
    router->ip_tree = hev_radix_tree_new ();
    router->exact_domains = hev_domain_hash_new (1024, 0);  /* Case insensitive */
    router->wildcard_domains = hev_domain_hash_new (1024, 0);
    router->suffix_domains = hev_domain_hash_new (1024, 0);

    if (!router->ip_tree || !router->exact_domains ||
        !router->wildcard_domains || !router->suffix_domains) {
        hev_router_free (router);
        return NULL;
    }

    /* Add default rule: proxy for all traffic */
    HevRouterRule *default_rule = malloc (sizeof (HevRouterRule));
    if (!default_rule) {
        hev_router_free (router);
        return NULL;
    }

    memset (default_rule, 0, sizeof (HevRouterRule));
    default_rule->action = HEV_ROUTER_ACTION_DENY;
    default_rule->type = HEV_RULE_TYPE_PORT;
    strcpy (default_rule->pattern, "*");
    strcpy (default_rule->description, "Default: use proxy");

    /* Add to all ports as default */
    for (int i = 0; i < 65536; i++) {
        HevRouterRule *port_rule = malloc (sizeof (HevRouterRule));
        if (port_rule) {
            memcpy (port_rule, default_rule, sizeof (HevRouterRule));
            port_rule->next = router->port_rules[i];
            router->port_rules[i] = port_rule;
        }
    }

    free (default_rule);

    LOG_D ("Router created with efficient data structures");
    return router;
}

/* Free router */
void
hev_router_free (HevRouter *router)
{
    if (!router)
        return;

    /* Free rules */
    HevRouterRule *rule = router->rules;
    while (rule) {
        HevRouterRule *next = rule->next;
        free (rule);
        rule = next;
    }

    /* Free port rules */
    for (int i = 0; i < 65536; i++) {
        HevRouterRule *port_rule = router->port_rules[i];
        while (port_rule) {
            HevRouterRule *next = port_rule->next;
            free (port_rule);
            port_rule = next;
        }
    }

    /* Free proxy nodes */
    HevProxyNode *proxy = router->proxy_nodes;
    while (proxy) {
        HevProxyNode *next = proxy->next;
        free (proxy);
        proxy = next;
    }

    /* Free efficient data structures */
    if (router->ip_tree)
        hev_radix_tree_free (router->ip_tree, NULL);
    if (router->exact_domains)
        hev_domain_hash_free (router->exact_domains, NULL);
    if (router->wildcard_domains)
        hev_domain_hash_free (router->wildcard_domains, NULL);
    if (router->suffix_domains)
        hev_domain_hash_free (router->suffix_domains, NULL);

    free (router);
}

/* Add IP/CIDR rule to tree */
int
hev_router_add_ip_rule (HevRouter *router, const char *ip_cidr, HevRouterRule *rule)
{
    uint32_t ip;
    int prefix_len;

    if (hev_radix_tree_parse_cidr (ip_cidr, &ip, &prefix_len) < 0) {
        LOG_W ("Failed to parse CIDR: %s", ip_cidr);
        return -1;
    }

    if (hev_radix_tree_insert_ipv4 (router->ip_tree, ip, prefix_len, rule) < 0) {
        LOG_W ("Failed to insert IP rule: %s", ip_cidr);
        return -1;
    }

    router->rule_count++;
    return 0;
}

/* Add domain rule to hash table */
int
hev_router_add_domain_rule (HevRouter *router, const char *domain, HevRouterRule *rule)
{
    HevDomainHash *hash_table = NULL;

    /* Determine hash table based on pattern type */
    if (domain[0] == '*') {
        /* Wildcard domain (*.example.com) */
        hash_table = router->wildcard_domains;
    } else if (domain[0] == '.') {
        /* Suffix domain (.example.com) */
        hash_table = router->suffix_domains;
    } else {
        /* Exact domain */
        hash_table = router->exact_domains;
    }

    if (!hash_table) {
        LOG_W ("No hash table for domain: %s", domain);
        return -1;
    }

    if (hev_domain_hash_insert (hash_table, domain, rule) < 0) {
        LOG_W ("Failed to insert domain rule: %s", domain);
        return -1;
    }

    router->rule_count++;
    return 0;
}

/* Parse a single router rule from YAML */
static int
hev_router_parse_rule (HevRouter *router, yaml_document_t *doc, yaml_node_t *rule_node)
{
    yaml_node_pair_t *pair;
    HevRouterRule *rule;
    const char *pattern = NULL;
    const char *action_str = NULL;
    const char *type_str = NULL;
    const char *description = "";
    HevRouterAction action = HEV_ROUTER_ACTION_DENY;
    HevRuleType type = HEV_RULE_TYPE_IP;

    if (!rule_node || YAML_MAPPING_NODE != rule_node->type)
        return -1;

    /* Parse rule properties */
    for (pair = rule_node->data.mapping.pairs.start;
         pair < rule_node->data.mapping.pairs.top; pair++) {
        yaml_node_t *key_node, *value_node;
        const char *key;

        if (!pair->key || !pair->value)
            break;

        key_node = yaml_document_get_node (doc, pair->key);
        value_node = yaml_document_get_node (doc, pair->value);

        if (!key_node || YAML_SCALAR_NODE != key_node->type)
            break;

        key = (const char *)key_node->data.scalar.value;

        if (0 == strcmp (key, "pattern")) {
            if (YAML_SCALAR_NODE == value_node->type)
                pattern = (const char *)value_node->data.scalar.value;
        } else if (0 == strcmp (key, "action")) {
            if (YAML_SCALAR_NODE == value_node->type)
                action_str = (const char *)value_node->data.scalar.value;
        } else if (0 == strcmp (key, "type")) {
            if (YAML_SCALAR_NODE == value_node->type)
                type_str = (const char *)value_node->data.scalar.value;
        } else if (0 == strcmp (key, "description")) {
            if (YAML_SCALAR_NODE == value_node->type)
                description = (const char *)value_node->data.scalar.value;
        }
    }

    if (!pattern || !action_str || !type_str) {
        LOG_W ("Invalid rule: missing required fields");
        return -1;
    }

    /* Parse action */
    if (0 == strcmp (action_str, "allow") || 0 == strcmp (action_str, "direct"))
        action = HEV_ROUTER_ACTION_ALLOW;
    else if (0 == strcmp (action_str, "deny") || 0 == strcmp (action_str, "proxy"))
        action = HEV_ROUTER_ACTION_DENY;
    else if (0 == strcmp (action_str, "block"))
        action = HEV_ROUTER_ACTION_BLOCK;

    /* Parse type */
    if (0 == strcmp (type_str, "ip"))
        type = HEV_RULE_TYPE_IP;
    else if (0 == strcmp (type_str, "port"))
        type = HEV_RULE_TYPE_PORT;
    else if (0 == strcmp (type_str, "domain"))
        type = HEV_RULE_TYPE_DOMAIN;
    else if (0 == strcmp (type_str, "wildcard"))
        type = HEV_RULE_TYPE_WILDCARD;

    /* Create rule */
    rule = malloc (sizeof (HevRouterRule));
    if (!rule) {
        LOG_E ("Failed to allocate memory for rule");
        return -1;
    }

    memset (rule, 0, sizeof (HevRouterRule));
    rule->action = action;
    rule->type = type;
    strncpy (rule->pattern, pattern, sizeof (rule->pattern) - 1);
    strncpy (rule->description, description, sizeof (rule->description) - 1);

    /* Add rule to router */
    switch (type) {
    case HEV_RULE_TYPE_IP:
        hev_router_add_ip_rule (router, pattern, rule);
        break;
    case HEV_RULE_TYPE_DOMAIN:
        hev_router_add_domain_rule (router, pattern, rule);
        break;
    case HEV_RULE_TYPE_WILDCARD:
        hev_router_add_domain_rule (router, pattern, rule);
        break;
    case HEV_RULE_TYPE_PORT:
        /* Add port rule to the main list for now */
        rule->next = router->rules;
        router->rules = rule;
        router->rule_count++;
        break;
    default:
        free (rule);
        LOG_W ("Unknown rule type: %s", type_str);
        return -1;
    }

    LOG_D ("Added rule: %s %s %s", action_str, type_str, pattern);
    return 0;
}

/* Load router config from file */
int
hev_router_load_config (HevRouter *router)
{
    yaml_parser_t parser;
    yaml_document_t doc;
    yaml_node_t *root, *router_node = NULL;
    yaml_node_pair_t *pair;
    FILE *fp;
    const char *config_path;
    int res = -1;

    /* Use fixed config path */
    config_path = "/data/data/com.termux/files/home/sockstun/app/src/main/jni/hev-socks5-tunnel/conf/main.yml";

    /* Initialize YAML parser */
    if (!yaml_parser_initialize (&parser)) {
        LOG_E ("Failed to initialize YAML parser");
        return -1;
    }

    /* Open config file */
    fp = fopen (config_path, "r");
    if (!fp) {
        LOG_W ("Failed to open config file: %s", config_path);
        goto exit_free_parser;
    }

    yaml_parser_set_input_file (&parser, fp);
    if (!yaml_parser_load (&parser, &doc)) {
        LOG_E ("Failed to parse config file: %s", config_path);
        goto exit_close_fp;
    }

    /* Find router section */
    root = yaml_document_get_root_node (&doc);
    if (!root || YAML_MAPPING_NODE != root->type) {
        LOG_W ("Invalid config file format");
        goto exit_delete_doc;
    }

    for (pair = root->data.mapping.pairs.start;
         pair < root->data.mapping.pairs.top; pair++) {
        yaml_node_t *key_node;

        if (!pair->key || !pair->value)
            break;

        key_node = yaml_document_get_node (&doc, pair->key);
        if (!key_node || YAML_SCALAR_NODE != key_node->type)
            break;

        if (0 == strcmp ((const char *)key_node->data.scalar.value, "router")) {
            router_node = yaml_document_get_node (&doc, pair->value);
            break;
        }
    }

    if (!router_node) {
        LOG_D ("No router section found in config");
        res = 0;
        goto exit_delete_doc;
    }

    if (YAML_MAPPING_NODE != router_node->type) {
        LOG_W ("Invalid router section format");
        goto exit_delete_doc;
    }

    /* Parse router rules */
    for (pair = router_node->data.mapping.pairs.start;
         pair < router_node->data.mapping.pairs.top; pair++) {
        yaml_node_t *key_node, *value_node;
        const char *key;

        if (!pair->key || !pair->value)
            break;

        key_node = yaml_document_get_node (&doc, pair->key);
        value_node = yaml_document_get_node (&doc, pair->value);

        if (!key_node || YAML_SCALAR_NODE != key_node->type)
            break;

        key = (const char *)key_node->data.scalar.value;

        if (0 == strcmp (key, "rules")) {
            if (YAML_SEQUENCE_NODE == value_node->type) {
                yaml_node_item_t *item;
                for (item = value_node->data.sequence.items.start;
                     item < value_node->data.sequence.items.top; item++) {
                    yaml_node_t *rule_node = yaml_document_get_node (&doc, *item);
                    if (hev_router_parse_rule (router, &doc, rule_node) < 0) {
                        LOG_W ("Failed to parse rule");
                    }
                }
            }
        }
    }

    LOG_D ("Loaded %d rules from config", router->rule_count);
    res = 0;

exit_delete_doc:
    yaml_document_delete (&doc);
exit_close_fp:
    fclose (fp);
exit_free_parser:
    yaml_parser_delete (&parser);
    return res;
}

/* Stage 1: Match IP/port rules (connection phase) */
HevRouterResult
hev_router_match_connection (HevRouter *router, const char *addr, int port)
{
    HevRouterResult result = {0};
    result.action = HEV_ROUTER_ACTION_DENY; /* Default to proxy */

    if (!router)
        return result;

    /* Check port-specific rules first */
    if (port >= 0 && port < 65536) {
        HevRouterRule *rule = router->port_rules[port];
        while (rule) {
            if (strcmp (rule->pattern, "*") != 0) {
                /* Not the default rule */
                result.match = 1;
                result.action = rule->action;
                result.rule = rule;
                if (rule->proxy_node[0])
                    strcpy (result.proxy_node, rule->proxy_node);
                return result;
            }
            rule = rule->next;
        }
    }

    /* Check IP rules using radix tree for efficient CIDR matching */
    if (addr) {
        struct in_addr ip_addr;
        if (inet_aton (addr, &ip_addr)) {
            uint32_t ip = ntohl (ip_addr.s_addr);

            /* Check IP rules tree */
            if (router->ip_tree) {
                HevRouterRule *rule = (HevRouterRule *)hev_radix_tree_lookup (router->ip_tree, ip);
                if (rule) {
                    result.match = 1;
                    result.action = rule->action;
                    result.rule = rule;
                    if (rule->proxy_node[0])
                        strcpy (result.proxy_node, rule->proxy_node);
                    LOG_D ("IP matched rule: %s -> action=%d", addr, result.action);
                    return result;
                }
            }
        }
    }

    return result;
}

/* Stage 2: Match domain rules after traffic detection */
HevRouterResult
hev_router_match_domain (HevRouter *router, const char *hostname, int port)
{
    HevRouterResult result = {0};
    result.action = HEV_ROUTER_ACTION_DENY; /* Default to proxy */

    if (!router || !hostname)
        return result;

    void *rule_value = NULL;
    HevRouterRule *rule = NULL;

    /* Check exact domain match using hash table */
    if (router->exact_domains) {
        rule_value = hev_domain_hash_lookup (router->exact_domains, hostname);
        if (rule_value) {
            rule = (HevRouterRule *)rule_value;
            result.match = 1;
            result.action = rule->action;
            result.rule = rule;
            if (rule->proxy_node[0])
                strcpy (result.proxy_node, rule->proxy_node);
            LOG_D ("Domain matched exact: %s -> action=%d", hostname, result.action);
            return result;
        }
    }

    /* Check wildcard domains using hash table (*.example.com) */
    if (router->wildcard_domains) {
        if (hev_domain_hash_match_wildcard (router->wildcard_domains, hostname, &rule_value)) {
            rule = (HevRouterRule *)rule_value;
            result.match = 1;
            result.action = rule->action;
            result.rule = rule;
            if (rule->proxy_node[0])
                strcpy (result.proxy_node, rule->proxy_node);
            LOG_D ("Domain matched wildcard: %s -> action=%d", hostname, result.action);
            return result;
        }
    }

    /* Check suffix domains using hash table (.example.com) */
    if (router->suffix_domains) {
        if (hev_domain_hash_match_suffix (router->suffix_domains, hostname, &rule_value)) {
            rule = (HevRouterRule *)rule_value;
            result.match = 1;
            result.action = rule->action;
            result.rule = rule;
            if (rule->proxy_node[0])
                strcpy (result.proxy_node, rule->proxy_node);
            LOG_D ("Domain matched suffix: %s -> action=%d", hostname, result.action);
            return result;
        }
    }

      return result;
}

/* Legacy function for backward compatibility - combines both phases */
HevRouterResult
hev_router_match_rule (HevRouter *router, const char *addr,
                        const char *hostname, int port)
{
    /* First try connection phase rules (IP/port) */
    HevRouterResult result = hev_router_match_connection (router, addr, port);

    /* If hostname is available, also check domain rules */
    if (hostname && hostname[0]) {
        HevRouterResult domain_result = hev_router_match_domain (router, hostname, port);
        if (domain_result.match) {
            /* Domain rules have priority over connection rules */
            return domain_result;
        }
    }

    return result;
}

/* Get proxy node by name */
HevProxyNode *
hev_router_get_proxy_node (HevRouter *router, const char *name)
{
    if (!router || !name)
        return NULL;

    HevProxyNode *proxy = router->proxy_nodes;
    while (proxy) {
        if (strcmp (proxy->name, name) == 0 && proxy->enabled) {
            return proxy;
        }
        proxy = proxy->next;
    }

    return NULL;
}

/* Get default proxy node */
HevProxyNode *
hev_router_get_default_proxy (HevRouter *router)
{
    if (!router)
        return NULL;

    HevProxyNode *proxy = router->proxy_nodes;
    while (proxy) {
        if (proxy->enabled && strstr (proxy->type, "socks5")) {
            return proxy;
        }
        proxy = proxy->next;
    }

    return NULL;
}