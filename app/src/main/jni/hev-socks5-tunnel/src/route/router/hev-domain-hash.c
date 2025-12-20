/*
 * HEV SOCKS5 Tunnel Domain Hash Table
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#include "hev-domain-hash.h"
#include "hev-logger.h"

/* Default hash table size */
#define DEFAULT_HASH_SIZE 1024

/* Simple hash function for domains */
static unsigned int
hev_domain_hash_func (const char *key, int case_sensitive)
{
    unsigned int hash = 5381;
    int c;

    while ((c = *key++)) {
        if (!case_sensitive)
            c = tolower (c);
        hash = ((hash << 5) + hash) + c;
    }

    return hash;
}

/* Convert string to lowercase */
static void
hev_domain_to_lower (char *str)
{
    while (*str) {
        *str = tolower (*str);
        str++;
    }
}

/* Create hash table entry */
static HevDomainEntry *
hev_domain_entry_new (const char *key, void *value, unsigned int hash)
{
    HevDomainEntry *entry = malloc (sizeof (HevDomainEntry));
    if (!entry)
        return NULL;

    entry->key = malloc (strlen (key) + 1);
    if (!entry->key) {
        free (entry);
        return NULL;
    }

    strcpy (entry->key, key);
    entry->value = value;
    entry->next = NULL;
    entry->hash = hash;

    return entry;
}

/* Free hash table entry */
static void
hev_domain_entry_free (HevDomainEntry *entry, void (*free_func) (void *))
{
    if (!entry)
        return;

    if (entry->key)
        free (entry->key);

    if (free_func && entry->value)
        free_func (entry->value);

    free (entry);
}

/* Create new hash table */
HevDomainHash *
hev_domain_hash_new (int size, int case_sensitive)
{
    HevDomainHash *hash;

    if (size <= 0)
        size = DEFAULT_HASH_SIZE;

    hash = malloc (sizeof (HevDomainHash));
    if (!hash)
        return NULL;

    hash->buckets = calloc (size, sizeof (HevDomainEntry *));
    if (!hash->buckets) {
        free (hash);
        return NULL;
    }

    hash->size = size;
    hash->count = 0;
    hash->case_sensitive = case_sensitive;

    return hash;
}

/* Free hash table */
void
hev_domain_hash_free (HevDomainHash *hash, void (*free_func) (void *))
{
    if (!hash)
        return;

    hev_domain_hash_clear (hash, free_func);
    free (hash->buckets);
    free (hash);
}

/* Clear hash table */
void
hev_domain_hash_clear (HevDomainHash *hash, void (*free_func) (void *))
{
    if (!hash)
        return;

    for (int i = 0; i < hash->size; i++) {
        HevDomainEntry *entry = hash->buckets[i];
        while (entry) {
            HevDomainEntry *next = entry->next;
            hev_domain_entry_free (entry, free_func);
            entry = next;
        }
        hash->buckets[i] = NULL;
    }

    hash->count = 0;
}

/* Insert key-value pair */
int
hev_domain_hash_insert (HevDomainHash *hash, const char *key, void *value)
{
    if (!hash || !key)
        return -1;

    /* Check for existing entry */
    void *existing = hev_domain_hash_lookup (hash, key);
    if (existing) {
        /* Update existing entry */
        return hev_domain_hash_remove (hash, key, NULL) ||
               hev_domain_hash_insert (hash, key, value);
    }

    unsigned int h = hev_domain_hash_func (key, hash->case_sensitive);
    int index = h % hash->size;

    HevDomainEntry *entry = hev_domain_entry_new (key, value, h);
    if (!entry)
        return -1;

    entry->next = hash->buckets[index];
    hash->buckets[index] = entry;
    hash->count++;

    /* Check if we need to resize */
    if (hash->count > hash->size * 0.75) {
        hev_domain_hash_resize (hash, hash->size * 2);
    }

    return 0;
}

/* Lookup key */
void *
hev_domain_hash_lookup (HevDomainHash *hash, const char *key)
{
    if (!hash || !key)
        return NULL;

    unsigned int h = hev_domain_hash_func (key, hash->case_sensitive);
    int index = h % hash->size;

    HevDomainEntry *entry = hash->buckets[index];
    while (entry) {
        if (entry->hash == h) {
            int cmp;
            if (hash->case_sensitive)
                cmp = strcmp (entry->key, key);
            else
                cmp = strcasecmp (entry->key, key);

            if (cmp == 0) {
                return entry->value;
            }
        }
        entry = entry->next;
    }

    return NULL;
}

/* Remove key */
int
hev_domain_hash_remove (HevDomainHash *hash, const char *key, void (*free_func) (void *))
{
    if (!hash || !key)
        return -1;

    unsigned int h = hev_domain_hash_func (key, hash->case_sensitive);
    int index = h % hash->size;

    HevDomainEntry *prev = NULL;
    HevDomainEntry *entry = hash->buckets[index];

    while (entry) {
        if (entry->hash == h) {
            int cmp;
            if (hash->case_sensitive)
                cmp = strcmp (entry->key, key);
            else
                cmp = strcasecmp (entry->key, key);

            if (cmp == 0) {
                if (prev)
                    prev->next = entry->next;
                else
                    hash->buckets[index] = entry->next;

                hev_domain_entry_free (entry, free_func);
                hash->count--;
                return 0;
            }
        }
        prev = entry;
        entry = entry->next;
    }

    return -1;
}

/* Check if key exists */
int
hev_domain_hash_contains (HevDomainHash *hash, const char *key)
{
    return hev_domain_hash_lookup (hash, key) != NULL;
}

/* Match wildcard patterns (e.g., *.example.com) */
int
hev_domain_hash_match_wildcard (HevDomainHash *hash, const char *domain, void **value)
{
    if (!hash || !domain)
        return -1;

    char *domain_copy = strdup (domain);
    if (!domain_copy)
        return -1;

    if (!hash->case_sensitive)
        hev_domain_to_lower (domain_copy);

    /* Try exact match first */
    *value = hev_domain_hash_lookup (hash, domain_copy);
    if (*value) {
        free (domain_copy);
        return 1;
    }

    /* Try wildcard matches */
    char *dot = domain_copy;
    while ((dot = strchr (dot, '.')) != NULL) {
        *dot = '\0';

        /* Try *.domain.com pattern */
        char wildcard[256];
        snprintf (wildcard, sizeof (wildcard), "*.%s", dot + 1);

        *value = hev_domain_hash_lookup (hash, wildcard);
        if (*value) {
            free (domain_copy);
            return 1;
        }

        dot++;
    }

    free (domain_copy);
    return 0;
}

/* Match suffix patterns (e.g., .example.com) */
int
hev_domain_hash_match_suffix (HevDomainHash *hash, const char *domain, void **value)
{
    if (!hash || !domain)
        return -1;

    int domain_len = strlen (domain);
    char *domain_copy = strdup (domain);
    if (!domain_copy)
        return -1;

    if (!hash->case_sensitive)
        hev_domain_to_lower (domain_copy);

    /* Check suffix matches */
    for (int i = 0; i < domain_len; i++) {
        if (domain_copy[i] == '.') {
            char *suffix = &domain_copy[i];

            /* Try with leading dot */
            *value = hev_domain_hash_lookup (hash, suffix);
            if (*value) {
                free (domain_copy);
                return 1;
            }
        }
    }

    /* Try to match domain against suffix rules with dots */
    /* For example, domain "co.uk" should match suffix ".co.uk" */
    int hash_size = hash->size;
    for (int i = 0; i < hash_size; i++) {
        HevDomainEntry *entry = hash->buckets[i];
        while (entry) {
            if (entry->key && entry->key[0] == '.') {
                /* Check if domain matches the suffix (without the leading dot) */
                if (strcmp (domain_copy, entry->key + 1) == 0) {
                    *value = entry->value;
                    free (domain_copy);
                    return 1;
                }
            }
            entry = entry->next;
        }
    }

    /* Try exact match without dot */
    *value = hev_domain_hash_lookup (hash, domain_copy);
    if (*value) {
        free (domain_copy);
        return 1;
    }

    free (domain_copy);
    return 0;
}

/* Resize hash table */
int
hev_domain_hash_resize (HevDomainHash *hash, int new_size)
{
    if (!hash || new_size <= 0)
        return -1;

    HevDomainEntry **new_buckets = calloc (new_size, sizeof (HevDomainEntry *));
    if (!new_buckets)
        return -1;

    /* Rehash all entries */
    for (int i = 0; i < hash->size; i++) {
        HevDomainEntry *entry = hash->buckets[i];
        while (entry) {
            HevDomainEntry *next = entry->next;
            int new_index = entry->hash % new_size;
            entry->next = new_buckets[new_index];
            new_buckets[new_index] = entry;
            entry = next;
        }
    }

    free (hash->buckets);
    hash->buckets = new_buckets;
    hash->size = new_size;

    LOG_D ("Resized domain hash to %d buckets", new_size);
    return 0;
}