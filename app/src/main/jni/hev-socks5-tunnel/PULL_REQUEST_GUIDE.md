# Pull Request 创建指南

## 当前状态

您的更改已经成功提交到本地分支 `feature/simple-forward-implementation`。由于权限限制，无法直接推送到原仓库，您需要通过以下方式创建 Pull Request：

## 方法 1: Fork 仓库（推荐）

1. **Fork 仓库**
   - 访问 https://github.com/heiher/hev-socks5-tunnel
   - 点击页面右上角的 "Fork" 按钮
   - 将仓库 fork 到您自己的 GitHub 账户

2. **添加远程仓库**
   ```bash
   git remote add fork https://github.com/YOUR_USERNAME/hev-socks5-tunnel
   git push -u fork feature/simple-forward-implementation
   ```

3. **创建 Pull Request**
   - 访问您的 fork 仓库
   - 点击 "New Pull Request" 按钮
   - 选择从 `feature/simple-forward-implementation` 分支到 `main` 分支
   - 填写 PR 描述并提交

## 方法 2: 创建补丁文件

如果您不想 fork，可以创建补丁文件：

```bash
# 创建补丁文件
git format-patch origin/main --stdout > simple-forward-implementation.patch

# 上传补丁文件到 GitHub Issues 或发送给项目维护者
```

## Pull Request 模板

### 标题
`Add simple traffic forwarding implementation to replace SOCKS5 core`

### 描述

这个 Pull Request 添加了一个简单的流量转发实现，用于替换复杂的 SOCKS5 core 库。

#### 主要更改：
- 新增 `src/custom/` 目录，包含 TCP/UDP 转发实现
- 修改 `src/hev-socks5-tunnel.c` 以使用新的转发机制
- 更新构建系统以包含自定义代码
- 移除对 hev-socks5-core 库的依赖

#### 功能特性：
- 所有网络流量转发到本地端口 1080
- 支持 TCP 和 UDP 协议
- 兼容现有的构建系统
- 保持原有的 TUN 设备接口

#### 使用场景：
- 配合外部 Go 程序监听端口 1080
- 简化 SOCKS5 隧道架构
- 更灵活的流量处理方式

#### 测试状态：
- ✅ 自定义代码编译成功
- ⚠️ 由于环境限制（Termux LWIP 冲突），完整构建需要在标准 Linux 环境中测试
- 📋 建议在 CI/CD 环境中进行完整测试

## CI/CD 期望结果

一旦 PR 被接受并合并，GitHub Actions CI 将会：

1. **多平台构建**
   - Linux (x86_64)
   - FreeBSD
   - macOS
   - Windows (MSYS2)
   - Android (通过 NDK)

2. **编译验证**
   - 验证自定义转发代码的正确性
   - 确保没有引入新的编译错误
   - 验证与现有代码的兼容性

3. **测试执行**
   - 运行项目自带的测试套件
   - 验证转发功能的正确性

## 联系信息

如果需要更多信息或有任何问题，请：
- 在 Pull Request 中添加评论
- 或通过 GitHub Issues 联系项目维护者