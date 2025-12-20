/*
 * Domain Hash Unit Tests
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../src/route/router/hev-domain-hash.h"
#include "test-framework.h"

/* Test basic domain hash creation and operations */
void test_domain_hash_basic(void)
{
    HevDomainHash *hash = hev_domain_hash_new(16, 0);  /* Case insensitive */
    TEST_ASSERT_NOT_NULL(hash, "Create new domain hash");

    /* Initially empty */
    void *value = hev_domain_hash_lookup(hash, "example.com");
    TEST_ASSERT_NULL(value, "Empty hash returns NULL");

    /* Insert and lookup */
    int test_value = 123;
    int result = hev_domain_hash_insert(hash, "example.com", &test_value);
    TEST_ASSERT_EQ(0, result, "Insert domain");

    value = hev_domain_hash_lookup(hash, "example.com");
    TEST_ASSERT_NOT_NULL(value, "Lookup inserted domain");
    TEST_ASSERT_EQ(&test_value, (int*)value, "Found correct value");

    /* Test case insensitive lookup */
    value = hev_domain_hash_lookup(hash, "EXAMPLE.COM");
    TEST_ASSERT_NOT_NULL(value, "Case insensitive lookup works");
    TEST_ASSERT_EQ(&test_value, (int*)value, "Found correct value");

    /* Test contains */
    TEST_ASSERT(hev_domain_hash_contains(hash, "example.com"), "Contains works");
    TEST_ASSERT(!hev_domain_hash_contains(hash, "notfound.com"), "Contains returns false");

    hev_domain_hash_free(hash, NULL);
    printf("    Basic domain hash operations work\n");
}

/* Test exact domain matching */
void test_domain_hash_exact_matching(void)
{
    HevDomainHash *hash = hev_domain_hash_new(16, 0);
    TEST_ASSERT_NOT_NULL(hash, "Create hash for exact matching");

    /* Insert multiple domains */
    int val1 = 1, val2 = 2, val3 = 3;
    hev_domain_hash_insert(hash, "google.com", &val1);
    hev_domain_hash_insert(hash, "facebook.com", &val2);
    hev_domain_hash_insert(hash, "twitter.com", &val3);

    /* Test exact matches */
    void *value = hev_domain_hash_lookup(hash, "google.com");
    TEST_ASSERT_NOT_NULL(value, "Find google.com");
    TEST_ASSERT_EQ(&val1, (int*)value, "Correct value for google.com");

    value = hev_domain_hash_lookup(hash, "facebook.com");
    TEST_ASSERT_NOT_NULL(value, "Find facebook.com");
    TEST_ASSERT_EQ(&val2, (int*)value, "Correct value for facebook.com");

    value = hev_domain_hash_lookup(hash, "twitter.com");
    TEST_ASSERT_NOT_NULL(value, "Find twitter.com");
    TEST_ASSERT_EQ(&val3, (int*)value, "Correct value for twitter.com");

    /* Test non-matches */
    value = hev_domain_hash_lookup(hash, "microsoft.com");
    TEST_ASSERT_NULL(value, "Don't find microsoft.com");

    hev_domain_hash_free(hash, NULL);
    printf("    Exact domain matching works\n");
}

/* Test wildcard domain matching (*.example.com) */
void test_domain_hash_wildcard_matching(void)
{
    HevDomainHash *hash = hev_domain_hash_new(16, 0);
    TEST_ASSERT_NOT_NULL(hash, "Create hash for wildcard matching");

    /* Insert wildcard domains */
    int val1 = 10, val2 = 20;
    hev_domain_hash_insert(hash, "*.google.com", &val1);
    hev_domain_hash_insert(hash, "*.github.com", &val2);

    /* Test wildcard matches */
    void *value = NULL;
    int found = hev_domain_hash_match_wildcard(hash, "mail.google.com", &value);
    TEST_ASSERT(found, "Wildcard matches mail.google.com");
    TEST_ASSERT_EQ(&val1, (int*)value, "Correct value for google wildcard");

    found = hev_domain_hash_match_wildcard(hash, "api.github.com", &value);
    TEST_ASSERT(found, "Wildcard matches api.github.com");
    TEST_ASSERT_EQ(&val2, (int*)value, "Correct value for github wildcard");

    /* Test non-matching wildcards */
    found = hev_domain_hash_match_wildcard(hash, "google.com", &value);
    TEST_ASSERT(!found, "Wildcard doesn't match without subdomain");

    found = hev_domain_hash_match_wildcard(hash, "other.example.com", &value);
    TEST_ASSERT(!found, "Wildcard doesn't match other domains");

    hev_domain_hash_free(hash, NULL);
    printf("    Wildcard domain matching works\n");
}

/* Test suffix domain matching (.example.com) */
void test_domain_hash_suffix_matching(void)
{
    HevDomainHash *hash = hev_domain_hash_new(16, 0);
    TEST_ASSERT_NOT_NULL(hash, "Create hash for suffix matching");

    /* Insert suffix domains */
    int val1 = 100, val2 = 200;
    hev_domain_hash_insert(hash, ".co.uk", &val1);
    hev_domain_hash_insert(hash, ".com.cn", &val2);

    /* Test suffix matches */
    void *value = NULL;
    int found = hev_domain_hash_match_suffix(hash, "example.co.uk", &value);
    TEST_ASSERT(found, "Suffix matches example.co.uk");
    TEST_ASSERT_EQ(&val1, (int*)value, "Correct value for .co.uk");

    found = hev_domain_hash_match_suffix(hash, "test.com.cn", &value);
    TEST_ASSERT(found, "Suffix matches test.com.cn");
    TEST_ASSERT_EQ(&val2, (int*)value, "Correct value for .com.cn");

    /* Test non-matching suffixes */
    found = hev_domain_hash_match_suffix(hash, "example.com", &value);
    TEST_ASSERT(!found, "Suffix doesn't match example.com");

    found = hev_domain_hash_match_suffix(hash, "co.uk", &value);
    TEST_ASSERT(found, "Suffix matches co.uk directly");  /* This should match */

    hev_domain_hash_free(hash, NULL);
    printf("    Suffix domain matching works\n");
}

/* Test domain removal */
void test_domain_hash_removal(void)
{
    HevDomainHash *hash = hev_domain_hash_new(16, 0);
    TEST_ASSERT_NOT_NULL(hash, "Create hash for removal test");

    /* Insert domains */
    int val1 = 1, val2 = 2;
    hev_domain_hash_insert(hash, "keep.com", &val1);
    hev_domain_hash_insert(hash, "remove.com", &val2);

    /* Verify both exist */
    TEST_ASSERT(hev_domain_hash_contains(hash, "keep.com"), "keep.com exists");
    TEST_ASSERT(hev_domain_hash_contains(hash, "remove.com"), "remove.com exists");

    /* Remove one domain */
    int result = hev_domain_hash_remove(hash, "remove.com", NULL);
    TEST_ASSERT_EQ(0, result, "Remove domain succeeds");

    /* Verify removal */
    TEST_ASSERT(hev_domain_hash_contains(hash, "keep.com"), "keep.com still exists");
    TEST_ASSERT(!hev_domain_hash_contains(hash, "remove.com"), "remove.com removed");

    /* Test removing non-existent domain */
    result = hev_domain_hash_remove(hash, "notfound.com", NULL);
    TEST_ASSERT_EQ(-1, result, "Remove non-existent fails");

    hev_domain_hash_free(hash, NULL);
    printf("    Domain removal works\n");
}

/* Test case sensitive vs insensitive */
void test_domain_hash_case_sensitivity(void)
{
    /* Case insensitive hash */
    HevDomainHash *hash_ci = hev_domain_hash_new(16, 0);
    TEST_ASSERT_NOT_NULL(hash_ci, "Create case insensitive hash");

    int val = 42;
    hev_domain_hash_insert(hash_ci, "Example.COM", &val);

    void *value = hev_domain_hash_lookup(hash_ci, "example.com");
    TEST_ASSERT_NOT_NULL(value, "Case insensitive lookup works lowercase");

    value = hev_domain_hash_lookup(hash_ci, "EXAMPLE.COM");
    TEST_ASSERT_NOT_NULL(value, "Case insensitive lookup works uppercase");

    value = hev_domain_hash_lookup(hash_ci, "ExAmPlE.CoM");
    TEST_ASSERT_NOT_NULL(value, "Case insensitive lookup works mixed case");

    /* Case sensitive hash */
    HevDomainHash *hash_cs = hev_domain_hash_new(16, 1);
    TEST_ASSERT_NOT_NULL(hash_cs, "Create case sensitive hash");

    hev_domain_hash_insert(hash_cs, "Example.COM", &val);

    value = hev_domain_hash_lookup(hash_cs, "example.com");
    TEST_ASSERT_NULL(value, "Case sensitive rejects lowercase");

    value = hev_domain_hash_lookup(hash_cs, "Example.COM");
    TEST_ASSERT_NOT_NULL(value, "Case sensitive accepts exact case");

    hev_domain_hash_free(hash_ci, NULL);
    hev_domain_hash_free(hash_cs, NULL);
    printf("    Case sensitivity works correctly\n");
}

/* Test hash table resizing */
void test_domain_hash_resizing(void)
{
    HevDomainHash *hash = hev_domain_hash_new(4, 0);  /* Start very small */
    TEST_ASSERT_NOT_NULL(hash, "Create small hash for resize test");

    /* Insert many domains to trigger resize */
    for (int i = 0; i < 100; i++) {
        char domain[64];
        int *value = malloc(sizeof(int));
        *value = i;
        snprintf(domain, sizeof(domain), "domain%d.com", i);
        hev_domain_hash_insert(hash, domain, value);
    }

    /* Verify we can still find them after resize */
    void *value = hev_domain_hash_lookup(hash, "domain50.com");
    TEST_ASSERT_NOT_NULL(value, "Found domain after resize");
    TEST_ASSERT_EQ(50, *(int*)value, "Correct value after resize");

    value = hev_domain_hash_lookup(hash, "domain99.com");
    TEST_ASSERT_NOT_NULL(value, "Found last domain after resize");
    TEST_ASSERT_EQ(99, *(int*)value, "Correct value for last domain");

    hev_domain_hash_free(hash, free);  /* Free the values */
    printf("    Hash table resizing works\n");
}

/* Main function for domain hash tests */
int main(void)
{
    test_suite_start("Domain Hash Tests");

    test_domain_hash_basic();
    test_domain_hash_exact_matching();
    test_domain_hash_wildcard_matching();
    test_domain_hash_suffix_matching();
    test_domain_hash_removal();
    test_domain_hash_case_sensitivity();
    test_domain_hash_resizing();

    test_suite_end();

    print_test_results();

    return g_test_stats.failed > 0 ? 1 : 0;
}