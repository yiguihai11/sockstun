/*
 * HEV SOCKS5 Tunnel Traffic Detector
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <arpa/inet.h>

#include "hev-traffic-detector.h"
#include "hev-router.h"
#include "hev-logger.h"

/* Traffic detector structure */
struct hev_traffic_detector_t {
    /* HTTP methods */
    const char *http_methods[8];
};

/* TLS record types */
#define TLS_TYPE_HANDSHAKE 0x16
#define TLS_HANDSHAKE_TYPE_CLIENT_HELLO 0x01

/* Create new traffic detector */
HevTrafficDetector *
hev_traffic_detector_new (void)
{
    HevTrafficDetector *detector = malloc (sizeof (HevTrafficDetector));
    if (!detector)
        return NULL;

    /* Initialize HTTP methods */
    detector->http_methods[0] = "GET ";
    detector->http_methods[1] = "POST ";
    detector->http_methods[2] = "PUT ";
    detector->http_methods[3] = "DELETE ";
    detector->http_methods[4] = "HEAD ";
    detector->http_methods[5] = "OPTIONS ";
    detector->http_methods[6] = "PATCH ";
    detector->http_methods[7] = "CONNECT ";

    return detector;
}

/* Free traffic detector */
void
hev_traffic_detector_free (HevTrafficDetector *detector)
{
    if (detector)
        free (detector);
}

/* Convert to lowercase */
static void
to_lower (char *str)
{
    while (*str) {
        *str = tolower (*str);
        str++;
    }
}

/* Parse HTTP request line */
static int
parse_http_request_line (const char *line, char *method, char *path)
{
    const char *space1 = strchr (line, ' ');
    if (!space1)
        return -1;

    int method_len = space1 - line;
    if (method_len > 15)
        return -1;

    strncpy (method, line, method_len);
    method[method_len] = '\0';

    const char *space2 = strchr (space1 + 1, ' ');
    if (!space2)
        return -1;

    int path_len = space2 - (space1 + 1);
    if (path_len > 1023)
        return -1;

    strncpy (path, space1 + 1, path_len);
    path[path_len] = '\0';

    return 0;
}

/* Parse HTTP header */
static int
parse_http_header (const char *header, char *name, char *value)
{
    const char *colon = strchr (header, ':');
    if (!colon)
        return -1;

    int name_len = colon - header;
    if (name_len > 63)
        return -1;

    strncpy (name, header, name_len);
    name[name_len] = '\0';

    /* Skip whitespace */
    const char *val_start = colon + 1;
    while (*val_start && (*val_start == ' ' || *val_start == '\t'))
        val_start++;

    strncpy (value, val_start, 255);
    value[255] = '\0';

    /* Trim whitespace from end */
    char *end = value + strlen (value) - 1;
    while (end >= value && (*end == ' ' || *end == '\t' || *end == '\r' || *end == '\n')) {
        *end = '\0';
        end--;
    }

    to_lower (name);
    return 0;
}

/* Detect HTTP traffic */
static HevTrafficResult *
detect_http (HevTrafficDetector *detector, const char *data, size_t len)
{
    HevTrafficResult *result = malloc (sizeof (HevTrafficResult));
    if (!result)
        return NULL;

    memset (result, 0, sizeof (HevTrafficResult));
    result->type = HEV_TRAFFIC_TYPE_HTTP;

    /* Copy raw headers (not implemented - field not available) */

    /* Parse request line */
    char *lines = strdup (data);
    char *line = strtok (lines, "\r\n");
    if (line) {
        parse_http_request_line (line, result->method, result->path);
    }

    /* Parse headers */
    while ((line = strtok (NULL, "\r\n")) != NULL) {
        if (line[0] == '\0')
            break; /* End of headers */

        char name[64], value[256];
        if (parse_http_header (line, name, value) == 0) {
            if (strcmp (name, "host") == 0) {
                strncpy (result->hostname, value, 255);
                result->hostname[255] = '\0';
            } /* else if (strcmp (name, "user-agent") == 0) {
                Note: user_agent field not available in current HevTrafficResult definition
                strncpy (result->user_agent, value, 255);
                result->user_agent[255] = '\0';
            } */
        }
    }

    free (lines);
    return result;
}

/* Parse TLS SNI */
static int
parse_tls_sni (const unsigned char *data, size_t len, char *sni)
{
    /* TLS record header: 5 bytes */
    if (len < 5)
        return -1;

    /* Check if it's a handshake record */
    if (data[0] != 0x16) /* TLS_HANDSHAKE */
        return -1;

    /* Extract handshake length */
    size_t record_len = (data[3] << 8) | data[4];
    if (len < 5 + record_len)
        return -1;

    const unsigned char *handshake = data + 5;

    /* Check if it's Client Hello */
    if (handshake[0] != 0x01) /* CLIENT_HELLO */
        return -1;

    /* Skip to extensions (simplified) */
    /* This is a basic implementation, real parsing would be more complex */

    /* Look for SNI extension (type 0x0000) */
    for (size_t i = 43; i < record_len - 9; i++) {
        if (handshake[i] == 0x00 && handshake[i+1] == 0x00) {
            /* Found SNI extension */
            size_t ext_len = (handshake[i+2] << 8) | handshake[i+3];
            if (i + 9 + ext_len > record_len)
                continue;

            /* Skip SNI list length and type */
            size_t pos = i + 9;

            /* Read server name length */
            size_t name_len = (handshake[pos] << 8) | handshake[pos+1];
            if (pos + 2 + name_len > record_len)
                continue;

            /* Extract server name */
            if (name_len < 255) {
                memcpy (sni, &handshake[pos+2], name_len);
                sni[name_len] = '\0';
                return 0;
            }
        }
    }

    return -1;
}

/* Detect HTTPS/TLS traffic */
static HevTrafficResult *
detect_https (HevTrafficDetector *detector, const char *data, size_t len)
{
    HevTrafficResult *result = malloc (sizeof (HevTrafficResult));
    if (!result)
        return NULL;

    memset (result, 0, sizeof (HevTrafficResult));
    result->type = HEV_TRAFFIC_TYPE_HTTPS;

    /* Try to parse SNI from TLS Client Hello */
    if (parse_tls_sni ((const unsigned char *)data, len, result->sni) == 0) {
        strcpy (result->hostname, result->sni);
        LOG_D ("Detected HTTPS SNI: %s", result->sni);
    }

    return result;
}

/* Detect traffic */
HevTrafficResult *
hev_traffic_detector_detect (HevTrafficDetector *detector,
                            const void *data, size_t len)
{
    if (!detector || !data || len == 0) {
        return NULL;
    }

    const char *str_data = (const char *)data;

    /* Check for HTTP traffic */
    for (int i = 0; i < 8; i++) {
        const char *method = detector->http_methods[i];
        size_t method_len = strlen (method);

        if (len >= method_len && strncmp (str_data, method, method_len) == 0) {
            return detect_http (detector, str_data, len);
        }
    }

    /* Check for HTTPS/TLS traffic */
    if (len >= 5 && str_data[0] == 0x16) { /* TLS Handshake */
        return detect_https (detector, str_data, len);
    }

    /* Unknown traffic */
    HevTrafficResult *result = malloc (sizeof (HevTrafficResult));
    if (result) {
        memset (result, 0, sizeof (HevTrafficResult));
        result->type = HEV_TRAFFIC_TYPE_OTHER;
    }

    return result;
}