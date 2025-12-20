/*
 ============================================================================
 Name        : hev-socks5-session-udp.c
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2017 - 2023 hev
 Description : Socks5 Session UDP
 ============================================================================
 */

#include <errno.h>
#include <string.h>

#include <lwip/udp.h>

#include <hev-task.h>
#include <hev-task-io.h>
#include <hev-task-io-socket.h>
#include <hev-task-mutex.h>
#include <hev-memory-allocator.h>
#include <hev-socks5-udp.h>
#include <hev-socks5-misc.h>

#include "hev-utils.h"
#include "hev-config.h"
#include "hev-logger.h"
#include "hev-compiler.h"
#include "hev-config-const.h"
#include "hev-socks5-tunnel.h"
#include "hev-smart-proxy.h"
#include "hev-chnroutes.h"
#include "hev-direct-udp.h"

#include "hev-socks5-session-udp.h"

typedef struct _HevSocks5UDPFrame HevSocks5UDPFrame;

struct _HevSocks5UDPFrame
{
    HevListNode node;
    HevSocks5Addr addr;
    struct pbuf *data;
    int use_direct;  /* 0: use proxy, 1: direct connection */
    hev_direct_udp_session_t direct_session;  /* Direct UDP session info */
};

/* Helper functions */
static uint16_t hev_socks5_addr_get_port (const HevSocks5Addr *addr);

/* UDP routing decision */
static int
hev_socks5_session_udp_should_use_direct (const HevSocks5Addr *addr)
{
    char addr_str[46];
    u16_t port;
    ip_addr_t saddr;

    /* Convert address to string for routing */
    if (hev_socks5_addr_into_lwip (addr, &saddr, &port) < 0) {
        LOG_D ("UDP failed to convert address");
        return 0;  /* Default to proxy */
    }

    /* Get IP address string */
    if (IP_IS_V4 (&saddr)) {
        ip4addr_ntoa_r (ip_2_ip4 (&saddr), addr_str, sizeof(addr_str));
    } else {
        ip6addr_ntoa_r (ip_2_ip6 (&saddr), addr_str, sizeof(addr_str));
    }

    /* Step 1: Check custom IP/port rules */
    HevSmartProxy *smart_proxy = hev_smart_proxy_get_instance ();
    if (smart_proxy) {
        HevSmartProxyResult result = hev_smart_proxy_connect (smart_proxy, addr_str, port);
        LOG_D ("UDP custom rules check for %s:%d - action=%d, match=%d",
               addr_str, port, result.action, result.match);

        if (result.match) {
            if (result.action == HEV_ROUTER_ACTION_ALLOW) {
                LOG_D ("UDP custom rule allows direct connection to %s:%d", addr_str, port);
                return 1;
            } else if (result.action == HEV_ROUTER_ACTION_BLOCK) {
                LOG_D ("UDP custom rule blocks connection to %s:%d", addr_str, port);
                return -1;  /* Block */
            } else {
                LOG_D ("UDP custom rule directs to proxy for %s:%d", addr_str, port);
                return 0;  /* Use proxy */
            }
        }
    }

    /* Step 2: If no custom rule matched and IP is in China routes, use direct */
    if (hev_config_get_chnroutes_enabled ()) {
        HevChnroutes *chnroutes = hev_chnroutes_get_instance ();
        if (chnroutes && hev_chnroutes_is_china_ip (chnroutes, addr_str)) {
            LOG_D ("UDP No custom rule matched, China IP detected via chnroutes: %s:%d",
                   addr_str, port);
            return 1;
        }
    }

    /* Step 3: Default to proxy */
    LOG_D ("UDP Using proxy for %s:%d", addr_str, port);
    return 0;
}

static int
task_io_yielder (HevTaskYieldType type, void *data)
{
    HevSocks5 *self = data;
    HevListNode *node;
    int res;

    if (self->type == HEV_SOCKS5_TYPE_UDP_IN_UDP) {
        ssize_t res;
        char buf;

        res = recv (self->fd, &buf, sizeof (buf), 0);
        if ((res == 0) || ((res < 0) && (errno != EAGAIN))) {
            hev_socks5_set_timeout (self, 0);
            return -1;
        }
    }

    res = hev_socks5_task_io_yielder (type, data);
    node = hev_socks5_session_get_node (HEV_SOCKS5_SESSION (self));
    hev_socks5_tunnel_update_session (node);

    return res;
}

static int
hev_socks5_session_udp_fwd_f (HevSocks5SessionUDP *self, unsigned int num)
{
    HevSocks5UDPFrame *frame;
    HevListNode *node;
    struct pbuf *buf;
    int i, res;

    res = self->frames;
    if (res <= 0)
        return 0;

    res = (res > num) ? num : res;

    /* First pass: make routing decisions and handle direct packets */
    node = hev_list_first (&self->frame_list);
    for (i = 0; i < res; i++) {
        frame = container_of (node, HevSocks5UDPFrame, node);
        node = hev_list_node_next (node);
        buf = frame->data;

        /* Make routing decision */
        int route_decision = hev_socks5_session_udp_should_use_direct (&frame->addr);
        if (route_decision == -1) {
            /* Block this packet */
            LOG_D ("UDP packet blocked");
            hev_list_del (&self->frame_list, node);
            hev_free (frame);
            pbuf_free (buf);
            self->frames--;
            res--;
            i--;
            continue;
        }

        frame->use_direct = route_decision;
    }

    /* Handle direct UDP packets */
    node = hev_list_first (&self->frame_list);
    int proxy_count = 0;
    int direct_count = 0;

    for (i = 0; i < res; i++) {
        frame = container_of (node, HevSocks5UDPFrame, node);
        node = hev_list_node_next (node);

        if (frame->use_direct) {
            ip_addr_t addr;
            u16_t port;

            /* Convert SOCKS5 address to lwip address */
            if (hev_socks5_addr_into_lwip (&frame->addr, &addr, &port) == 0) {
                /* Create direct UDP session if needed */
                if (!frame->direct_session.is_direct) {
                    hev_direct_udp_config_t config = {
                        .enabled = 1,
                        .timeout_ms = hev_config_get_smart_proxy_timeout_ms ()
                    };

                    /* Initialize direct UDP module if not done yet */
                    static int direct_udp_initialized = 0;
                    if (!direct_udp_initialized) {
                        hev_direct_udp_init (&config);
                        direct_udp_initialized = 1;
                    }

                    if (hev_direct_udp_create_socket (&addr, port, &frame->direct_session) < 0) {
                        LOG_E ("Failed to create direct UDP socket, falling back to proxy");
                        frame->use_direct = 0;
                    }
                }

                /* Send direct UDP packet */
                if (frame->direct_session.is_direct) {
                    if (hev_direct_udp_send (&frame->direct_session, buf, &addr, port) < 0) {
                        LOG_E ("Failed to send direct UDP, closing socket");
                        hev_direct_udp_close_socket (&frame->direct_session);
                        frame->use_direct = 0;
                    } else {
                        direct_count++;
                    }
                }
            }
        }
    }

    /* Second pass: count proxy packets and prepare array */
    HevSocks5UDPMsg proxy_msgv[res];
    int proxy_index = 0;

    node = hev_list_first (&self->frame_list);
    for (i = 0; i < res; i++) {
        frame = container_of (node, HevSocks5UDPFrame, node);
        node = hev_list_node_next (node);

        if (!frame->use_direct) {
            proxy_msgv[proxy_index].buf = buf->payload;
            proxy_msgv[proxy_index].len = buf->len;
            proxy_msgv[proxy_index].addr = &frame->addr;
            proxy_index++;
        }
    }

    /* Send proxy packets */
    if (proxy_index > 0) {
        res = hev_socks5_udp_sendmmsg (HEV_SOCKS5_UDP (self), proxy_msgv, proxy_index);
        if (res <= 0) {
            LOG_D ("%p socks5 session udp fwd f send", self);
        }
        proxy_count = res;
    }

    /* Clean up all frames */
    for (i = 0; i < res; i++) {
        node = hev_list_first (&self->frame_list);
        frame = container_of (node, HevSocks5UDPFrame, node);
        buf = frame->data;

        /* Close direct session if needed */
        if (frame->use_direct && frame->direct_session.is_direct) {
            hev_direct_udp_close_socket (&frame->direct_session);
        }

        hev_list_del (&self->frame_list, node);
        hev_free (frame);
        pbuf_free (buf);
        self->frames--;
    }

    LOG_D ("UDP session: %d direct packets, %d proxy packets sent",
           direct_count, proxy_count);

    return 1;
}

static int
hev_socks5_session_udp_fwd_b (HevSocks5SessionUDP *self, unsigned int num)
{
    char buf[UDP_BUF_SIZE * num];
    HevSocks5UDPMsg msgv[num];
    int i, res;

    for (i = 0; i < num; i++) {
        msgv[i].buf = buf + UDP_BUF_SIZE * i;
        msgv[i].len = UDP_BUF_SIZE;
    }

    res = hev_socks5_udp_recvmmsg (HEV_SOCKS5_UDP (self), msgv, num, 1);
    if (res <= 0) {
        if (res == -1 && errno == EAGAIN)
            return 0;
        LOG_D ("%p socks5 session udp fwd b recv", self);
        return -1;
    }

    for (i = 0; i < res; i++) {
        ip_addr_t saddr;
        struct pbuf *b;
        uint16_t port;
        err_t err;
        int ret;

        if (self->addr && self->port) {
            ip_2_ip4 (&saddr)->addr = self->addr;
            port = self->port;
        } else {
            ret = hev_socks5_addr_into_lwip (msgv[i].addr, &saddr, &port);
            if (ret < 0) {
                LOG_D ("%p socks5 session udp fwd b addr", self);
                return -1;
            }
        }

        b = pbuf_alloc_reference (msgv[i].buf, msgv[i].len, PBUF_REF);
        if (!b) {
            LOG_D ("%p socks5 session udp fwd b buf", self);
            return -1;
        }

        hev_task_mutex_lock (self->mutex);
        err = udp_sendfrom (self->pcb, b, &saddr, port);
        hev_task_mutex_unlock (self->mutex);

        pbuf_free (b);
        if (err != ERR_OK) {
            LOG_D ("%p socks5 session udp fwd b send", self);
            return -1;
        }
    }

    return 1;
}

static void
udp_recv_handler (void *arg, struct udp_pcb *pcb, struct pbuf *p,
                  const ip_addr_t *addr, u16_t port)
{
    HevSocks5SessionUDP *self = arg;
    HevSocks5UDPFrame *frame;

    if (!p) {
        hev_socks5_session_terminate (HEV_SOCKS5_SESSION (self));
        return;
    }

    if (self->frames > UDP_POOL_SIZE) {
        pbuf_free (p);
        return;
    }

    frame = hev_malloc (sizeof (HevSocks5UDPFrame));
    if (!frame) {
        pbuf_free (p);
        return;
    }

    frame->data = p;
    memset (&frame->node, 0, sizeof (frame->node));
    hev_socks5_addr_from_lwip (&frame->addr, &pcb->local_ip, pcb->local_port);

    if (frame->addr.atype == HEV_SOCKS5_ADDR_TYPE_NAME) {
        self->addr = ip_2_ip4 (&pcb->local_ip)->addr;
        self->port = pcb->local_port;
    }

    self->frames++;
    hev_list_add_tail (&self->frame_list, &frame->node);
    hev_task_wakeup (self->data.task);
}

HevSocks5SessionUDP *
hev_socks5_session_udp_new (struct udp_pcb *pcb, HevTaskMutex *mutex)
{
    HevSocks5SessionUDP *self;
    int res;

    self = hev_malloc0 (sizeof (HevSocks5SessionUDP));
    if (!self)
        return NULL;

    res = hev_socks5_session_udp_construct (self, pcb, mutex);
    if (res < 0) {
        hev_free (self);
        return NULL;
    }

    LOG_D ("%p socks5 session udp new", self);

    return self;
}

static int
hev_socks5_session_udp_bind (HevSocks5 *self, int fd,
                             const struct sockaddr *dest)
{
    HevConfigServer *srv;
    unsigned int mark;

    LOG_D ("%p socks5 session udp bind", self);

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

static uint16_t
hev_socks5_addr_get_port (const HevSocks5Addr *addr)
{
    uint16_t port = 0;

    switch (addr->atype) {
    case HEV_SOCKS5_ADDR_TYPE_IPV4:
        port = addr->ipv4.port;
        break;
    case HEV_SOCKS5_ADDR_TYPE_IPV6:
        port = addr->ipv6.port;
        break;
    case HEV_SOCKS5_ADDR_TYPE_NAME:
        memcpy (&port, addr->domain.addr + addr->domain.len, 2);
    }

    return port;
}

static int
hev_socks5_session_udp_set_upstream_addr (HevSocks5Client *base,
                                          HevSocks5Addr *addr)
{
    HevConfigServer *srv = hev_config_get_socks5_server ();
    HevSocks5ClientClass *ckptr;

    if (srv->udp_in_udp && srv->udp_addr[0]) {
        uint16_t port = hev_socks5_addr_get_port (addr);
        hev_socks5_addr_from_name (addr, srv->udp_addr, port);
    }

    ckptr = HEV_SOCKS5_CLIENT_CLASS (HEV_SOCKS5_CLIENT_UDP_TYPE);
    return ckptr->set_upstream_addr (base, addr);
}

static void
hev_socks5_session_udp_splice (HevSocks5Session *base)
{
    HevSocks5SessionUDP *self = HEV_SOCKS5_SESSION_UDP (base);
    HevTask *task = hev_task_self ();
    int res_f = 1, res_b = 1;
    int num;
    int fd;

    LOG_D ("%p socks5 session udp splice", self);

    num = hev_config_get_misc_udp_copy_buffer_nums ();
    fd = hev_socks5_udp_get_fd (HEV_SOCKS5_UDP (self));
    if (hev_task_mod_fd (task, fd, POLLIN | POLLOUT) < 0)
        hev_task_add_fd (task, fd, POLLIN | POLLOUT);

    for (;;) {
        HevTaskYieldType type;

        if (res_f >= 0)
            res_f = hev_socks5_session_udp_fwd_f (self, num);
        if (res_b >= 0)
            res_b = hev_socks5_session_udp_fwd_b (self, num);

        if (res_f > 0 || res_b > 0)
            type = HEV_TASK_YIELD;
        else if ((res_f & res_b) == 0)
            type = HEV_TASK_WAITIO;
        else
            break;

        if (task_io_yielder (type, self))
            break;
    }
}

static HevTask *
hev_socks5_session_udp_get_task (HevSocks5Session *base)
{
    HevSocks5SessionUDP *self = HEV_SOCKS5_SESSION_UDP (base);

    return self->data.task;
}

static void
hev_socks5_session_udp_set_task (HevSocks5Session *base, HevTask *task)
{
    HevSocks5SessionUDP *self = HEV_SOCKS5_SESSION_UDP (base);

    self->data.task = task;
}

static HevListNode *
hev_socks5_session_udp_get_node (HevSocks5Session *base)
{
    HevSocks5SessionUDP *self = HEV_SOCKS5_SESSION_UDP (base);

    return &self->data.node;
}

int
hev_socks5_session_udp_construct (HevSocks5SessionUDP *self,
                                  struct udp_pcb *pcb, HevTaskMutex *mutex)
{
    HevConfigServer *srv = hev_config_get_socks5_server ();
    int type;
    int res;

    if (srv->udp_in_udp)
        type = HEV_SOCKS5_TYPE_UDP_IN_UDP;
    else
        type = HEV_SOCKS5_TYPE_UDP_IN_TCP;

    res = hev_socks5_client_udp_construct (&self->base, type);
    if (res < 0)
        return -1;

    LOG_D ("%p socks5 session udp construct", self);

    HEV_OBJECT (self)->klass = HEV_SOCKS5_SESSION_UDP_TYPE;

    udp_recv (pcb, udp_recv_handler, self);

    self->pcb = pcb;
    self->mutex = mutex;
    self->data.self = self;

    return 0;
}

void
hev_socks5_session_udp_destruct (HevObject *base)
{
    HevSocks5SessionUDP *self = HEV_SOCKS5_SESSION_UDP (base);
    HevListNode *node;

    LOG_D ("%p socks5 session udp destruct", self);

    node = hev_list_first (&self->frame_list);
    while (node) {
        HevSocks5UDPFrame *frame;

        frame = container_of (node, HevSocks5UDPFrame, node);
        node = hev_list_node_next (node);
        pbuf_free (frame->data);
        hev_free (frame);
    }

    hev_task_mutex_lock (self->mutex);
    if (self->pcb) {
        udp_recv (self->pcb, NULL, NULL);
        udp_remove (self->pcb);
    }
    hev_task_mutex_unlock (self->mutex);

    HEV_SOCKS5_CLIENT_UDP_TYPE->destruct (base);
}

static void *
hev_socks5_session_udp_iface (HevObject *base, void *type)
{
    if (type == HEV_SOCKS5_SESSION_TYPE) {
        HevSocks5SessionUDPClass *klass = HEV_OBJECT_GET_CLASS (base);
        return &klass->session;
    }

    return HEV_SOCKS5_CLIENT_UDP_TYPE->iface (base, type);
}

HevObjectClass *
hev_socks5_session_udp_class (void)
{
    static HevSocks5SessionUDPClass klass;
    HevSocks5SessionUDPClass *kptr = &klass;
    HevObjectClass *okptr = HEV_OBJECT_CLASS (kptr);

    if (!okptr->name) {
        HevSocks5Class *skptr;
        HevSocks5ClientClass *ckptr;
        HevSocks5SessionIface *siptr;
        void *ptr;

        ptr = HEV_SOCKS5_CLIENT_UDP_TYPE;
        memcpy (kptr, ptr, sizeof (HevSocks5ClientUDPClass));

        okptr->name = "HevSocks5SessionUDP";
        okptr->destruct = hev_socks5_session_udp_destruct;
        okptr->iface = hev_socks5_session_udp_iface;

        skptr = HEV_SOCKS5_CLASS (kptr);
        skptr->binder = hev_socks5_session_udp_bind;

        ckptr = HEV_SOCKS5_CLIENT_CLASS (kptr);
        ckptr->set_upstream_addr = hev_socks5_session_udp_set_upstream_addr;

        siptr = &kptr->session;
        siptr->splicer = hev_socks5_session_udp_splice;
        siptr->get_task = hev_socks5_session_udp_get_task;
        siptr->set_task = hev_socks5_session_udp_set_task;
        siptr->get_node = hev_socks5_session_udp_get_node;
    }

    return okptr;
}
