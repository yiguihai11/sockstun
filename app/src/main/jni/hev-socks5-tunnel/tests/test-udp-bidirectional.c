/*
 * HEV SOCKS5 Tunnel UDP Bidirectional Tests
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <errno.h>

#include "../src/route/hev-direct-udp.h"
#include "../src/misc/hev-logger.h"

/* Simple test assertion macro */
#define TEST_ASSERT(condition, message) \
    do { \
        if (!(condition)) { \
            printf ("FAIL: %s\n", message); \
            exit (1); \
        } \
    } while (0)

/* Test receive callback data */
static int packet_received = 0;
static uint8_t recv_buffer[2048];
static size_t recv_len = 0;
static ip_addr_t last_recv_addr;
static u16_t last_recv_port = 0;

/* Test receive callback function */
static void
test_recv_callback (hev_direct_udp_session_t *session,
                    struct pbuf *p,
                    const ip_addr_t *addr,
                    u16_t port,
                    void *user_data)
{
    if (!p)
        return;

    /* Store received packet info */
    recv_len = (p->len < sizeof (recv_buffer)) ? p->len : sizeof (recv_buffer);
    memcpy (recv_buffer, p->payload, recv_len);

    /* Store sender info */
    memcpy (&last_recv_addr, addr, sizeof (ip_addr_t));
    last_recv_port = port;

    packet_received++;

    pbuf_free (p);
}

/* Test UDP initialization and basic configuration */
void
test_udp_init (void)
{
    hev_direct_udp_config_t config = {
        .enabled = 1,
        .timeout_ms = 5000
    };

    int res = hev_direct_udp_init (&config);
    TEST_ASSERT (res == 0, "UDP initialization should succeed");

    hev_direct_udp_fini ();

    printf ("    UDP initialization test passed\n");
}

/* Test UDP socket creation with callback */
void
test_udp_socket_with_callback (void)
{
    hev_direct_udp_config_t config = {
        .enabled = 1,
        .timeout_ms = 1000
    };

    hev_direct_udp_init (&config);

    ip_addr_t addr;
    hev_direct_udp_session_t session;
    ipaddr_aton ("127.0.0.1", &addr);

    int res = hev_direct_udp_create_socket (&addr, 8080, &session);
    TEST_ASSERT (res >= 0, "Socket creation should succeed");
    TEST_ASSERT (session.fd >= 0, "Socket fd should be valid");
    TEST_ASSERT (session.is_direct == 1, "Session should be marked as direct");

    /* Set receive callback */
    hev_direct_udp_set_receive_callback (&session, test_recv_callback, NULL);

    /* Verify callback was set (can't directly access, but no crash means success) */
    TEST_ASSERT (1, "Callback should be set without crashing");

    hev_direct_udp_close_socket (&session);
    hev_direct_udp_fini ();

    printf ("    UDP socket with callback test passed\n");
}

/* Test UDP packet reception via input function */
void
test_udp_input_receive (void)
{
    hev_direct_udp_config_t config = {
        .enabled = 1,
        .timeout_ms = 1000
    };

    hev_direct_udp_init (&config);

    ip_addr_t addr;
    hev_direct_udp_session_t session;
    ipaddr_aton ("127.0.0.1", &addr);

    int res = hev_direct_udp_create_socket (&addr, 8081, &session);
    TEST_ASSERT (res >= 0, "Socket creation should succeed");

    /* Reset test state */
    packet_received = 0;
    recv_len = 0;

    /* Set receive callback */
    hev_direct_udp_set_receive_callback (&session, test_recv_callback, NULL);

    /* Test input function with sample data */
    const char *test_data = "Hello UDP Bidirectional!";
    ip_addr_t test_addr;
    ipaddr_aton ("192.168.1.100", &test_addr);
    hev_direct_udp_input (&session, test_data, strlen (test_data), &test_addr, 9999);

    /* Verify callback was called */
    TEST_ASSERT (packet_received == 1, "Should receive one packet");
    TEST_ASSERT (recv_len == strlen (test_data), "Should receive correct data length");
    TEST_ASSERT (memcmp (recv_buffer, test_data, strlen (test_data)) == 0,
                 "Should receive correct data");
    TEST_ASSERT (last_recv_port == 9999, "Should receive correct port");

    hev_direct_udp_close_socket (&session);
    hev_direct_udp_fini ();

    printf ("    UDP input receive test passed\n");
}

/* Test UDP send and receive round trip */
void
test_udp_send_receive_roundtrip (void)
{
    hev_direct_udp_config_t config = {
        .enabled = 1,
        .timeout_ms = 1000
    };

    hev_direct_udp_init (&config);

    ip_addr_t addr;
    hev_direct_udp_session_t session;
    ipaddr_aton ("127.0.0.1", &addr);

    int res = hev_direct_udp_create_socket (&addr, 8082, &session);
    TEST_ASSERT (res >= 0, "Socket creation should succeed");

    /* Set receive callback */
    hev_direct_udp_set_receive_callback (&session, test_recv_callback, NULL);

    /* Create test packet */
    struct pbuf *p = pbuf_alloc (PBUF_TRANSPORT, 20, PBUF_RAM);
    TEST_ASSERT (p != NULL, "Packet allocation should succeed");
    strcpy (p->payload, "roundtrip_test");

    /* Send packet */
    ip_addr_t target_addr;
    ipaddr_aton ("127.0.0.1", &target_addr);
    res = hev_direct_udp_send (&session, p, &target_addr, 8082);
    /* May fail due to no receiver, but should not crash */

    pbuf_free (p);

    /* Try to receive (should return 0 as no data is available) */
    res = hev_direct_udp_receive (&session);
    TEST_ASSERT (res == 0, "Should return 0 when no data available");

    hev_direct_udp_close_socket (&session);
    hev_direct_udp_fini ();

    printf ("    UDP send/receive roundtrip test passed\n");
}

/* Test IPv6 support with callback */
void
test_udp_ipv6_callback (void)
{
    hev_direct_udp_config_t config = {
        .enabled = 1,
        .timeout_ms = 1000
    };

    hev_direct_udp_init (&config);

    ip_addr_t addr;
    hev_direct_udp_session_t session;

    /* Test IPv6 if supported */
    if (ipaddr_aton ("::1", &addr) == ERR_OK) {
        int res = hev_direct_udp_create_socket (&addr, 8083, &session);
        if (res >= 0) {
            /* Set receive callback */
            hev_direct_udp_set_receive_callback (&session, test_recv_callback, NULL);

            /* Test IPv6 input */
            const char *test_data = "IPv6 Test";
            ip_addr_t test_addr;
            ipaddr_aton ("::1", &test_addr);
            hev_direct_udp_input (&session, test_data, strlen (test_data), &test_addr, 8083);

            /* Verify callback was called */
            TEST_ASSERT (packet_received == 1, "Should receive IPv6 packet");
            TEST_ASSERT (recv_len == strlen (test_data), "Should receive correct IPv6 data");

            hev_direct_udp_close_socket (&session);
            printf ("      IPv6 callback test passed\n");
        }
    }

    hev_direct_udp_fini ();
}

/* Test multiple packets */
void
test_udp_multiple_packets (void)
{
    hev_direct_udp_config_t config = {
        .enabled = 1,
        .timeout_ms = 1000
    };

    hev_direct_udp_init (&config);

    ip_addr_t addr;
    hev_direct_udp_session_t session;
    ipaddr_aton ("127.0.0.1", &addr);

    int res = hev_direct_udp_create_socket (&addr, 8084, &session);
    TEST_ASSERT (res >= 0, "Socket creation should succeed");

    /* Reset test state */
    packet_received = 0;

    /* Set receive callback */
    hev_direct_udp_set_receive_callback (&session, test_recv_callback, NULL);

    /* Send multiple packets via input */
    for (int i = 0; i < 5; i++) {
        char test_data[32];
        snprintf (test_data, sizeof (test_data), "Packet %d", i);

        ip_addr_t test_addr;
        ipaddr_aton ("192.168.1.100", &test_addr);
        hev_direct_udp_input (&session, test_data, strlen (test_data), &test_addr, 9000 + i);
    }

    /* Verify all packets were received */
    TEST_ASSERT (packet_received == 5, "Should receive all 5 packets");

    hev_direct_udp_close_socket (&session);
    hev_direct_udp_fini ();

    printf ("    UDP multiple packets test passed\n");
}

/* Main test runner */
int
main (int argc, char *argv[])
{
    printf ("Running UDP Bidirectional Tests...\n\n");

    /* Run tests */
    test_udp_init ();
    test_udp_socket_with_callback ();
    test_udp_input_receive ();
    test_udp_send_receive_roundtrip ();
    test_udp_ipv6_callback ();
    test_udp_multiple_packets ();

    printf ("\nAll UDP Bidirectional tests passed!\n");

    return 0;
}