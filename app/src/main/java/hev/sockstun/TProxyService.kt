/*
 ============================================================================
 Name        : TProxyService.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : TProxy Service
 ============================================================================
 */

package hev.sockstun

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.InetAddresses
import android.net.IpPrefix
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date

class TProxyService : VpnService() {
    companion object {
        private const val TAG = "TProxyService"
        const val ACTION_CONNECT = "hev.sockstun.START"
        const val ACTION_DISCONNECT = "hev.sockstun.STOP"
        const val ACTION_STATUS_CHANGED = "hev.sockstun.STATUS_CHANGED"
        const val ACTION_LOG_EVENT = "hev.sockstun.LOG_EVENT"
        const val EXTRA_IS_RUNNING = "running"
        const val EXTRA_LOG_TAG = "tag"
        const val EXTRA_LOG_MESSAGE = "message"
        private const val CHANNEL_NAME = "socks5"
        private const val NOTIFICATION_ID = 1

        // 服务运行状态
        @Volatile
        var isRunning = false
            private set
            
        // 状态锁，防止并发操作
        private val stateLock = Object()
        
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

    private var isProcessing = false
    private var tunFd: android.os.ParcelFileDescriptor? = null
    private lateinit var logFile: File
    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            statsHandler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(stateLock) {
            if (isProcessing) {
                Log.w(TAG, "服务正在处理中，忽略重复请求")
                return START_STICKY
            }
            
            isProcessing = true
            try {
                when(intent?.action) {
                    ACTION_CONNECT -> {
                        if (!isRunning) {
                            // 先设置状态为运行中
                            isRunning = true
                            Preferences(this).isEnabled = true
                            sendStatusChangeBroadcast(true)
                            
                            // 然后启动服务
                            startService()
                        }
                    }
                    ACTION_DISCONNECT -> {
                        if (isRunning) {
                            // 先停止服务
                            handleServiceStop()
                            
                            // 然后更新状态
                            isRunning = false
                            Preferences(this).isEnabled = false
                            sendStatusChangeBroadcast(false)
                            
                            // 停止前台服务
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            
                            // 停止服务
                            stopSelf()
                        }
                    }
                }
            } finally {
                isProcessing = false
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        synchronized(stateLock) {
            if (isRunning) {
                // 先停止服务
                handleServiceStop()
                
                // 然后更新状态
                isRunning = false
                Preferences(this).isEnabled = false
                sendStatusChangeBroadcast(false)
            }
        }
        
        super.onDestroy()
    }

    override fun onRevoke() {
        synchronized(stateLock) {
            if (isRunning) {
                // 先停止服务
                handleServiceStop()
                
                // 然后更新状态
                isRunning = false
                Preferences(this).isEnabled = false
                sendStatusChangeBroadcast(false)
            }
        }
        
        super.onRevoke()
    }

    override fun onCreate() {
        super.onCreate()
        
        // 初始化日志文件
        logFile = File(filesDir, "app.log")
        
        // 获取ShadowSocks版本信息
        val executor = NativeProgramExecutor(this)
        val version = executor.getSsLocalVersion()
        Log.i(TAG, "ShadowSocks本地版本: $version")
    }

    private fun logToUI(tag: String, message: String) {
        val intent = Intent(ACTION_LOG_EVENT)
        intent.putExtra(EXTRA_LOG_TAG, tag)
        intent.putExtra(EXTRA_LOG_MESSAGE, message)
        sendBroadcast(intent, null)
        
        // 同时写入文件
        writeLogToFile(tag, message)
    }
    
    private fun writeLogToFile(tag: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "$timestamp $tag: $message\n"
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    private fun handleServiceStop() {
        try {
            // 停止统计更新
            statsHandler.removeCallbacks(statsRunnable)
            
            // 停止前台服务
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            // 关闭VPN接口
            tunFd?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e(TAG, "关闭VPN接口时出错", e)
                    logToUI(TAG, "关闭VPN接口时出错: ${e.message}")
                }
                tunFd = null
            }
            
            // 停止TProxy服务
            TProxyStopService()
            
            // 记录日志
            Log.i(TAG, "VPN服务已停止")
            logToUI(TAG, "VPN服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止服务时出错", e)
            logToUI(TAG, "停止服务时出错: ${e.message}")
        }
    }

    private fun startService() {
        try {
            // 初始化通知渠道
            initNotificationChannel()
            
            // 创建通知并启动前台服务
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                // API 34 及以上，使用 ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                // API 33 及以下，不传递 service type
                startForeground(NOTIFICATION_ID, notification)
            }
            
            val prefs = Preferences(this)
            setupVpnInterface(prefs)
            setupTProxyService(prefs)
            
            // 启动后台线程更新通知
            startStatsUpdateThread()
            
            // 记录日志
            Log.i(TAG, "VPN服务已启动")
            logToUI(TAG, "VPN服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动服务时出错", e)
            logToUI(TAG, "启动服务时出错: ${e.message}")
            
            // 发生错误，停止服务并更新状态
            handleServiceStop()
            isRunning = false
            Preferences(this).isEnabled = false
            sendStatusChangeBroadcast(false)
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
            val notification = createNotification(text)
            
            // 更新前台服务通知
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
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

    private fun createNotification(stats: String = ""): Notification {
        // 让通知打开 MainActivity
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        // 让用户手动停止 VPN
        val stopIntent = Intent(this, TProxyService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 
            0, 
            stopIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_NAME)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(stats)
            .setStyle(NotificationCompat.BigTextStyle().bigText(stats))
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopPendingIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun initNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = getString(R.string.app_name)
        val channel = NotificationChannel(CHANNEL_NAME, name, NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun sendStatusChangeBroadcast(isRunning: Boolean) {
        val intent = Intent(ACTION_STATUS_CHANGED)
        intent.putExtra(EXTRA_IS_RUNNING, isRunning)
        sendBroadcast(intent, null)
        Log.d(TAG, "发送状态变更广播：isRunning=$isRunning")
        
        // 也通过日志记录
        logToUI(TAG, "VPN状态变更：${if (isRunning) "已启动" else "已停止"}")
    }

    // 统计更新线程
    private fun startStatsUpdateThread() {
        // 启动后台线程更新统计信息
        statsHandler.post(statsRunnable)
    }

    // 发送日志广播
    private fun sendLogBroadcast(tag: String, message: String) {
        val intent = Intent(ACTION_LOG_EVENT)
        intent.putExtra(EXTRA_LOG_TAG, tag)
        intent.putExtra(EXTRA_LOG_MESSAGE, message)
        sendBroadcast(intent, null) // 不需要权限
    }
}