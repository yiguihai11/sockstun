/*
 ============================================================================
 Name        : TProxyService.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : TProxy Service
 ============================================================================
 */

package hev.sockstun

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.IpPrefix
import android.net.InetAddresses
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class TProxyService : VpnService() {
    companion object {
        const val ACTION_CONNECT = "hev.sockstun.CONNECT"
        const val ACTION_DISCONNECT = "hev.sockstun.DISCONNECT"
        const val ACTION_STATUS_CHANGED = "hev.sockstun.STATUS_CHANGED"
        private const val CHANNEL_NAME = "socks5"
        private const val NOTIFICATION_ID = 1

        // JNI 方法
        @JvmStatic
        private external fun TProxyStartService(configPath: String, fd: Int)
        @JvmStatic
        private external fun TProxyStopService()
        @JvmStatic
        private external fun TProxyGetStats(): LongArray?

        init {
            System.loadLibrary("hev-socks5-tunnel")
        }

        fun getStats(): LongArray? = TProxyGetStats()
    }

    private var tunFd: android.os.ParcelFileDescriptor? = null
    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            statsHandler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            handleServiceStop()
            return START_NOT_STICKY
        }
        startService()
        return START_STICKY
    }

    override fun onDestroy() {
        handleServiceStop()
        super.onDestroy()
    }

    override fun onRevoke() {
        handleServiceStop()
        super.onRevoke()
    }

    private fun handleServiceStop() {
        // 停止统计更新
        statsHandler.removeCallbacks(statsRunnable)

        // 停止前台服务（修正 API 34 以上的调用）
        stopForeground(STOP_FOREGROUND_REMOVE)

        // 关闭VPN接口
        tunFd?.let {
            try {
                it.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            tunFd = null
        }

        // 停止TProxy服务
        TProxyStopService()

        // 更新服务状态
        Preferences(this).isEnabled = false

        // 广播状态变更
        sendStatusChangeBroadcast(false)

        // 结束进程
        //exitProcess(0)
    }

    private fun startService() {
        // 避免重复启动
        if (tunFd != null) return

        val prefs = Preferences(this)

        try {
            setupVpnInterface(prefs)
            setupTProxyService(prefs)

            // 更新服务状态
            prefs.isEnabled = true
            
            // 广播状态变更
            sendStatusChangeBroadcast(true)

            // 启动前台服务
            initNotificationChannel()
            createNotification("")
            statsHandler.post(statsRunnable)

        } catch (e: Exception) {
            e.printStackTrace()
            handleServiceStop()
        }
    }

    private fun setupVpnInterface(prefs: Preferences) {
        val builder = Builder().apply {
            setBlocking(false)
            setMtu(prefs.tunnelMtu)

            // 配置IPv4
            if (prefs.isIpv4) {
                addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
                addRoute("0.0.0.0", 0)
                if (prefs.dnsIpv4.isNotEmpty()) {
                    addDnsServer(prefs.dnsIpv4)
                }
            }

            // 配置IPv6
            if (prefs.isIpv6) {
                addAddress(prefs.tunnelIpv6Address, prefs.tunnelIpv6Prefix)
                addRoute("::", 0)
                if (prefs.dnsIpv6.isNotEmpty()) {
                    addDnsServer(prefs.dnsIpv6)
                }
            }
            
            // 处理排除路由
            if (prefs.isExcludeRoutes && prefs.excludeRoutes.isNotEmpty()) {
                setupExcludeRoutes(this, prefs.excludeRoutes)
            }

            // 配置应用过滤
            setupAppFilter(this, prefs)
        }

        // 建立 VPN 接口
        tunFd = builder.establish() ?: throw IOException("Failed to establish VPN interface")
    }
    
    private fun setupExcludeRoutes(builder: Builder, excludeRoutesText: String) {
        // 拆分多行内容
        val lines = excludeRoutesText.trim().split("\\s*[\\r\\n]+\\s*".toRegex())
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue
            
            try {
                // IPv4格式匹配
                val ipv4WithPrefixRegex = """^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/(\d{1,2})$""".toRegex()
                val ipv4OnlyRegex = """^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})$""".toRegex()
                
                // IPv6格式匹配
                val ipv6WithPrefixRegex = """^([0-9a-fA-F:]+)/(\d{1,3})$""".toRegex()
                val ipv6OnlyRegex = """^([0-9a-fA-F:]+)$""".toRegex()
                
                when {
                    // IPv4 CIDR格式处理
                    ipv4WithPrefixRegex.matches(trimmedLine) -> {
                        val match = ipv4WithPrefixRegex.find(trimmedLine)
                        val ip = match?.groupValues?.get(1) ?: continue
                        val prefix = match.groupValues[2].toIntOrNull() ?: continue
                        
                        if (isValidIpv4Address(ip) && prefix in 0..32) {
                            val inetAddress = InetAddresses.parseNumericAddress(ip)
                            builder.excludeRoute(IpPrefix(inetAddress, prefix))
                        }
                    }
                    // 单个IPv4地址处理
                    ipv4OnlyRegex.matches(trimmedLine) -> {
                        val match = ipv4OnlyRegex.find(trimmedLine)
                        val ip = match?.groupValues?.get(1) ?: continue
                        
                        if (isValidIpv4Address(ip)) {
                            val inetAddress = InetAddresses.parseNumericAddress(ip)
                            builder.excludeRoute(IpPrefix(inetAddress, 32))
                        }
                    }
                    // IPv6 CIDR格式处理
                    ipv6WithPrefixRegex.matches(trimmedLine) -> {
                        val match = ipv6WithPrefixRegex.find(trimmedLine)
                        val ip = match?.groupValues?.get(1) ?: continue
                        val prefix = match.groupValues[2].toIntOrNull() ?: continue
                        
                        if (isValidIpv6Address(ip) && prefix in 0..128) {
                            val inetAddress = InetAddresses.parseNumericAddress(ip)
                            builder.excludeRoute(IpPrefix(inetAddress, prefix))
                        }
                    }
                    // 单个IPv6地址处理
                    ipv6OnlyRegex.matches(trimmedLine) -> {
                        val match = ipv6OnlyRegex.find(trimmedLine)
                        val ip = match?.groupValues?.get(1) ?: continue
                        
                        if (isValidIpv6Address(ip)) {
                            val inetAddress = InetAddresses.parseNumericAddress(ip)
                            builder.excludeRoute(IpPrefix(inetAddress, 128))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun isValidIpv4Address(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            
            parts.all { part ->
                val num = part.toIntOrNull() ?: return false
                num in 0..255
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isValidIpv6Address(ip: String): Boolean {
        return try {
            // 基本格式检查
            if (ip.isEmpty() || !ip.contains(":")) return false
            
            // 检查冒号数量和格式
            val parts = ip.split(":")
            
            // IPv6地址中冒号分隔的部分不能超过8个
            if (parts.size > 8) return false
            
            // 检查每一部分是否是有效的16进制
            for (part in parts) {
                // 允许空字符串(连续冒号的情况)
                if (part.isEmpty()) continue
                
                // 每部分最多4个16进制字符
                if (part.length > 4) return false
                
                // 检查是否全为16进制字符
                if (!part.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    return false
                }
            }
            
            // 基本检查通过
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setupAppFilter(builder: Builder, prefs: Preferences) {
        // 根据应用过滤模式处理
        when (prefs.appFilterMode) {
            MainActivity.APP_FILTER_MODE_OFF -> {
                // 全局模式：所有应用都通过VPN，仅让VPN服务自己绕过
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            MainActivity.APP_FILTER_MODE_BYPASS -> {
                // 选定的应用绕过VPN
                val apps = prefs.getApps()
                
                // 如果没有选择应用，则默认让VPN服务自己绕过
                var disallowSelf = true
                
                // 添加需要绕过的应用
                apps.forEach { appName ->
                    try {
                        builder.addDisallowedApplication(appName)
                        if (appName == packageName) {
                            disallowSelf = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // 确保VPN服务本身也绕过
                if (disallowSelf) {
                    try {
                        builder.addDisallowedApplication(packageName)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            MainActivity.APP_FILTER_MODE_ONLY -> {
                // 仅选定的应用使用VPN
                val apps = prefs.getApps()
                
                // 如果没有选择应用，则让所有应用都不通过VPN
                if (apps.isEmpty()) {
                    try {
                        builder.addDisallowedApplication(packageName)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return
                }
                
                // 添加需要使用VPN的应用
                apps.forEach { appName ->
                    try {
                        builder.addAllowedApplication(appName)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return if (bytesPerSecond >= 1024 * 1024) {
            String.format(Locale.US, "%.2f MB/s", bytesPerSecond.toDouble() / (1024 * 1024))
        } else {
            String.format(Locale.US, "%.2f KB/s", bytesPerSecond.toDouble() / 1024)
        }
    }

    private fun updateNotification() {
        getStats()?.let { stats ->
            val text = String.format(
                Locale.US,
                "↑ %s  ↓ %s\n↑ %d pkt/s  ↓ %d pkt/s",
                formatSpeed(stats[1]),
                formatSpeed(stats[3]),
                stats[0],
                stats[2]
            )
            createNotification(text)
        }
    }

    private fun setupTProxyService(prefs: Preferences) {
        val tproxyFile = File(cacheDir, "tproxy.conf")
        try {
            tproxyFile.createNewFile()
            FileOutputStream(tproxyFile, false).use { fos ->
                val tproxyConf = buildString {
                    append("misc:\n")
                    append("  task-stack-size: ${prefs.taskStackSize}\n")
                    append("tunnel:\n")
                    append("  mtu: ${prefs.tunnelMtu}\n")
                    append("socks5:\n")
                    append("  port: ${prefs.socksPort}\n")
                    append("  address: '${prefs.socksAddress}'\n")
                    append("  udp: '${if (prefs.isUdpInTcp) "tcp" else "udp"}'\n")

                    if (prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
                        append("  username: '${prefs.socksUsername}'\n")
                        append("  password: '${prefs.socksPassword}'\n")
                    }
                }
                fos.write(tproxyConf.toByteArray())
            }
        } catch (e: IOException) {
            return
        }

        TProxyStartService(tproxyFile.absolutePath, tunFd!!.fd)
    }

    private fun createNotification(stats: String = "") {
        // 让通知打开 MainActivity
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // 让用户手动停止 VPN
        val stopIntent = Intent(this, TProxyService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val stopPendingIntent =
            PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_NAME)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentText(stats)
            .setStyle(NotificationCompat.BigTextStyle().bigText(stats))
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.stop), stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = getString(R.string.app_name)
        val channel =
            NotificationChannel(CHANNEL_NAME, name, NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun sendStatusChangeBroadcast(isRunning: Boolean) {
        val intent = Intent(ACTION_STATUS_CHANGED)
        intent.putExtra("running", isRunning)
        sendBroadcast(intent)
    }
} 