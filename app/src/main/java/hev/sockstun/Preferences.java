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
	public static final String SOCKS_UDP_ADDR = "SocksUdpAddr";
	public static final String SOCKS_UDP_PORT = "SocksUdpPort";
	public static final String SOCKS_UDP_USER = "SocksUdpUser";
	public static final String SOCKS_UDP_PASS = "SocksUdpPass";
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
	public static final String TASK_STACK_SIZE = "TaskStackSize";
	public static final String TCP_BUFFER_SIZE = "TcpBufferSize";
	public static final String UDP_RECV_BUFFER_SIZE = "UdpRecvBufferSize";
	public static final String UDP_COPY_BUFFER_NUMS = "UdpCopyBufferNums";
	public static final String CONNECT_TIMEOUT = "ConnectTimeout";
	public static final String TCP_READ_WRITE_TIMEOUT = "TcpReadWriteTimeout";
	public static final String UDP_READ_WRITE_TIMEOUT = "UdpReadWriteTimeout";
	public static final String MAX_SESSION_COUNT = "MaxSessionCount";
	public static final String PID_FILE = "PidFile";
	public static final String LIMIT_NOFILE = "LimitNofile";

	private SharedPreferences prefs;

	public Preferences(Context context) {
		prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
	}

	public String getSocksAddress() {
		return prefs.getString(SOCKS_ADDR, "127.0.0.1");
	}

	public void setSocksAddress(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_ADDR, addr);
		editor.commit();
	}

	public String getSocksUdpAddress() {
		return prefs.getString(SOCKS_UDP_ADDR, "");
	}

	public void setSocksUdpAddress(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_UDP_ADDR, addr);
		editor.commit();
	}

	public int getSocksPort() {
		return prefs.getInt(SOCKS_PORT, 1080);
	}

	public void setSocksPort(int port) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(SOCKS_PORT, port);
		editor.commit();
	}

	public String getSocksUsername() {
		return prefs.getString(SOCKS_USER, "");
	}

	public void setSocksUsername(String user) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_USER, user);
		editor.commit();
	}

	public String getSocksPassword() {
		return prefs.getString(SOCKS_PASS, "");
	}

	public void setSocksPassword(String pass) {
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
		return prefs.getInt(TASK_STACK_SIZE, 81920);
	}

	public void setTaskStackSize(int size) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(TASK_STACK_SIZE, size);
		editor.commit();
	}

	public int getTcpBufferSize() {
		return prefs.getInt(TCP_BUFFER_SIZE, 65536);
	}

	public void setTcpBufferSize(int size) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(TCP_BUFFER_SIZE, size);
		editor.commit();
	}

	public int getUdpRecvBufferSize() {
		return prefs.getInt(UDP_RECV_BUFFER_SIZE, 524288);
	}

	public void setUdpRecvBufferSize(int size) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(UDP_RECV_BUFFER_SIZE, size);
		editor.commit();
	}

	public int getUdpCopyBufferNums() {
		return prefs.getInt(UDP_COPY_BUFFER_NUMS, 10);
	}

	public void setUdpCopyBufferNums(int nums) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(UDP_COPY_BUFFER_NUMS, nums);
		editor.commit();
	}

	public int getConnectTimeout() {
		return prefs.getInt(CONNECT_TIMEOUT, 10000);
	}

	public void setConnectTimeout(int timeout) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(CONNECT_TIMEOUT, timeout);
		editor.commit();
	}

	public int getTcpReadWriteTimeout() {
		return prefs.getInt(TCP_READ_WRITE_TIMEOUT, 300000);
	}

	public void setTcpReadWriteTimeout(int timeout) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(TCP_READ_WRITE_TIMEOUT, timeout);
		editor.commit();
	}

	public int getUdpReadWriteTimeout() {
		return prefs.getInt(UDP_READ_WRITE_TIMEOUT, 60000);
	}

	public void setUdpReadWriteTimeout(int timeout) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(UDP_READ_WRITE_TIMEOUT, timeout);
		editor.commit();
	}

	public int getSocksUdpPort() {
		return prefs.getInt(SOCKS_UDP_PORT, 0);
	}

	public void setSocksUdpPort(int port) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(SOCKS_UDP_PORT, port);
		editor.commit();
	}

	public String getSocksUdpUsername() {
		return prefs.getString(SOCKS_UDP_USER, "");
	}

	public void setSocksUdpUsername(String user) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_UDP_USER, user);
		editor.commit();
	}

	public String getSocksUdpPassword() {
		return prefs.getString(SOCKS_UDP_PASS, "");
	}

	public void setSocksUdpPassword(String pass) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SOCKS_UDP_PASS, pass);
		editor.commit();
	}

	public int getMaxSessionCount() {
		return prefs.getInt(MAX_SESSION_COUNT, 0);
	}

	public void setMaxSessionCount(int count) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(MAX_SESSION_COUNT, count);
		editor.commit();
	}

	public String getPidFile() {
		return prefs.getString(PID_FILE, "");
	}

	public void setPidFile(String path) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PID_FILE, path);
		editor.commit();
	}

	public int getLimitNofile() {
		return prefs.getInt(LIMIT_NOFILE, 65535);
	}

	public void setLimitNofile(int limit) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(LIMIT_NOFILE, limit);
		editor.commit();
	}
}
