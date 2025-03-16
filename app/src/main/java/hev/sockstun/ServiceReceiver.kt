/*
 ============================================================================
 Name        : ServiceReceiver.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : ServiceReceiver
 ============================================================================
 */

package hev.sockstun

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

class ServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        
        val prefs = Preferences(context)
        if (!prefs.isEnabled) return

        // 准备VPN服务
        prepareVpnService(context)
        
        // 启动VPN服务
        startVpnService(context)
    }
    
    private fun prepareVpnService(context: Context) {
        VpnService.prepare(context)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun startVpnService(context: Context) {
        Intent(context, TProxyService::class.java).apply {
            action = TProxyService.ACTION_CONNECT
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(this)
                } else {
                    context.startService(this)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
} 