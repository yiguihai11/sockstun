/*
 ============================================================================
 Name        : Perferences.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Perferences
 ============================================================================
 */

package hev.sockstun;

import java.util.Set;
import java.util.HashSet;
import android.content.Context;
import android.content.SharedPreferences;

public class Preferences
{
	public static final String PREFS_NAME = "SocksPrefs";
	public static final String SOCKS_ADDR = "SocksAddr";
	public static final String SOCKS_PORT = "SocksPort";
	public static final String SOCKS_USER = "SocksUser";
	public static final String SOCKS_PASS = "SocksPass";
	public static final String DNS_IPV4 = "DnsIpv4";
	public static final String DNS_IPV6 = "DnsIpv6";
	public static final String IPV4 = "Ipv4";
	public static final String IPV6 = "Ipv6";
	public static final String GLOBAL = "Global";
	public static final String UDP_IN_TCP = "UdpInTcp";
	public static final String REMOTE_DNS = "RemoteDNS";
	public static final String APPS = "Apps";
	public static final String ENABLE = "Enable";

	// 新增配置项
	public static final String UDP_ADDR = "UdpAddr";
	public static final String UDP_PORT = "UdpPort";
	public static final String UDP_USER = "UdpUser";
	public static final String UDP_PASS = "UdpPass";
	public static final String UDP_RELAY_MODE = "UdpRelayMode";
	public static final String CHNROUTES_ENABLED = "ChnroutesEnabled";
	public static final String ACL_ENABLED = "AclEnabled";
	public static final String SMART_PROXY_ENABLED = "SmartProxyEnabled";
	public static final String SMART_PROXY_TIMEOUT = "SmartProxyTimeout";
	public static final String SMART_PROXY_BLOCK_EXPIRY = "SmartProxyBlockExpiry";

	private SharedPreferences prefs;

	public Preferences(Context context) {
		prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
	}

	// 保持向后兼容的方法
	public String getSocksAddress() {
		return getSocksAddr();
	}

	public void setSocksAddress(String addr) {
		setSocksAddr(addr);
	}

	public int getSocksPort() {
		return Integer.parseInt(getSocksPortStr());
	}

	public void setSocksPort(int port) {
		setSocksPortStr(String.valueOf(port));
	}

	public String getSocksUsername() {
		return getSocksUser();
	}

	public void setSocksUsername(String user) {
		setSocksUser(user);
	}

	public String getSocksPassword() {
		return getSocksPass();
	}

	public void setSocksPassword(String pass) {
		setSocksPass(pass);
	}

	// 新的统一命名方法
	public String getSocksAddr() {
		return prefs.getString(SOCKS_ADDR, "64.69.34.166");
	}

	public void setSocksAddr(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_ADDR, addr);
		editor.commit();
	}

	public String getSocksPortStr() {
		return prefs.getString(SOCKS_PORT, "1080");
	}

	public void setSocksPortStr(String port) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_PORT, port);
		editor.commit();
	}

	public String getSocksUser() {
		return prefs.getString(SOCKS_USER, "yiguihai");
	}

	public void setSocksUser(String user) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_USER, user);
		editor.commit();
	}

	public String getSocksPass() {
		return prefs.getString(SOCKS_PASS, "ygh15177542493");
	}

	public void setSocksPass(String pass) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_PASS, pass);
		editor.commit();
	}

	public String getDnsIpv4() {
		return prefs.getString(DNS_IPV4, "8.8.8.8");
	}

	public void setDnsIpv4(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DNS_IPV4, addr);
		editor.commit();
	}

	public String getDnsIpv6() {
		return prefs.getString(DNS_IPV6, "2001:4860:4860::8888");
	}

	public void setDnsIpv6(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DNS_IPV6, addr);
		editor.commit();
	}

	public String getMappedDns() {
		return "198.18.0.2";
	}

	public boolean getUdpInTcp() {
		return prefs.getBoolean(UDP_IN_TCP, false);
	}

	public void setUdpInTcp(boolean enable) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(UDP_IN_TCP, enable);
		editor.commit();
	}

	public boolean getRemoteDns() {
		return prefs.getBoolean(REMOTE_DNS, true);
	}

	public void setRemoteDns(boolean enable) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(REMOTE_DNS, enable);
		editor.commit();
	}

	public boolean getIpv4() {
		return prefs.getBoolean(IPV4, true);
	}

	public void setIpv4(boolean enable) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(IPV4, enable);
		editor.commit();
	}

	public boolean getIpv6() {
		return prefs.getBoolean(IPV6, true);
	}

	public void setIpv6(boolean enable) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(IPV6, enable);
		editor.commit();
	}

	public boolean getGlobal() {
		return prefs.getBoolean(GLOBAL, false);
	}

	public void setGlobal(boolean enable) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(GLOBAL, enable);
		editor.commit();
	}

	public Set<String> getApps() {
		return prefs.getStringSet(APPS, new HashSet<String>());
	}

	public void setApps(Set<String> apps) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putStringSet(APPS, apps);
		editor.commit();
	}

	public boolean getEnable() {
		return prefs.getBoolean(ENABLE, false);
	}

	public void setEnable(boolean enable) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(ENABLE, enable);
		editor.commit();
	}

	public int getTunnelMtu() {
		return 8500;
	}

	public String getTunnelIpv4Address() {
		return "198.18.0.1";
	}

	public int getTunnelIpv4Prefix() {
		return 32;
	}

	public String getTunnelIpv6Address() {
		return "fc00::1";
	}

	public int getTunnelIpv6Prefix() {
		return 128;
	}

	public int getTaskStackSize() {
		return 81920;
	}

	// UDP配置方法
	public String getUdpAddr() {
		return prefs.getString(UDP_ADDR, "");
	}

	public void setUdpAddr(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(UDP_ADDR, addr);
		editor.commit();
	}

	public String getUdpPort() {
		return prefs.getString(UDP_PORT, "");
	}

	public void setUdpPort(String port) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(UDP_PORT, port);
		editor.commit();
	}

	public String getUdpUser() {
		return prefs.getString(UDP_USER, "");
	}

	public void setUdpUser(String user) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(UDP_USER, user);
		editor.commit();
	}

	public String getUdpPass() {
		return prefs.getString(UDP_PASS, "");
	}

	public void setUdpPass(String pass) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(UDP_PASS, pass);
		editor.commit();
	}

	public String getUdpRelayMode() {
		return prefs.getString(UDP_RELAY_MODE, "tcp");
	}

	public void setUdpRelayMode(String mode) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(UDP_RELAY_MODE, mode);
		editor.commit();
	}

	// 功能开关配置方法
	public boolean isChnroutesEnabled() {
		return prefs.getBoolean(CHNROUTES_ENABLED, true);
	}

	public void setChnroutesEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(CHNROUTES_ENABLED, enabled);
		editor.commit();
	}

	public boolean isAclEnabled() {
		return prefs.getBoolean(ACL_ENABLED, false);
	}

	public void setAclEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(ACL_ENABLED, enabled);
		editor.commit();
	}

	public boolean isSmartProxyEnabled() {
		return prefs.getBoolean(SMART_PROXY_ENABLED, true);
	}

	public void setSmartProxyEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(SMART_PROXY_ENABLED, enabled);
		editor.commit();
	}

	public String getSmartProxyTimeout() {
		return prefs.getString(SMART_PROXY_TIMEOUT, "2000");
	}

	public void setSmartProxyTimeout(String timeout) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SMART_PROXY_TIMEOUT, timeout);
		editor.commit();
	}

	public String getSmartProxyBlockExpiry() {
		return prefs.getString(SMART_PROXY_BLOCK_EXPIRY, "360");
	}

	public void setSmartProxyBlockExpiry(String blockExpiry) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SMART_PROXY_BLOCK_EXPIRY, blockExpiry);
		editor.commit();
	}
}
