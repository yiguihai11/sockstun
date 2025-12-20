/*
 ============================================================================
 Name        : hev-socks5-session-tcp.c
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2017 - 2023 hev
 Description : Socks5 Session TCP
 ============================================================================
 */

#include <errno.h>
#include <string.h>
#include <arpa/inet.h>
#include <sys/socket.h>

#include <lwip/tcp.h>

#include <hev-task.h>
#include <hev-task-io.h>
#include <hev-task-io-socket.h>
#include <hev-task-mutex.h>
#include <hev-memory-allocator.h>
#include <hev-socks5-misc.h>

#include "hev-utils.h"
#include "hev-config.h"
#include "hev-logger.h"
#include "hev-config-const.h"
#include "hev-socks5-tunnel.h"
#include "hev-smart-proxy.h"
#include "hev-chnroutes.h"
#include "route/router/hev-blocked-items.h"

#include "hev-socks5-session-tcp.h"

static int
task_io_yielder (HevTaskYieldType type, void *data)
{
    HevSocks5Session *self = data;
    HevListNode *node;
    int res;

    res = hev_socks5_task_io_yielder (type, data);
    node = hev_socks5_session_get_node (self);
    hev_socks5_tunnel_update_session (node);

    return res;
}

static int
tcp_splice_f (HevSocks5SessionTCP *self)
{
    struct iovec iov[64];
    struct pbuf *p;
    int iovc = 0;
    int res = 1;
    int fd;

    if (self->queue) {
        for (p = self->queue; p && (iovc < 64); p = p->next, iovc++) {
            iov[iovc].iov_base = p->payload;
            iov[iovc].iov_len = p->len;
        }
    } else if (self->pcb_eof) {
        res = -1;
    } else {
        res = 0;
    }

    if (iovc) {
        /* Choose socket based on connection type */
        if (self->direct_session.is_direct) {
            fd = self->direct_session.fd;
        } else {
            fd = HEV_SOCKS5 (self)->fd;
        }

        ssize_t s = writev (fd, iov, iovc);
        if (0 >= s) {
            if ((0 > s) && (EAGAIN == errno))
                res = 0;
            else
                res = -1;
        } else {
            hev_task_mutex_lock (self->mutex);
            self->queue = pbuf_free_header (self->queue, s);
            if (self->pcb)
                tcp_recved (self->pcb, s);
            hev_task_mutex_unlock (self->mutex);
            res = 1;
        }
    } else if (res < 0) {
        /* Choose socket based on connection type */
        if (self->direct_session.is_direct) {
            shutdown (self->direct_session.fd, SHUT_WR);
        } else {
            shutdown (HEV_SOCKS5 (self)->fd, SHUT_WR);
        }
    }

    return res;
}

static int
tcp_splice_b (HevSocks5SessionTCP *self)
{
    struct iovec iov[2];
    err_t err = ERR_OK;
    int res = 1, iovc;
    int fd;

    iovc = hev_ring_buffer_writing (self->buffer, iov);
    if (iovc) {
        /* Choose socket based on connection type */
        if (self->direct_session.is_direct) {
            fd = self->direct_session.fd;
        } else {
            fd = HEV_SOCKS5 (self)->fd;
        }

        ssize_t s = readv (fd, iov, iovc);
        if (0 >= s) {
            if ((0 > s) && (EAGAIN == errno))
                res = 0;
            else
                res = -1;
        } else {
            hev_ring_buffer_write_finish (self->buffer, s);
        }
    }

    hev_task_mutex_lock (self->mutex);
    if (self->pcb) {
        iovc = hev_ring_buffer_reading (self->buffer, iov);
        if (iovc) {
            ssize_t s = 0;
            int i;

            /* Check for traffic detection on first data chunk if direct connection */
            if (self->direct_session.is_direct && !self->direct_session.traffic_detected) {
                for (i = 0; i < iovc; i++) {
                    void *ptr = iov[i].iov_base;
                    size_t len = iov[i].iov_len;

                    /* Get smart proxy instance for traffic detection */
                    HevSmartProxy *smart_proxy = hev_smart_proxy_get_instance ();
                    if (smart_proxy && len > 0) {
                        /* Get destination address */
                        char dest_addr[INET_ADDRSTRLEN];
                        inet_ntop (AF_INET, &self->direct_session.pcb->local_ip,
                                  dest_addr, sizeof (dest_addr));
                        int dest_port = self->direct_session.pcb->local_port;

                        /* Stage 2: Handle traffic detection */
                        HevSmartProxyResult result = hev_smart_proxy_handle_traffic (
                            smart_proxy, dest_addr, ptr, len);

                        if (result.match && result.action != HEV_ROUTER_ACTION_ALLOW) {
                            /* Need to switch to SOCKS5 proxy */
                            LOG_D ("%p Traffic detected, switching to SOCKS5 for %s:%d",
                                   self, dest_addr, dest_port);

                            /* Close direct socket */
                            close (self->direct_session.fd);
                            self->direct_session.fd = -1;
                            self->direct_session.is_direct = 0;
                            self->direct_session.traffic_detected = 1;
                            self->direct_session.fallback_triggered = 1;

                            /* Connect via SOCKS5 */
                            HevSocks5Addr addr;
                            int addr_res = hev_socks5_addr_from_lwip (&addr,
                                &self->direct_session.pcb->local_ip,
                                self->direct_session.pcb->local_port);
                            if (addr_res >= 0) {
                                addr_res = hev_socks5_client_tcp_construct (&self->base, &addr);
                                if (addr_res < 0) {
                                    LOG_E ("%p Failed to establish SOCKS5 connection", self);
                                    res = -1;
                                    break;
                                }
                            } else {
                                LOG_E ("%p Failed to get address for SOCKS5", self);
                                res = -1;
                                break;
                            }

                            /* Fall through to normal data path */
                        } else {
                            self->direct_session.traffic_detected = 1;
                        }
                    }

                    /* Write data to PCB */
                    err |= tcp_write (self->pcb, ptr, len, 0);
                    s += len;
                }
            } else {
                /* Normal data path */
                for (i = 0; i < iovc; i++) {
                    void *ptr = iov[i].iov_base;
                    size_t len = iov[i].iov_len;
                    err |= tcp_write (self->pcb, ptr, len, 0);
                    s += len;
                }
            }

            hev_ring_buffer_read_finish (self->buffer, s);
            err |= tcp_output (self->pcb);
            res = 1;
        } else if (res < 0) {
            tcp_shutdown (self->pcb, 0, 1);
        }
    }
    hev_task_mutex_unlock (self->mutex);
    if (!self->pcb || (err != ERR_OK))
        res = -1;

    return res;
}

static err_t
tcp_recv_handler (void *arg, struct tcp_pcb *pcb, struct pbuf *p, err_t err)
{
    HevSocks5SessionTCP *self = arg;

    if (p) {
        if (!self->queue) {
            self->queue = p;
        } else {
            if (self->queue->tot_len > TCP_WND_MAX (pcb))
                return ERR_WOULDBLOCK;
            pbuf_cat (self->queue, p);
        }
    } else {
        self->pcb_eof = 1;
    }

    hev_task_wakeup (self->data.task);
    return ERR_OK;
}

static err_t
tcp_sent_handler (void *arg, struct tcp_pcb *pcb, u16_t len)
{
    HevSocks5SessionTCP *self = arg;

    hev_ring_buffer_read_release (self->buffer, len);
    hev_task_wakeup (self->data.task);

    return ERR_OK;
}

static void
tcp_err_handler (void *arg, err_t err)
{
    HevSocks5SessionTCP *self = arg;
    struct tcp_pcb *pcb = self->pcb;  /* Save pcb before setting to NULL */

    self->pcb = NULL;

    /* Check if we need to fallback to SOCKS5 */
    if (hev_direct_check_fallback (&self->direct_session, err) &&
        !self->direct_session.fallback_triggered) {

        self->direct_session.fallback_triggered = 1;
        LOG_W ("%p Fallback to SOCKS5 due to TCP error", self);

        /* Record failure for dynamic blacklisting */
        HevSmartProxy *smart_proxy = hev_smart_proxy_get_instance ();
        if (smart_proxy && self->direct_session.pcb) {
            char dest_addr[INET_ADDRSTRLEN];
            inet_ntop (AF_INET, &self->direct_session.pcb->local_ip,
                      dest_addr, sizeof (dest_addr));

            HevFailureReason reason = HEV_FAILURE_REASON_UNKNOWN;
            if (err == ERR_RST)
                reason = HEV_FAILURE_REASON_RST;
            else if (err == ERR_TIMEOUT)
                reason = HEV_FAILURE_REASON_TIMEOUT;
            else if (err == ERR_CONN)
                reason = HEV_FAILURE_REASON_CONN_REFUSED;
            else if (err == ERR_ABRT)
                reason = HEV_FAILURE_REASON_RST;

            hev_smart_proxy_record_failure (smart_proxy, dest_addr, NULL, reason);
        }

        /* Close direct socket */
        if (self->direct_session.is_direct && self->direct_session.fd >= 0) {
            close (self->direct_session.fd);
            self->direct_session.fd = -1;
        }

        /* Re-establish connection using SOCKS5 */
        if (pcb) {
            HevSocks5Addr addr;
            int res = hev_socks5_addr_from_lwip (&addr, &pcb->local_ip, pcb->local_port);
            if (res >= 0) {
                res = hev_socks5_client_tcp_construct (&self->base, &addr);
                if (res >= 0) {
                    self->direct_session.is_direct = 0;
                    LOG_D ("%p Successfully re-established connection via SOCKS5", self);
                    return;
                }
            }

            LOG_E ("%p Failed to re-establish connection via SOCKS5", self);
        }
    }

    hev_socks5_session_terminate (HEV_SOCKS5_SESSION (self));
}

HevSocks5SessionTCP *
hev_socks5_session_tcp_new (struct tcp_pcb *pcb, HevTaskMutex *mutex)
{
    HevSocks5SessionTCP *self;
    int res;

    self = hev_malloc0 (sizeof (HevSocks5SessionTCP));
    if (!self)
        return NULL;

    res = hev_socks5_session_tcp_construct (self, pcb, mutex);
    if (res < 0) {
        hev_free (self);
        return NULL;
    }

    LOG_D ("%p socks5 session tcp new", self);

    return self;
}

static int
hev_socks5_session_tcp_bind (HevSocks5 *self, int fd,
                             const struct sockaddr *dest)
{
    HevConfigServer *srv;
    unsigned int mark;

    LOG_D ("%p socks5 session tcp bind", self);

    srv = hev_config_get_socks5_server ();
    mark = srv->mark;

    if (mark) {
        int res;

        res = set_sock_mark (fd, mark);
        if (res < 0)
            return -1;
    }

    return 0;
}

static void
hev_socks5_session_tcp_splice (HevSocks5Session *base)
{
    HevSocks5SessionTCP *self = HEV_SOCKS5_SESSION_TCP (base);
    int tcp_buffer_size;
    int res_f = 1;
    int res_b = 1;

    LOG_D ("%p socks5 session tcp splice", self);

    if (!self->pcb)
        return;

    tcp_buffer_size = hev_config_get_misc_tcp_buffer_size ();
    self->buffer = hev_ring_buffer_alloca (tcp_buffer_size);
    if (!self->buffer)
        return;

    for (;;) {
        HevTaskYieldType type;

        if (res_f >= 0)
            res_f = tcp_splice_f (self);
        if (res_b >= 0)
            res_b = tcp_splice_b (self);

        if (res_f > 0 || res_b > 0)
            type = HEV_TASK_YIELD;
        else if ((res_f & res_b) == 0)
            type = HEV_TASK_WAITIO;
        else
            break;

        if (task_io_yielder (type, base) < 0)
            break;
    }

    while (self->pcb) {
        if (hev_ring_buffer_get_use_size (self->buffer) == 0)
            break;

        if (task_io_yielder (HEV_TASK_WAITIO, base) < 0)
            break;
    }
}

static HevTask *
hev_socks5_session_tcp_get_task (HevSocks5Session *base)
{
    HevSocks5SessionTCP *self = HEV_SOCKS5_SESSION_TCP (base);

    return self->data.task;
}

static void
hev_socks5_session_tcp_set_task (HevSocks5Session *base, HevTask *task)
{
    HevSocks5SessionTCP *self = HEV_SOCKS5_SESSION_TCP (base);

    self->data.task = task;
}

static HevListNode *
hev_socks5_session_tcp_get_node (HevSocks5Session *base)
{
    HevSocks5SessionTCP *self = HEV_SOCKS5_SESSION_TCP (base);

    return &self->data.node;
}

int
hev_socks5_session_tcp_construct (HevSocks5SessionTCP *self,
                                  struct tcp_pcb *pcb, HevTaskMutex *mutex)
{
    HevSocks5Addr addr;
    char dest_addr[INET_ADDRSTRLEN];
    int dest_port;
    int res;

    res = hev_socks5_addr_from_lwip (&addr, &pcb->local_ip, pcb->local_port);
    if (res < 0)
        return -1;

    /* Initialize direct session */
    self->direct_session.fd = -1;
    self->direct_session.is_direct = 0;
    self->direct_session.pcb = pcb;
    self->direct_session.fallback_triggered = 0;
    self->direct_session.traffic_detected = 0;

    /* Get destination address and port */
    inet_ntop (AF_INET, &pcb->local_ip, dest_addr, sizeof (dest_addr));
    dest_port = pcb->local_port;

    /* Step 1: Check chnroutes first (if enabled) */
    int use_direct = 0;
    if (hev_config_get_chnroutes_enabled ()) {
        HevChnroutes *chnroutes = hev_chnroutes_get_instance ();
        if (chnroutes && hev_chnroutes_is_china_ip (chnroutes, dest_addr)) {
            /* IP matches China routes, use direct connection */
            use_direct = 1;
            LOG_D ("%p China IP detected via chnroutes: %s:%d", self, dest_addr, dest_port);
        }
    }

    if (use_direct) {
        /* Direct connection for China IP */
        LOG_D ("%p Using direct connection to %s:%d", self, dest_addr, dest_port);

        /* Initialize direct connect module */
        hev_direct_config_t direct_config;
        direct_config.enabled = 1;
        direct_config.timeout_ms = hev_config_get_smart_proxy_timeout_ms ();
        hev_direct_init (&direct_config);

        self->direct_session.fd = hev_direct_create_socket (dest_addr, dest_port);
        if (self->direct_session.fd >= 0) {
            self->direct_session.is_direct = 1;
            LOG_D ("%p Direct connection established", self);
        } else {
            /* Direct connection failed, fallback to SOCKS5 */
            self->direct_session.is_direct = 0;
            LOG_D ("%p Direct connection failed, using SOCKS5", self);
            res = hev_socks5_client_tcp_construct (&self->base, &addr);
            if (res < 0)
                return -1;
        }
    } else {
        /* Step 2: Use smart proxy logic for non-China IPs */
        HevSmartProxy *smart_proxy = hev_smart_proxy_get_instance ();
        if (!smart_proxy) {
            smart_proxy = hev_smart_proxy_new (NULL);
            if (smart_proxy) {
                hev_smart_proxy_load_config (smart_proxy);
                hev_smart_proxy_set_instance (smart_proxy);
            }
        }

        if (smart_proxy) {
            /* Stage 1: Check initial connection routing */
            HevSmartProxyResult result = hev_smart_proxy_connect (smart_proxy, dest_addr, dest_port);

            LOG_D ("%p SmartProxy routing decision for %s:%d - action=%d, needs_detection=%d",
                   self, dest_addr, dest_port, result.action, result.needs_detection);

            if (result.action == HEV_ROUTER_ACTION_ALLOW) {
                /* Direct connection */
                LOG_D ("%p SmartProxy allows direct connection to %s:%d", self, dest_addr, dest_port);

                /* Initialize direct connect module */
                hev_direct_config_t direct_config;
                direct_config.enabled = 1;
                direct_config.timeout_ms = hev_config_get_smart_proxy_timeout_ms ();
                hev_direct_init (&direct_config);

                self->direct_session.fd = hev_direct_create_socket (dest_addr, dest_port);
                if (self->direct_session.fd >= 0) {
                    self->direct_session.is_direct = 1;
                    LOG_D ("%p SmartProxy direct connection established", self);
                } else {
                    /* Record failure and fallback to SOCKS5 */
                    hev_smart_proxy_record_failure (smart_proxy, dest_addr, NULL,
                                                   HEV_FAILURE_REASON_CONN_REFUSED);
                    self->direct_session.is_direct = 0;
                    LOG_D ("%p SmartProxy direct connection failed, using SOCKS5", self);
                    res = hev_socks5_client_tcp_construct (&self->base, &addr);
                    if (res < 0)
                        return -1;
                }
            } else if (result.action == HEV_ROUTER_ACTION_DENY) {
                /* Use SOCKS5 proxy */
                self->direct_session.is_direct = 0;
                LOG_D ("%p SmartProxy using SOCKS5 for %s:%d", self, dest_addr, dest_port);
                res = hev_socks5_client_tcp_construct (&self->base, &addr);
                if (res < 0)
                    return -1;
            } else if (result.action == HEV_ROUTER_ACTION_BLOCK) {
                /* Block connection */
                LOG_D ("%p SmartProxy blocking connection to %s:%d", self, dest_addr, dest_port);
                return -1;
            } else {
                /* Default to SOCKS5 */
                self->direct_session.is_direct = 0;
                LOG_D ("%p SmartProxy defaulting to SOCKS5 for %s:%d", self, dest_addr, dest_port);
                res = hev_socks5_client_tcp_construct (&self->base, &addr);
                if (res < 0)
                    return -1;
            }
        } else {
            /* No smart proxy, use SOCKS5 */
            self->direct_session.is_direct = 0;
            LOG_D ("%p No smart proxy, using SOCKS5 for %s:%d", self, dest_addr, dest_port);
            res = hev_socks5_client_tcp_construct (&self->base, &addr);
            if (res < 0)
                return -1;
        }
    }

    LOG_D ("%p socks5 session tcp construct", self);

    HEV_OBJECT (self)->klass = HEV_SOCKS5_SESSION_TCP_TYPE;

    tcp_arg (pcb, self);
    tcp_recv (pcb, tcp_recv_handler);
    tcp_sent (pcb, tcp_sent_handler);
    tcp_err (pcb, tcp_err_handler);

    self->pcb = pcb;
    self->mutex = mutex;
    self->data.self = self;

    return 0;
}

void
hev_socks5_session_tcp_destruct (HevObject *base)
{
    HevSocks5SessionTCP *self = HEV_SOCKS5_SESSION_TCP (base);

    LOG_D ("%p socks5 session tcp destruct", self);

    hev_task_mutex_lock (self->mutex);
    if (self->pcb) {
        tcp_recv (self->pcb, NULL);
        tcp_sent (self->pcb, NULL);
        tcp_err (self->pcb, NULL);
        tcp_abort (self->pcb);
    }

    if (self->queue)
        pbuf_free (self->queue);
    hev_task_mutex_unlock (self->mutex);

    /* Close direct socket if it was used */
    if (self->direct_session.is_direct && self->direct_session.fd >= 0) {
        close (self->direct_session.fd);
        self->direct_session.fd = -1;
    }

    /* Call SOCKS5 destruct if SOCKS5 was used or fallback was triggered */
    if (!self->direct_session.is_direct || self->direct_session.fallback_triggered) {
        HEV_SOCKS5_CLIENT_TCP_TYPE->destruct (base);
    }
}

static void *
hev_socks5_session_tcp_iface (HevObject *base, void *type)
{
    if (type == HEV_SOCKS5_SESSION_TYPE) {
        HevSocks5SessionTCPClass *klass = HEV_OBJECT_GET_CLASS (base);
        return &klass->session;
    }

    return HEV_SOCKS5_CLIENT_TCP_TYPE->iface (base, type);
}

HevObjectClass *
hev_socks5_session_tcp_class (void)
{
    static HevSocks5SessionTCPClass klass;
    HevSocks5SessionTCPClass *kptr = &klass;
    HevObjectClass *okptr = HEV_OBJECT_CLASS (kptr);

    if (!okptr->name) {
        HevSocks5Class *skptr;
        HevSocks5SessionIface *siptr;
        void *ptr;

        ptr = HEV_SOCKS5_CLIENT_TCP_TYPE;
        memcpy (kptr, ptr, sizeof (HevSocks5ClientTCPClass));

        okptr->name = "HevSocks5SessionTCP";
        okptr->destruct = hev_socks5_session_tcp_destruct;
        okptr->iface = hev_socks5_session_tcp_iface;

        skptr = HEV_SOCKS5_CLASS (kptr);
        skptr->binder = hev_socks5_session_tcp_bind;

        siptr = &kptr->session;
        siptr->splicer = hev_socks5_session_tcp_splice;
        siptr->get_task = hev_socks5_session_tcp_get_task;
        siptr->set_task = hev_socks5_session_tcp_set_task;
        siptr->get_node = hev_socks5_session_tcp_get_node;
    }

    return okptr;
}
