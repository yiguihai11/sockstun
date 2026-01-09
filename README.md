# SocksTun

[![status](https://github.com/yiguihai11/sockstun/actions/workflows/build.yaml/badge.svg?branch=main&event=push)](https://github.com/yiguihai11/sockstun/actions)

一款基于 SOCKS5 代理的 Android VPN 客户端，简洁轻量，基于高性能的 [hev-socks5-tunnel](https://github.com/yiguihai11/hev-socks5-tunnel) 实现。

## 下载安装

[<img src="https://github.com/yiguihai11/sockstun/raw/main/.github/badges/get-it-on.png"
    alt="Download APK"
    height="80">](https://github.com/yiguihai11/sockstun/releases/tag/latest)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/hev.sockstun)

## 主要功能

- TCP 连接代理
- UDP 数据包代理（支持 Fullcone NAT、UDP-in-UDP 和 UDP-in-TCP [^1]）
- 简单的用户名/密码认证
- 自定义 DNS 服务器
- IPv4/IPv6 双栈支持
- 全局/按应用代理模式
- 中国路由表分流（Chnroutes）
- ACL 访问控制
- DNS 分流隧道
- DNS 转发器
- DNS 延迟优化
- Smart Proxy 智能代理切换

## 编译方法

Fork 本项目并创建 Release，或手动编译：

```bash
git clone --recursive https://github.com/yiguihai11/sockstun
cd sockstun
./gradlew assembleDebug
```

## SOCKS5 服务器

### UDP over TCP 中转

```bash
git clone --recursive https://github.com/heiher/hev-socks5-server
cd hev-socks5-server
make

hev-socks5-server conf.yml
```

```yaml
main:
  workers: 4
  port: 1080
  listen-address: '::'

misc:
  limit-nofile: 65535
```

### UDP over UDP 中转

任何实现了 RFC1928 的 CONNECT 和 UDP-ASSOCIATE 方法的 SOCKS5 服务器。

## 相关项目

- HevSocks5Tunnel - https://github.com/yiguihai11/hev-socks5-tunnel
- HevSocks5Server - https://github.com/heiher/hev-socks5-server

## 贡献者

- **hev** - https://hev.cc
- **ziqi mo** - https://github.com/mosentest

## 许可证

MIT License

[^1]: 详见 [协议规范](https://github.com/heiher/hev-socks5-core/tree/main?tab=readme-ov-file#udp-in-tcp)。[hev-socks5-server](https://github.com/heiher/hev-socks5-server) 支持 UDP over TCP 中转。
