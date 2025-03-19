/*
 ============================================================================
 Name        : Preferences.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Preferences
 ============================================================================
 */

package hev.sockstun

import android.content.Context
import android.content.SharedPreferences

class Preferences(context: Context) {
    companion object {
        const val PREFS_NAME = "SocksPrefs"
        const val SOCKS_ADDR = "SocksAddr"
        const val SOCKS_PORT = "SocksPort"
        const val SOCKS_USER = "SocksUser"
        const val SOCKS_PASS = "SocksPass"
        const val DNS_IPV4 = "DnsIpv4"
        const val DNS_IPV6 = "DnsIpv6"
        const val IPV4 = "Ipv4"
        const val IPV6 = "Ipv6"
        const val UDP_IN_TCP = "UdpInTcp"
        const val APPS = "Apps"
        const val ENABLE = "Enable"
        const val EXCLUDE_ROUTES = "ExcludeRoutes"
        const val EXCLUDE_ROUTES_ENABLED = "ExcludeRoutesEnabled"
        const val APP_FILTER_MODE = "AppFilterMode"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var socksAddress: String
        get() = prefs.getString(SOCKS_ADDR, "127.0.0.1") ?: "127.0.0.1"
        set(addr) = prefs.edit().putString(SOCKS_ADDR, addr).apply()

    var socksPort: Int
        get() = prefs.getInt(SOCKS_PORT, 1080)
        set(port) = prefs.edit().putInt(SOCKS_PORT, port).apply()

    var socksUsername: String
        get() = prefs.getString(SOCKS_USER, "") ?: ""
        set(user) = prefs.edit().putString(SOCKS_USER, user).apply()

    var socksPassword: String
        get() = prefs.getString(SOCKS_PASS, "") ?: ""
        set(pass) = prefs.edit().putString(SOCKS_PASS, pass).apply()

    var dnsIpv4: String
        get() = prefs.getString(DNS_IPV4, "8.8.8.8") ?: "8.8.8.8"
        set(addr) = prefs.edit().putString(DNS_IPV4, addr).apply()

    var dnsIpv6: String
        get() = prefs.getString(DNS_IPV6, "2001:4860:4860::8888") ?: "2001:4860:4860::8888"
        set(addr) = prefs.edit().putString(DNS_IPV6, addr).apply()

    var isUdpInTcp: Boolean
        get() = prefs.getBoolean(UDP_IN_TCP, true)
        set(enable) = prefs.edit().putBoolean(UDP_IN_TCP, enable).apply()

    var isIpv4: Boolean
        get() = prefs.getBoolean(IPV4, true)
        set(enable) = prefs.edit().putBoolean(IPV4, enable).apply()

    var isIpv6: Boolean
        get() = prefs.getBoolean(IPV6, true)
        set(enable) = prefs.edit().putBoolean(IPV6, enable).apply()

    // 应用过滤模式 (0=全局, 1=绕行, 2=仅代理)
    var appFilterMode: Int
        get() = prefs.getInt(APP_FILTER_MODE, 0) // 默认为 0（全局模式）
        set(mode) = prefs.edit().putInt(APP_FILTER_MODE, mode).apply()

    fun getApps(): Set<String> = prefs.getStringSet(APPS, emptySet()) ?: emptySet()

    fun setApps(apps: Set<String>) = prefs.edit().putStringSet(APPS, apps).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean(ENABLE, false)
        set(enable) = prefs.edit().putBoolean(ENABLE, enable).apply()

    var isExcludeRoutes: Boolean
        get() = prefs.getBoolean(EXCLUDE_ROUTES_ENABLED, false)
        set(enable) = prefs.edit().putBoolean(EXCLUDE_ROUTES_ENABLED, enable).apply()

    var excludeRoutes: String
        get() = prefs.getString(EXCLUDE_ROUTES, "") ?: ""
        set(routes) = prefs.edit().putString(EXCLUDE_ROUTES, routes).apply()

    // 常量值，直接提供属性
    val tunnelMtu: Int = 8500
    val tunnelIpv4Address: String = "198.18.0.1"
    val tunnelIpv4Prefix: Int = 32
    val tunnelIpv6Address: String = "fc00::1"
    val tunnelIpv6Prefix: Int = 128
    val taskStackSize: Int = 81920
}