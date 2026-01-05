# SocksTun 项目架构文档

## 项目概述

**SocksTun** 是一个基于 Android VPN Service 的轻量级网络代理应用，使用 SOCKS5 协议进行流量转发。项目采用混合架构（Java + C 原生层），实现了智能流量分流、DNS 延迟优化等高级功能。

### 核心特点

- **混合架构**：Java 层负责 UI 和配置，C 层负责高性能网络处理
- **基于 TUN 接口**：使用 Android VPN Service 建立系统级隧道
- **智能分流**：国内流量直连，国外流量通过 SOCKS5 代理
- **DNS 优化**：自动测速选择最低延迟的 IP 地址
- **高性能设计**：零拷贝、异步 I/O、协程支持

---

## 目录结构

```
sockstun/
├── app/src/main/
│   ├── AndroidManifest.xml         # 应用配置清单
│   ├── java/hev/sockstun/          # Java 源码
│   │   ├── TProxyService.java      # VPN 核心服务
│   │   ├── ConfigGenerator.java    # YAML 配置生成器
│   │   └── Preferences.java        # 配置管理
│   └── jni/hev-socks5-tunnel/      # C 原生层
│       ├── src/
│       │   ├── hev-main.c          # 程序入口
│       │   ├── hev-socks5-tunnel.c # 隧道核心
│       │   ├── hev-session-manager.c   # 会话管理器
│       │   ├── hev-traffic-router.c    # 流量路由器
│       │   ├── hev-filter.c        # 访问控制/过滤器
│       │   ├── hev-dns-cache.c     # DNS 缓存
│       │   └── hev-dns-latency.c   # DNS 延迟优化
│       └── third-part/             # 第三方依赖
│           ├── hev-task-system/    # 协程/任务调度系统
│           ├── hev-socks5/         # SOCKS5 库
│           └── lwip/               # 轻量级 TCP/IP 协议栈
├── conf/                           # 配置文件目录
│   ├── acl.txt                     # 访问控制列表
│   └── chnroutes.txt               # 中国路由表
├── docs/                           # 文档
└── gradle/                         # Gradle 构建配置
```

---

## 技术栈

### 核心技术

| 技术 | 用途 | 说明 |
|------|------|------|
| Android VPN Service | 系统级 VPN | 建立 TUN 隧道，拦截网络流量 |
| TUN/TAP 接口 | 虚拟网络设备 | 用户态网络接口 |
| SOCKS5 协议 | 代理协议 | 支持 TCP/UDP 转发 |
| lwIP | TCP/IP 协议栈 | 嵌入式轻量级协议栈 |
| HevTask | 协程框架 | 轻量级用户态协程 |

### 构建工具

- **Android NDK**：原生代码开发工具链
- **CMake**：跨平台构建系统
- **Makefile**：传统构建脚本
- **Gradle**：Android 构建工具

---

## 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android 应用层                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ TProxyService│  │ConfigGenerator│  │ Preferences  │         │
│  │  (VPN核心)   │  │  (配置生成)   │  │  (配置管理)   │         │
│  └──────┬───────┘  └──────────────┘  └──────────────┘         │
│         │                                                        │
│         │ JNI 接口                                             │
└─────────┼────────────────────────────────────────────────────────┘
          │
          │ TUN Interface (fd)
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      C 原生层 (hev-socks5-tunnel)                │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                  hev-socks5-tunnel                         │  │
│  │           (隧道核心 - 数据包封装/解封装)                    │  │
│  └──────────────────────┬─────────────────────────────────────┘  │
│                         │                                        │
│                         ▼                                        │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                hev-traffic-router                          │  │
│  │                   (流量路由器)                             │  │
│  │  ┌─────────────┬─────────────┬─────────────┐              │  │
│  │  │ chnroutes   │ ACL规则     │ 智能代理     │              │  │
│  │  │ 国内/国外    │ 黑/白名单    │ 直连探测     │              │  │
│  │  └──────┬──────┴──────┬──────┴──────┬──────┘              │  │
│  │         │             │             │                       │  │
│  │    国内流量        ACL匹配     国外流量                       │  │
│  │    直接连接        阻断/允许    智能代理                      │  │
│  └─────────────────────┼─────────────────────────────────────┘  │
│                        │                                        │
│                        ▼                                        │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              hev-session-manager                           │  │
│  │                (会话管理器)                                │  │
│  │      TCP/UDP 连接管理 │ 超时处理 │ 统计信息                │  │
│  └───────────────────────┼────────────────────────────────────┘  │
│                          │                                        │
│                          ▼                                        │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    SOCKS5 代理转发                         │  │
│  │  ┌─────────────┬─────────────┬─────────────┐              │  │
│  │  │ TCP Splice  │ UDP-in-TCP  │ UDP-in-UDP  │              │  │
│  │  └─────────────┴─────────────┴─────────────┘              │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              辅助模块                                       │  │
│  │  ┌──────────────┬──────────────┬──────────────┐           │  │
│  │  │ hev-filter   │ hev-dns-cache│ hev-dns-     │           │  │
│  │  │ (访问控制)   │ (DNS缓存)    │ latency      │           │  │
│  │  │              │              │ (DNS优化)    │           │  │
│  │  └──────────────┴──────────────┴──────────────┘           │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 数据流处理流程

```
┌───────────────────────────────────────────────────────────────────┐
│ 1. VPN 隧道建立                                                    │
│    TProxyService.create() → VpnService.Builder                    │
│    → 配置 TUN 接口 (IP/MTU/路由) → 传递文件描述符给 JNI          │
└──────────────────────────┬────────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────┐
│ 2. 数据包捕获                                                      │
│    Android 系统将目标地址流量路由到 TUN 接口                       │
│    → hev-socks5-tunnel 从 TUN 读取原始 IP 数据包                  │
└──────────────────────────┬────────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────┐
│ 3. 流量分析决策                                                    │
│    ┌────────────────────────────────────────────────────────┐    │
│    │ hev-traffic-router 分析目标地址                          │    │
│    │                                                          │    │
│    │  检查 chnroutes → 判断国内/国外                           │    │
│    │  检查 ACL 规则 → 允许/阻断                                │    │
│    │  检查智能代理 → 直连优先                                  │    │
│    └────────────────────────────────────────────────────────┘    │
└──────────────────────────┬────────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────┐
│ 4. 连接处理                                                        │
│    ┌──────────┬──────────┬──────────┐                            │
│    │ 国内流量 │ ACL阻断  │ 国外流量 │                            │
│    │ 直接连接 │ 丢弃      │ 智能代理  │                            │
│    └──────────┴──────────┴──────────┘                            │
│                                                                  │
│    智能代理流程:                                                  │
│    1. 尝试直接连接 (timeout=1300ms)                              │
│    2. 成功 → 直连模式                                            │
│    3. 超时 → 切换到 SOCKS5 代理                                   │
│    4. 记录阻断 IP (360分钟)                                       │
└──────────────────────────┬────────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────┐
│ 5. DNS 特殊处理                                                    │
│    DNS 查询 → mapdns 虚拟服务器 (223.5.5.5/2400:3200::1)            │
│    → DNS 分流: 国内 DNS 直接查询                                   │
│    → DNS 延迟优化: 测速选择最快 IP                                 │
│    → DNS 污染检测: 发污染通过 SOCKS5 重新查询                      │
└──────────────────────────┬────────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────┐
│ 6. 数据转发                                                        │
│    TCP: 使用 splice 零拷贝转发                                     │
│    UDP: UDP-in-TCP 或 UDP-in-UDP 模式                             │
│    → 会话管理器维护连接状态和统计                                  │
└───────────────────────────────────────────────────────────────────┘
```

---

## 核心模块详解

### 1. TProxyService (VPN 核心服务)

**文件**: `app/src/main/java/hev/sockstun/TProxyService.java`

**功能**:
- 继承自 `VpnService`，建立 VPN 隧道
- 配置 TUN 接口参数（IP 地址、MTU、路由）
- 管理 DNS 服务器设置
- 生成 YAML 配置文件
- 通过 JNI 调用原生代码

**关键方法**:
```java
// 启动 VPN 服务
private int startVpn() {
    // 配置 VPN 接口
    VpnService.Builder builder = new VpnService.Builder();
    builder.setSession("SocksTun");
    builder.addAddress("198.18.0.1", 32);        // IPv4
    builder.addAddress("fc00::1", 128);          // IPv6
    builder.setMtu(8500);
    builder.addRoute("0.0.0.0", 0);              // 默认路由
    builder.addRoute("::", 0);
    builder.addDnsServer("223.5.5.5");           // 国内 DNS
    builder.addDnsServer("2400:3200::1");        // 国内 IPv6 DNS

    // 建立隧道
    ParcelFileDescriptor tun = builder.establish();

    // 调用原生代码
    TProxyStartService(config_path, tun.getFd());
}

// JNI 接口
private static native void TProxyStartService(String config_path, int fd);
private static native void TProxyStopService();
private static native long[] TProxyGetStats();
```

---

### 2. hev-socks5-tunnel (隧道核心)

**文件**: `src/hev-socks5-tunnel.c`

**功能**:
- 从 TUN 接口读取 IP 数据包
- 解析 IP/UDP/TCP 协议头
- 分发到对应的会话处理器

**数据结构**:
```c
typedef struct _HevSocks5Tunnel {
    HevTask *task_lwip_io;      // lwIP I/O 任务
    HevTask *task_lwip_timer;   // lwIP 定时器任务
    int event_fd;               // 事件通知文件描述符
    int tun_fd;                 // TUN 接口文件描述符
    int running;                // 运行状态
} HevSocks5Tunnel;
```

**核心流程**:
```c
// 主循环
static void
socks5_tunnel_splice_task (void *data)
{
    while (tunnel->running) {
        // 从 TUN 读取数据包
        len = read (tunnel->tun_fd, buffer, sizeof (buffer));

        // 解析 IP 头
        ip_hdr = (struct iphdr *)buffer;

        // 根据协议分发
        if (ip_hdr->protocol == IPPROTO_TCP) {
            handle_tcp_packet (...);
        } else if (ip_hdr->protocol == IPPROTO_UDP) {
            handle_udp_packet (...);
        }
    }
}
```

---

### 3. hev-traffic-router (流量路由器)

**文件**: `src/hev-traffic-router.c`

**功能**:
- 基于规则的流量分流
- 国内/国外流量识别
- ACL 规则匹配

**路由决策**:
```c
typedef enum {
    ROUTE_DIRECT,      // 直接连接
    ROUTE_PROXY,       // SOCKS5 代理
    ROUTE_BLOCK,       // 阻断
    ROUTE_SMART_PROXY  // 智能代理
} RouteDecision;

RouteDecision
hev_traffic_router_route_tcp (const ip_addr_t *dest_addr,
                               u16_t dest_port,
                               const char *domain)
{
    // 1. 检查 ACL 规则
    if (hev_acl_check_domain (domain) == ACL_DENY)
        return ROUTE_BLOCK;

    // 2. 检查 chnroutes
    if (hev_match_chnroutes (dest_addr))
        return ROUTE_DIRECT;  // 国内直连

    // 3. 检查智能代理
    if (smart_proxy_enabled)
        return ROUTE_SMART_PROXY;

    // 4. 默认走代理
    return ROUTE_PROXY;
}
```

---

### 4. hev-session-manager (会话管理器)

**文件**: `src/hev-session-manager.c`

**功能**:
- 管理所有 TCP/UDP 会话
- 维护连接状态
- 处理会话超时
- 收集统计信息

**会话结构**:
```c
typedef struct _HevTCPSession {
    int alive;                    // 存活标志
    uint64_t tx_packets;          // 发送包数
    uint64_t tx_bytes;            // 发送字节数
    uint64_t rx_packets;          // 接收包数
    uint64_t rx_bytes;            // 接收字节数
    HevTask *task_fwd;            // 前向任务
    HevTask *task_bwd;            // 后向任务
    struct tcp_pcb *pcb;          // lwIP PCB
} HevTCPSession;
```

**超时处理**:
```c
static void
session_timeout_task (void *data)
{
    // 检查空闲会话
    LIST_FOREACH (session, &sessions) {
        if (session->idle_time > SESSION_TIMEOUT) {
            LOG_I ("%p session: idle timeout, terminating", session);
            hev_session_manager_terminate (session);
        }
    }
}
```

---

### 5. hev-filter (过滤器)

**文件**: `src/hev-filter.c`

**功能**:
- 访问控制列表 (ACL)
- SNI (Server Name Indication) 过滤
- IP 地址黑/白名单
- chnroutes 中国路由表

**过滤流程**:
```c
int
hev_filter_check_tcp (const uint8_t *data, size_t len,
                      const ip_addr_t *addr, u16_t port)
{
    // 1. 检查 IP 黑名单
    if (hev_acl_check_ip (addr) == ACL_DENY)
        return FILTER_DENY;

    // 2. 提取 TLS SNI
    if (port == 443) {
        char domain[256];
        if (extract_sni (data, len, domain)) {
            // 3. 检查域名黑名单
            if (hev_acl_check_domain (domain) == ACL_DENY)
                return FILTER_DENY;
        }
    }

    return FILTER_ALLOW;
}
```

---

### 6. hev-dns-latency (DNS 延迟优化)

**文件**: `src/hev-dns-latency.c`

**功能**:
- 当 DNS 返回多个 IP 时，自动测速选择延迟最低的
- 支持 IPv4/IPv6
- 异步测速，不阻塞 DNS 响应

**优化流程**:
```c
// DNS 响应包含多个 IP
IP 列表: [IP1, IP2, IP3, ...]
    │
    ▼
┌─────────────────────────────────────────┐
│  并发测速所有 IP                         │
│  ┌──────┐  ┌──────┐  ┌──────┐          │
│  │ IP1  │  │ IP2  │  │ IP3  │  ...     │
│  │ TCP  │  │ TCP  │  │ TCP  │          │
│  │ 443  │  │ 443  │  │ 443  │          │
│  └──┬───┘  └──┬───┘  └──┬───┘          │
│     │         │         │               │
│     └────┬────┴────┬────┘               │
│          ▼         ▼                     │
│     记录延迟   记录延迟                 │
│          └────┬────┘                    │
│               ▼                         │
│      选择最低延迟的 IP                    │
└─────────────────────────────────────────┘
```

**测速方法**:
```c
// 优先级: TCP 443 > TCP 80 > ICMP Ping
int
hev_dns_latency_test_ip (const ip_addr_t *ip,
                         DnsLatencyResult *result)
{
    // 1. 尝试 TCP 443
    if (tcp_connect_test (ip, 443, &result->latency_us) == 0)
        return 0;

    // 2. 尝试 TCP 80
    if (tcp_connect_test (ip, 80, &result->latency_us) == 0)
        return 0;

    // 3. 尝试 ICMP Ping
    if (icmp_ping_test (ip, &result->latency_us) == 0)
        return 0;

    return -1;  // 所有测试失败
}
```

---

### 7. hev-dns-cache (DNS 缓存)

**文件**: `src/hev-dns-cache.c`

**功能**:
- DNS 响应缓存
- DNS 污染检测
- 污染响应自动纠正

**缓存结构**:
```c
typedef struct _DnsCacheEntry {
    char domain[256];
    uint8_t *response_data;
    size_t response_len;
    uint32_t ttl;
    time_t expiry;
    int poisoned;  // 是否被污染
    LIST_ENTRY (DnsCacheEntry) next;
} DnsCacheEntry;
```

---

## 配置系统

### 配置格式

项目使用 YAML 格式配置文件，支持丰富的配置选项。

### 完整配置示例

```yaml
# ============================================
# 隧道配置
# ============================================
tunnel:
  mtu: 8500                    # MTU 大小
  ipv4: 198.18.0.1              # IPv4 地址
  ipv6: fc00::1                 # IPv6 地址

# ============================================
# SOCKS5 服务器配置
# ============================================
socks5:
  tcp:
    address: "your-server.com"  # 服务器地址
    port: 1080                  # TCP 端口
    username: "user"            # 认证用户名
    password: "pass"            # 认证密码
  udp:
    address: "your-server.com"  # UDP 服务器地址
    port: 1080                  # UDP 端口
    udp-relay: "tcp"            # UDP 中继模式 (tcp/udp)

# ============================================
# DNS 分流配置
# ============================================
dns-split-tunnel:
  enabled: true                 # 启用 DNS 分流
  domestic-dns:
    - "223.5.5.5"               # 国内 IPv4 DNS
    - "2400:3200::1"            # 国内 IPv6 DNS
  foreign-dns:
    - "1.1.1.1"                 # 国外 DNS
    - "8.8.8.8"
    - "2606:4700:4700::1111"    # IPv6 DNS
    - "2001:4860:4860::8888"

# ============================================
# DNS 延迟优化
# ============================================
dns-latency-optimize:
  enabled: true                 # 启用 DNS 延迟优化
  timeout-ms: 3000              # 测速总超时时间

# ============================================
# 智能代理配置
# ============================================
smart-proxy:
  enabled: true                 # 启用智能代理
  timeout-ms: 1300              # 直连超时时间
  blocked-ip-expiry-minutes: 360 # IP 阻断过期时间 (分钟)

# ============================================
# 中国路由表
# ============================================
chnroutes:
  enabled: true                 # 启用中国路由表
  file-path: "conf/chnroutes.txt"

# ============================================
# 访问控制列表
# ============================================
acl:
  enabled: true                 # 启用 ACL
  file-path: "conf/acl.txt"
  default-action: "proxy"       # 默认动作 (direct/proxy/block)

# ============================================
# 应用程序配置
# ============================================
app:
  log-level: "info"             # 日志级别 (debug/info/warn/error)
  tcp-keepalive:
    enabled: true
    idle: 60                    # 空闲时间 (秒)
    interval: 30                # 探测间隔 (秒)
    count: 3                    # 探测次数
```

### 配置文件生成

配置文件由 `ConfigGenerator.java` 根据用户设置动态生成：

```java
// 生成 SOCKS5 配置
yaml.append("socks5:\n");
yaml.append("  tcp:\n");
yaml.append("    address: \"").append(server).append("\"\n");
yaml.append("    port: ").append(port).append("\n");
if (username != null) {
    yaml.append("    username: \"").append(username).append("\"\n");
    yaml.append("    password: \"").append(password).append("\"\n");
}

// 生成 DNS 配置
yaml.append("dns-split-tunnel:\n");
yaml.append("  enabled: ").append(dnsSplitTunnel).append("\n");
yaml.append("  foreign-dns:\n");
for (String dns : foreignDnsList) {
    yaml.append("    - \"").append(dns).append("\"\n");
}
```

---

## JNI 接口

### 接口定义

| 方法 | 功能 | 参数 | 返回值 |
|------|------|------|--------|
| `TProxyStartService` | 启动隧道服务 | config_path (String), fd (int) | void |
| `TProxyStopService` | 停止服务 | - | void |
| `TProxyGetStats` | 获取流量统计 | - | long[4] |
| `TProxyGetLogs` | 获取日志 | max_lines (int) | String |

### 流量统计返回值

```java
long[] stats = TProxyGetStats();
// stats[0] = tx_packets  (发送包数)
// stats[1] = tx_bytes    (发送字节数)
// stats[2] = rx_packets  (接收包数)
// stats[3] = rx_bytes    (接收字节数)
```

### 原生实现

**文件**: `src/hev-jni.c`

```c
JNIEXPORT void JNICALL
Java_hev_sockstun_TProxyService_TProxyStartService (
    JNIEnv *env, jobject thiz, jstring config_path, jint fd)
{
    const char *path = (*env)->GetStringUTFChars (env, config_path, NULL);

    // 在新线程中启动隧道
    pthread_create (&thread, NULL, tunnel_thread, (void *)path);

    (*env)->ReleaseStringUTFChars (env, config_path, path);
}

static void *
tunnel_thread (void *data)
{
    char *config_path = (char *)data;

    // 初始化并运行隧道
    hev_socks5_tunnel_main_from_file (config_path, tun_fd);

    return NULL;
}
```

---

## 性能优化

### 1. 零拷贝技术

使用 Linux `splice` 系统调用实现零拷贝数据转发：

```c
// splice 在两个文件描述符之间移动数据，无需用户态缓冲
splice (src_fd, NULL, dst_fd, NULL, len, SPLICE_F_MOVE);
```

### 2. 异步 I/O

基于事件驱动的非阻塞 I/O：

```c
// 使用 poll 监听多个文件描述符
struct pollfd fds[] = {
    { .fd = tun_fd,    .events = POLLIN },
    { .fd = event_fd,  .events = POLLIN },
};

poll (fds, 2, -1);
```

### 3. 协程支持

使用 HevTask 实现轻量级协程：

```c
// 创建协程
HevTask *task = hev_task_new (stack_size);
hev_task_run (task, task_function, data);

// 主动让出 CPU
hev_task_yield (HEV_TASK_YIELD);
```

### 4. 内存池

使用对象池减少内存分配开销：

```c
// 分配对象
obj = hev_object_pool_alloc (pool);

// 归还对象
hev_object_pool_free (obj);
```

---

## 常见问题

### 1. DNS 延迟优化不工作？

**原因**: DNS 响应只包含一个 IP 地址

**解决**: 当 DNS 返回多个 IP 时才会启用优化

### 2. 智能代理仍然走代理？

**原因**: 目标 IP 被记录为阻断 IP

**解决**: 等待阻断过期时间（默认 360 分钟）或重启应用

### 3. DNS 分流不生效？

**原因**: mapdns 虚拟服务器配置错误

**解决**: 检查配置文件中的 DNS 服务器地址是否正确

### 4. 程序关闭卡死？

**原因**: DNS 延迟优化异步任务未正确清理

**解决**: 已修复，确保使用最新版本代码

---

## 开发指南

### 编译原生代码

```bash
cd app/src/main/jni/hev-socks5-tunnel
make clean
make -j4
```

### 调试日志

```bash
# 查看实时日志
adb logcat -s hev-socks5-tunnel

# 获取原生日志
adb shell cat /data/data/com.termux/files/home/sockstun/log.txt
```

### 性能分析

```bash
# 使用 perf 分析 CPU 性能
adb shell perf top -p $(pidof hev-socks5-tunnel)

# 使用 strace 跟踪系统调用
adb shell strace -p $(pidof hev-socks5-tunnel)
```

---

## 许可证

本项目遵循 GPL-3.0 许可证。

---

## 贡献

欢迎提交 Issue 和 Pull Request。

---

**文档版本**: 1.0
**最后更新**: 2026-01-06
