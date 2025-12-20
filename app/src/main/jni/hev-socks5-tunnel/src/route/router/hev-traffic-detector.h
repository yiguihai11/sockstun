/*
 * HEV SOCKS5 Tunnel Traffic Detector
 *
 * Copyright (c) 2024 hev
 * Author: hev <github@hev.im>
 */

#ifndef __HEV_TRAFFIC_DETECTOR_H__
#define __HEV_TRAFFIC_DETECTOR_H__

#include <stddef.h>
#include "hev-router.h"

#ifdef __cplusplus
extern "C" {
#endif

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