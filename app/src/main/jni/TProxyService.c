#include <jni.h>
#include <string.h>
#include "hev-socks5-tunnel.h"

JNIEXPORT void JNICALL
Java_hev_sockstun_TProxyService_TProxyStartService(JNIEnv *env, jclass clazz,
                                                  jstring config_path, jint fd)
{
    const char *path = (*env)->GetStringUTFChars(env, config_path, 0);
    hev_socks5_tunnel_init(fd);
    hev_socks5_tunnel_run();
    (*env)->ReleaseStringUTFChars(env, config_path, path);
}

JNIEXPORT void JNICALL
Java_hev_sockstun_TProxyService_TProxyStopService(JNIEnv *env, jclass clazz)
{
    hev_socks5_tunnel_stop();
    hev_socks5_tunnel_fini();
}

JNIEXPORT jlongArray JNICALL
Java_hev_sockstun_TProxyService_TProxyGetStats(JNIEnv *env, jclass clazz)
{
    size_t tx_packets, tx_bytes, rx_packets, rx_bytes;
    jlongArray result = (*env)->NewLongArray(env, 4);
    jlong stats[4];

    hev_socks5_tunnel_stats(&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);

    stats[0] = tx_packets;
    stats[1] = tx_bytes;
    stats[2] = rx_packets;
    stats[3] = rx_bytes;

    (*env)->SetLongArrayRegion(env, result, 0, 4, stats);
    return result;
} 
