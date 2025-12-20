/* IPv6 Radix Tree Tests */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
#include <inttypes.h>

#include "test-framework.h"
#include "../src/route/router/hev-radix-tree.h"

/* Helper function to convert IPv6 string to bytes */
int str_to_ipv6(const char *str, uint8_t *ip) {
    struct in6_addr addr;
    if (inet_pton(AF_INET6, str, &addr) != 1)
        return -1;
    memcpy(ip, addr.s6_addr, 16);
    return 0;
}

/* Print IPv6 address for debugging */
void print_ipv6(const uint8_t *ip) {
    char buf[INET6_ADDRSTRLEN];
    struct in6_addr addr;
    memcpy(addr.s6_addr, ip, 16);
    inet_ntop(AF_INET6, &addr, buf, sizeof(buf));
    printf("%s", buf);
}

/* Test IPv6 basic operations */
void test_ipv6_basic(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create IPv6 tree");

    uint8_t ipv6_1[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8::1", ipv6_1), "Parse IPv6 address");

    int value = 42;
    int result = hev_radix_tree_insert_ipv6(tree, ipv6_1, 128, &value);
    TEST_ASSERT_EQ(0, result, "Insert IPv6 /128");

    void *found = hev_radix_tree_lookup_ipv6(tree, ipv6_1);
    TEST_ASSERT_NOT_NULL(found, "Lookup exact IPv6");
    TEST_ASSERT_EQ(&value, (int*)found, "Found correct value");

    hev_radix_tree_free(tree, NULL);
    printf("    IPv6 basic operations work\n");
}

/* Test IPv6 CIDR matching */
void test_ipv6_cidr(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create tree for IPv6 CIDR");

    /* Insert a /64 network */
    uint8_t network[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8:1234::", network), "Parse network");

    int net_value = 100;
    int result = hev_radix_tree_insert_ipv6(tree, network, 64, &net_value);
    TEST_ASSERT_EQ(0, result, "Insert IPv6 /64");

    /* Test exact network address */
    void *found = hev_radix_tree_lookup_ipv6(tree, network);
    TEST_ASSERT_NOT_NULL(found, "Network address matches");
    TEST_ASSERT_EQ(&net_value, (int*)found, "Correct value for network");

    /* Test address within /64 */
    uint8_t test_ip1[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8:1234::abcd", test_ip1), "Parse test IP 1");
    found = hev_radix_tree_lookup_ipv6(tree, test_ip1);
    TEST_ASSERT_NOT_NULL(found, "IP within /64 matches");
    TEST_ASSERT_EQ(&net_value, (int*)found, "Correct value for IP in network");

    /* Test address outside /64 */
    uint8_t test_ip2[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8:5678::1", test_ip2), "Parse test IP 2");
    found = hev_radix_tree_lookup_ipv6(tree, test_ip2);
    TEST_ASSERT_NULL(found, "IP outside /64 doesn't match");

    hev_radix_tree_free(tree, NULL);
    printf("    IPv6 CIDR matching works\n");
}

/* Test IPv6 overlapping prefixes */
void test_ipv6_overlap(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create tree for overlap test");

    /* Insert broad /48 */
    uint8_t net48[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8::", net48), "Parse /48 network");
    int val48 = 48;
    hev_radix_tree_insert_ipv6(tree, net48, 48, &val48);

    /* Insert specific /64 */
    uint8_t net64[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8:1234::", net64), "Parse /64 network");
    int val64 = 64;
    hev_radix_tree_insert_ipv6(tree, net64, 64, &val64);

    /* Test that /64 matches more specific rule */
    uint8_t test_ip[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8:1234::1", test_ip), "Parse test IP");
    void *found = hev_radix_tree_lookup_ipv6(tree, test_ip);
    TEST_ASSERT_NOT_NULL(found, "IP matches /64");
    TEST_ASSERT_EQ(&val64, (int*)found, "More specific /64 takes precedence");

    /* Test that other IPs match /48 */
    uint8_t test_ip2[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8::abcd", test_ip2), "Parse second test IP");
    found = hev_radix_tree_lookup_ipv6(tree, test_ip2);
    TEST_ASSERT_NOT_NULL(found, "IP matches /48");
    TEST_ASSERT_EQ(&val48, (int*)found, "Broader /48 applies");

    hev_radix_tree_free(tree, NULL);
    printf("    IPv6 overlapping prefixes work\n");
}

/* Test IPv6 /0 (default route) */
void test_ipv6_default(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create tree for default route");

    /* Insert default route */
    int default_val = 0;
    uint8_t any[16] = {0};
    int result = hev_radix_tree_insert_ipv6(tree, any, 0, &default_val);
    TEST_ASSERT_EQ(0, result, "Insert IPv6 /0");

    /* Any IPv6 should match */
    uint8_t test_ip[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8::1", test_ip), "Parse test IP");
    void *found = hev_radix_tree_lookup_ipv6(tree, test_ip);
    TEST_ASSERT_NOT_NULL(found, "IPv6 /0 matches all IPs");
    TEST_ASSERT_EQ(&default_val, (int*)found, "Correct value for /0");

    /* Insert more specific after default */
    uint8_t specific[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8:1234::", specific), "Parse specific network");
    int spec_val = 64;
    hev_radix_tree_insert_ipv6(tree, specific, 64, &spec_val);

    /* Specific should override default */
    found = hev_radix_tree_lookup_ipv6(tree, specific);
    TEST_ASSERT_NOT_NULL(found, "Specific matches");
    TEST_ASSERT_EQ(&spec_val, (int*)found, "Specific takes precedence");

    /* Other IPs still match default */
    uint8_t other[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:db8:5678::", other), "Parse other IP");
    found = hev_radix_tree_lookup_ipv6(tree, other);
    TEST_ASSERT_NOT_NULL(found, "Other IP matches default");
    TEST_ASSERT_EQ(&default_val, (int*)found, "Default still applies");

    hev_radix_tree_free(tree, NULL);
    printf("    IPv6 default route works\n");
}

/* Test mixed IPv4 and IPv6 */
void test_mixed_ip(void)
{
    HevRadixTree *tree = hev_radix_tree_new();
    TEST_ASSERT_NOT_NULL(tree, "Create tree for mixed test");

    /* Insert IPv4 route */
    uint32_t ipv4 = 0x08080808;  /* 8.8.8.8 */
    int ipv4_val = 4;
    hev_radix_tree_insert_ipv4(tree, ipv4, 32, &ipv4_val);

    /* Insert IPv6 route */
    uint8_t ipv6[16];
    TEST_ASSERT_EQ(0, str_to_ipv6("2001:4860:4860::8888", ipv6), "Parse IPv6");
    int ipv6_val = 6;
    hev_radix_tree_insert_ipv6(tree, ipv6, 128, &ipv6_val);

    /* IPv4 lookup should not find IPv6 */
    void *found = hev_radix_tree_lookup(tree, ipv4);
    TEST_ASSERT_NOT_NULL(found, "IPv4 found");
    TEST_ASSERT_EQ(&ipv4_val, (int*)found, "Correct IPv4 value");

    /* IPv6 lookup should not find IPv4 */
    found = hev_radix_tree_lookup_ipv6(tree, ipv6);
    TEST_ASSERT_NOT_NULL(found, "IPv6 found");
    TEST_ASSERT_EQ(&ipv6_val, (int*)found, "Correct IPv6 value");

    /* Cross-lookups should fail */
    found = hev_radix_tree_lookup_ipv6(tree, (uint8_t*)&ipv4);  /* Wrong function */
    TEST_ASSERT_NULL(found, "IPv4 not found in IPv6 lookup");

    hev_radix_tree_free(tree, NULL);
    printf("    Mixed IPv4/IPv6 works correctly\n");
}

/* Main function for IPv6 tests */
int main(void)
{
    test_suite_start("IPv6 Radix Tree Tests");

    test_ipv6_basic();
    test_ipv6_cidr();
    test_ipv6_overlap();
    test_ipv6_default();
    test_mixed_ip();

    test_suite_end();
    print_test_results();

    return g_test_stats.failed > 0 ? 1 : 0;
}