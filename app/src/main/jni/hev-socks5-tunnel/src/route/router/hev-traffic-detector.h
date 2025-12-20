/*
 * HEV SOCKS5 Tunnel Traffic Detector
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_TRAFFIC_DETECTOR_H__
#define __HEV_TRAFFIC_DETECTOR_H__

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Traffic types */
typedef enum {
    HEV_TRAFFIC_TYPE_UNKNOWN,
    HEV_TRAFFIC_TYPE_HTTP,
    HEV_TRAFFIC_TYPE_HTTPS,
    HEV_TRAFFIC_TYPE_OTHER
} HevTrafficType;

/* Traffic detection result */
typedef struct {
    HevTrafficType type;
    char hostname[256];
    char sni[256];
    char method[16];
    char path[1024];
    char user_agent[256];
    char raw_headers[4096];
} HevTrafficResult;

/* Traffic detector */
typedef struct hev_traffic_detector_t HevTrafficDetector;

/* Traffic detector functions */
HevTrafficDetector *hev_traffic_detector_new (void);
void hev_traffic_detector_free (HevTrafficDetector *detector);
HevTrafficResult *hev_traffic_detector_detect (HevTrafficDetector *detector,
                                                const void *data, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_TRAFFIC_DETECTOR_H__ */