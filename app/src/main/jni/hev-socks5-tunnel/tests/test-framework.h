/*
 * Simple Test Framework
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __TEST_FRAMEWORK_H__
#define __TEST_FRAMEWORK_H__

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/* Test statistics */
typedef struct {
    int total;
    int passed;
    int failed;
} TestStats;

/* Global test statistics */
extern TestStats g_test_stats;

/* Test macros */
#define TEST_ASSERT(condition, message) \
    do { \
        g_test_stats.total++; \
        if (condition) { \
            g_test_stats.passed++; \
            printf("  ✓ %s\n", message); \
        } else { \
            g_test_stats.failed++; \
            printf("  ✗ %s\n", message); \
            printf("    at %s:%d\n", __FILE__, __LINE__); \
        } \
    } while(0)

#define TEST_ASSERT_EQ(expected, actual, message) \
    TEST_ASSERT((expected) == (actual), message)

#define TEST_ASSERT_STR(expected, actual, message) \
    TEST_ASSERT(strcmp(expected, actual) == 0, message)

#define TEST_ASSERT_NULL(ptr, message) \
    TEST_ASSERT((ptr) == NULL, message)

#define TEST_ASSERT_NOT_NULL(ptr, message) \
    TEST_ASSERT((ptr) != NULL, message)

/* Test suite functions */
void test_suite_start(const char *suite_name);
void test_suite_end(void);
void print_test_results(void);

/* Individual test functions */
void test_radix_tree_basic(void);
void test_radix_tree_ipv4_cidr(void);
void test_radix_tree_overlapping_ranges(void);
void test_domain_hash_basic(void);
void test_domain_hash_wildcard(void);
void test_domain_hash_suffix(void);
void test_domain_hash_case_insensitive(void);
void test_router_ip_matching(void);
void test_router_domain_matching(void);

#endif /* __TEST_FRAMEWORK_H__ */