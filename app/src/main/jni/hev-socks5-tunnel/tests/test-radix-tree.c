/*
 * Radix Tree Unit Tests
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include "../src/route/router/hev-radix-tree.h"
#include "test-framework.h"

/* Test basic radix tree creation and destruction */
void test_radix_tree_basic(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create new radix tree");

    /* Initially empty */
    void *value = hev_radix_tree_lookup(tree, 0x01000000);  /* 1.0.0.0 */
    TEST_ASSERT_NULL(value, "Empty tree returns NULL for lookup");

    hev_radix_tree_free(tree, NULL);
    printf("    Basic creation/destruction works\n");
}

/* Test IPv4 CIDR insertion and lookup */
void test_radix_tree_ipv4_cidr(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create tree for CIDR test");

    /* Insert a /24 network */
    uint32_t ip = 0x01000000;  /* 1.0.0.0 */
    int prefix_len = 24;
    int test_value = 42;

    int result = hev_radix_tree_insert_ipv4(tree, ip, prefix_len, &test_value);
    TEST_ASSERT_EQ(0, result, "Insert /24 network");

    /* Test exact match */
    void *found = hev_radix_tree_lookup(tree, ip);
    TEST_ASSERT_NOT_NULL(found, "Lookup exact IP");
    TEST_ASSERT_EQ(&test_value, (int*)found, "Found correct value");

    /* Test IPs within the /24 network */
    found = hev_radix_tree_lookup(tree, 0x010000FF);  /* 1.0.0.255 */
    TEST_ASSERT_NOT_NULL(found, "Lookup IP within /24");
    TEST_ASSERT_EQ(&test_value, (int*)found, "Found correct value");

    /* Test IPs outside the /24 network */
    found = hev_radix_tree_lookup(tree, 0x01000100);  /* 1.0.1.0 */
    TEST_ASSERT_NULL(found, "Lookup IP outside /24");

    hev_radix_tree_free(tree, NULL);
    printf("    CIDR insertion/lookup works\n");
}

/* Test overlapping CIDR ranges */
void test_radix_tree_overlapping_ranges(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create tree for overlap test");

    /* Insert a /16 network */
    uint32_t ip_16 = 0xC0A80000;  /* 192.168.0.0 */
    int value_16 = 100;
    hev_radix_tree_insert_ipv4(tree, ip_16, 16, &value_16);

    /* Insert a more specific /24 network within it */
    uint32_t ip_24 = 0xC0A80100;  /* 192.168.1.0 */
    int value_24 = 200;
    hev_radix_tree_insert_ipv4(tree, ip_24, 24, &value_24);

    /* Test that the more specific /24 matches */
    void *found = hev_radix_tree_lookup(tree, ip_24);  /* 192.168.1.0 */
    TEST_ASSERT_NOT_NULL(found, "Lookup in specific /24");
    TEST_ASSERT_EQ(&value_24, (int*)found, "Found more specific /24 value");

    /* Test that other IPs in the /16 match the /16 rule */
    found = hev_radix_tree_lookup(tree, 0xC0A80200);  /* 192.168.2.0 */
    TEST_ASSERT_NOT_NULL(found, "Lookup in broader /16");
    TEST_ASSERT_EQ(&value_16, (int*)found, "Found broader /16 value");

    /* Test multiple specific subnets */
    uint32_t ip_24_2 = 0xC0A80200;  /* 192.168.2.0 */
    int value_24_2 = 300;
    hev_radix_tree_insert_ipv4(tree, ip_24_2, 24, &value_24_2);

    found = hev_radix_tree_lookup(tree, ip_24_2);
    TEST_ASSERT_NOT_NULL(found, "Lookup second specific /24");
    TEST_ASSERT_EQ(&value_24_2, (int*)found, "Found second /24 value");

    hev_radix_tree_free(tree, NULL);
    printf("    Overlapping CIDR ranges work correctly\n");
}

/* Test CIDR parsing */
void test_radix_tree_cidr_parsing(void)
{
    uint32_t ip;
    int prefix_len;

    /* Valid CIDR notations */
    TEST_ASSERT_EQ(0, hev_radix_tree_parse_cidr("192.168.1.0/24", &ip, &prefix_len),
                   "Parse valid CIDR /24");
    TEST_ASSERT_EQ(0xC0A80100, ip, "Correct IP parsed");
    TEST_ASSERT_EQ(24, prefix_len, "Correct prefix parsed");

    TEST_ASSERT_EQ(0, hev_radix_tree_parse_cidr("10.0.0.0/8", &ip, &prefix_len),
                   "Parse valid CIDR /8");
    TEST_ASSERT_EQ(0x0A000000, ip, "Correct IP parsed");
    TEST_ASSERT_EQ(8, prefix_len, "Correct prefix parsed");

    TEST_ASSERT_EQ(0, hev_radix_tree_parse_cidr("255.255.255.255/32", &ip, &prefix_len),
                   "Parse valid CIDR /32");
    TEST_ASSERT_EQ(0xFFFFFFFF, ip, "Correct IP parsed");
    TEST_ASSERT_EQ(32, prefix_len, "Correct prefix parsed");

    /* Invalid CIDR notations */
    TEST_ASSERT_EQ(-1, hev_radix_tree_parse_cidr("invalid", &ip, &prefix_len),
                   "Reject invalid format");
    TEST_ASSERT_EQ(-1, hev_radix_tree_parse_cidr("192.168.1.0/33", &ip, &prefix_len),
                   "Reject invalid prefix length");
    TEST_ASSERT_EQ(-1, hev_radix_tree_parse_cidr("256.256.256.256/24", &ip, &prefix_len),
                   "Reject invalid IP");

    printf("    CIDR parsing works correctly\n");
}

/* Test edge cases */
void test_radix_tree_edge_cases(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create tree for edge cases");

    /* Insert /0 (all IPs) */
    int value_all = 999;
    hev_radix_tree_insert_ipv4(tree, 0, 0, &value_all);

    /* All IPs should match */
    void *found = hev_radix_tree_lookup(tree, 0x12345678);
    TEST_ASSERT_NOT_NULL(found, "/0 matches all IPs");
    TEST_ASSERT_EQ(&value_all, (int*)found, "Correct value for /0");

    /* Insert more specific after /0 */
    uint32_t specific_ip = 0x08080808;  /* 8.8.8.8 */
    int value_specific = 888;
    hev_radix_tree_insert_ipv4(tree, specific_ip, 32, &value_specific);

    /* Specific IP should match the more specific rule */
    found = hev_radix_tree_lookup(tree, specific_ip);
    TEST_ASSERT_NOT_NULL(found, "Specific IP matches");
    TEST_ASSERT_EQ(&value_specific, (int*)found, "More specific rule takes precedence");

    /* Other IPs should still match /0 */
    found = hev_radix_tree_lookup(tree, 0x12345678);
    TEST_ASSERT_NOT_NULL(found, "Other IP matches /0");
    TEST_ASSERT_EQ(&value_all, (int*)found, "Correct value for /0");

    hev_radix_tree_free(tree, NULL);
    printf("    Edge cases handled correctly\n");
}

/* Main function for radix tree tests */
int main(void)
{
    test_suite_start("Radix Tree Tests");

    test_radix_tree_basic();
    test_radix_tree_ipv4_cidr();
    test_radix_tree_overlapping_ranges();
    test_radix_tree_cidr_parsing();
    test_radix_tree_edge_cases();

    test_suite_end();

    print_test_results();

    return g_test_stats.failed > 0 ? 1 : 0;
}