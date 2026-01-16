# hev-socks5-tunnel 项目规则

## AI 协作规则

**重要！在与 Claude AI 协作时必须遵守以下规则：**

1. **语言要求**: 始终使用中文回复用户
2. **代码原则**:
   - 优先复用现有代码，不要随意添加新功能
   - 除非经过用户明确同意，不得添加新功能
   - 保持代码简洁，避免过度工程化
3. **测试方式**:
   - 用户环境是 Termux（Android），**没有 root 权限**
   - 使用 `bash rsync.sh` 同步代码到远程服务器进行编译测试
   - 通过分析远程服务器的日志输出（test.log）来定位问题
   - 本地主要进行代码审查和日志分析
4. **失败回滚**:
   - **如果代码修改后测试不通过或失败，必须撤销刚刚的修改**
   - 不要留下失败的代码（避免屎山积累）
   - 在尝试新方案前先恢复到工作状态
5. **清理测试残留**:
   - **不要随意创建测试文件**
   - 每次测试完必须清理测试文件残留
   - 清理测试残留的旧进程
   - 保持工作目录整洁

## 项目概述

这是一个 Android VPN 应用，基于 hev-socks5-tunnel 实现，具有 DNS 污染检测和智能代理功能。

## 项目结构

```
sockstun/
├── app/                          # Android 应用模块
│   └── src/main/jni/
│       └── hev-socks5-tunnel/    # 原生 C 代码库
│           ├── src/              # 源代码
│           │   ├── core/         # SOCKS5 核心实现
│           │   ├── hev-config.c  # 配置解析
│           │   ├── hev-dns-cache.c        # DNS 缓存和污染检测
│           │   └── hev-session-manager.c  # 会话管理
│           ├── conf/             # 配置文件示例
│           └── test.log          # 运行日志
├── tun2socks/                    # tun2socks 模块
└── rsync.sh                      # 开发同步脚本
```

## 核心技术概念

### DNS 污染检测
- 系统检测 DNS 响应中的国外 IP 地址
- 检测到污染后通过 SOCKS5 代理重新查询国外 DNS
- 相关文件: `hev-dns-cache.c`

### SOCKS5 协议
- 支持 TCP 和 UDP associate
- UDP_IN_UDP (udp-relay=1): 直接 UDP 模式
- UDP_IN_TCP (udp-relay=0): UDP over TCP 模式
- 相关文件: `src/core/src/hev-socks5-client.c`

### 智能代理模式
- 先尝试直连，超时后切换到 SOCKS5
- 探测超时: 600ms
- 相关代码: `hev-session-manager.c`

### 错误码
```
HEV_SOCKS5_RES_REP_SUCC = 0   // 成功
HEV_SOCKS5_RES_REP_FAIL = 1   // 一般失败
HEV_SOCKS5_RES_REP_HOST = 4   // 主机不可达
HEV_SOCKS5_RES_REP_IMPL = 7   // 连接被拒绝
HEV_SOCKS5_RES_REP_ADDR = 8   // 地址类型不支持
```

## 配置文件

### DNS 分流配置 (tproxy.conf)
```yaml
dns-split-tunnel:
  enabled: true
  domestic-dns:              # 国内 DNS（仅 IPv4 推荐）
    - "223.5.5.5"
    - "119.29.29.29"
  foreign-dns:               # 国外 DNS（仅 IPv4）
    - "1.1.1.1"
    - "8.8.8.8"
```

### SOCKS5 配置
```yaml
socks5:
  tcp:
    port: 1080
    address: '64.69.34.166'
    username: 'xxx'
    password: 'xxx'
  udp-relay: 0  # 0=UDP_IN_TCP, 1=UDP_IN_UDP
```

### 优化参数
```yaml
misc:
  udp-buffer-size: 524288      # UDP 缓冲 512KB
  tcp-buffer-size: 65536       # TCP 缓冲 64KB
  udp-read-write-timeout: 60000  # UDP 超时 60秒（不要低于5000）
  tcp-connect-timeout: 5000    # TCP 连接超时
```

## 已知问题

### DNS 污染检测失败
**现象**: `socks5 client res.rep 4` 错误
**原因**: SOCKS5 代理无法通过 UDP associate 到达国外 DNS 服务器
**解决方案**:
1. 检查 SOCKS5 代理是否支持 UDP associate
2. 使用 `udp-relay: 0` (UDP over TCP)
3. 更换支持完整 SOCKS5 协议的代理

### IPv6 DNS 查询问题
**现象**: IPv6 污染检测后 SOCKS5 握手失败
**原因**: SOCKS5 代理可能不支持 IPv6 连接
**解决方案**: 配置仅使用 IPv4 国内 DNS 服务器

## 开发工作流程

由于本地环境是 Termux（无 root），采用远程开发模式：

### 1. 修改代码
在本地编辑器中修改代码文件

### 2. 同步到远程服务器并测试
```bash
# 同步代码到远程服务器并自动编译测试
bash rsync.sh
```

`rsync.sh` 脚本会：
- 同步代码到 `root@64.69.34.166:/tmp/hev-socks5-tunnel`
- 在远程服务器执行 `make dev` 编译
- 运行测试脚本

### 3. 查看远程日志分析问题
```bash
# 拉取最新日志（可选）
ssh -p 2233 root@64.69.34.166 "tail -100 /tmp/hev-socks5-tunnel/test.log"

# 或者直接在远程查看日志
ssh -p 2233 root@64.69.34.166
tail -f /tmp/hev-socks5-tunnel/test.log
```

### 4. 常用日志分析命令
```bash
# 搜索 DNS 污染检测日志
ssh -p 2233 root@64.69.34.166 "grep -i 'pollution\|foreign-dns' /tmp/hev-socks5-tunnel/test.log"

# 搜索 SOCKS5 错误
ssh -p 2233 root@64.69.34.166 "grep 'socks5 client res.rep' /tmp/hev-socks5-tunnel/test.log"

# 查看最新日志
ssh -p 2233 root@64.69.34.166 "tail -200 /tmp/hev-socks5-tunnel/test.log"
```

## 代码修改原则

**核心原则：复用现有代码，最小化修改**

1. **优先复用**: 检查是否有现有函数或逻辑可以复用
2. **最小改动**: 只修改必要的代码，不要重构无关部分
3. **保持兼容**: 不破坏现有功能和配置格式
4. **询问用户**: 添加新功能前必须先询问用户并获得同意

### 模块修改注意事项

1. **DNS 相关** (`hev-dns-cache.c`):
   - 小心处理污染检测逻辑
   - 注意 IPv4/IPv6 的区分处理
   - 修改后重点测试 DNS 污染场景

2. **SOCKS5 客户端** (`src/core/src/hev-socks5-client.c`):
   - 注意协议兼容性
   - UDP associate 和 TCP 模式差异
   - 修改后测试 SOCKS5 握手流程

3. **配置解析** (`hev-config.c`):
   - 保持向后兼容
   - 新增配置要有默认值

4. **缓冲区管理**:
   - UDP 需要 8x 于 TCP 的缓冲区（无流控制）
   - UDP: 512KB, TCP: 64KB

## 测试

### 测试 SOCKS5 代理连接
```bash
curl -v -x socks5://user:pass@host:port https://1.1.1.1
```

### 测试 DNS 解析
```bash
curl -v https://v2ex.com
```

## 联系方式

- 项目路径: `/data/data/com.termux/files/home/sockstun`
- 子模块: `app/src/main/jni/hev-socks5-tunnel`
