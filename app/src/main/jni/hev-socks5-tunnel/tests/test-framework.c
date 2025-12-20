/*
 * Simple Test Framework Implementation
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include "test-framework.h"

/* Global test statistics */
TestStats g_test_stats = {0, 0, 0};

/* Test suite functions */
void test_suite_start(const char *suite_name)
{
    printf("\n=== Running %s ===\n", suite_name);
}

void test_suite_end(void)
{
    printf("---\n");
}

void print_test_results(void)
{
    printf("\n=== Test Results ===\n");
    printf("Total:  %d\n", g_test_stats.total);
    printf("Passed: %d\n", g_test_stats.passed);
    printf("Failed: %d\n", g_test_stats.failed);
    printf("Success Rate: %.1f%%\n",
           g_test_stats.total > 0 ?
           (float)g_test_stats.passed * 100 / g_test_stats.total : 0.0);

    if (g_test_stats.failed == 0) {
        printf("\n✓ All tests passed!\n");
    } else {
        printf("\n✗ Some tests failed!\n");
    }
}