/*
 * HEV SOCKS5 Tunnel Domain Hash Table
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_DOMAIN_HASH_H__
#define __HEV_DOMAIN_HASH_H__

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Hash table entry */
typedef struct hev_domain_entry_t {
    char *key;
    void *value;
    struct hev_domain_entry_t *next;
    unsigned int hash;
} HevDomainEntry;

/* Hash table */
typedef struct hev_domain_hash_t {
    HevDomainEntry **buckets;
    int size;
    int count;
    int case_sensitive;
} HevDomainHash;

/* Hash table functions */
HevDomainHash *hev_domain_hash_new (int size, int case_sensitive);
void hev_domain_hash_free (HevDomainHash *hash, void (*free_func) (void *));

/* Insert key-value pair */
int hev_domain_hash_insert (HevDomainHash *hash, const char *key, void *value);

/* Lookup key */
void *hev_domain_hash_lookup (HevDomainHash *hash, const char *key);

/* Remove key */
int hev_domain_hash_remove (HevDomainHash *hash, const char *key, void (*free_func) (void *));

/* Check if key exists */
int hev_domain_hash_contains (HevDomainHash *hash, const char *key);

/* Get all keys */
int hev_domain_hash_get_keys (HevDomainHash *hash, char **keys, int max_count);

/* Clear hash table */
void hev_domain_hash_clear (HevDomainHash *hash, void (*free_func) (void *));

/* Resize hash table */
int hev_domain_hash_resize (HevDomainHash *hash, int new_size);

/* Get hash table statistics */
void hev_domain_hash_get_stats (HevDomainHash *hash, int *bucket_count, int *entry_count);

/* Domain matching functions */
int hev_domain_hash_match_wildcard (HevDomainHash *hash, const char *domain, void **value);
int hev_domain_hash_match_suffix (HevDomainHash *hash, const char *domain, void **value);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_DOMAIN_HASH_H__ */