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
	public static final String MAPDNS_ADDRESS = "MapdnsAddress";
	public static final String MAPDNS_ADDRESS6 = "MapdnsAddress6";
	public static final String MAPDNS_PORT = "MapdnsPort";
	public static final String MAPDNS_NETWORK = "MapdnsNetwork";
	public static final String MAPDNS_NETMASK = "MapdnsNetmask";
	public static final String MAPDNS_NETWORK6 = "MapdnsNetwork6";
	public static final String MAPDNS_PREFIXLEN = "MapdnsPrefixlen";
	public static final String MAPDNS_CACHE_SIZE = "MapdnsCacheSize";
	public static final String IPV4 = "Ipv4";
	public static final String IPV6 = "Ipv6";
	public static final String GLOBAL = "Global";
	public static final String UDP_IN_TCP = "UdpInTcp";
	public static final String REMOTE_DNS = "RemoteDNS";
	public static final String APPS = "Apps";
	public static final String ENABLE = "Enable";
	public static final String TASK_STACK_SIZE = "TaskStackSize";
	public static final String LOG_LEVEL = "LogLevel";
	public static final String TCP_BUFFER_SIZE = "TcpBufferSize";
	public static final String UDP_RECV_BUFFER_SIZE = "UdpRecvBufferSize";
	public static final String UDP_COPY_BUFFER_NUMS = "UdpCopyBufferNums";
	public static final String CONNECT_TIMEOUT = "ConnectTimeout";
	public static final String TCP_READ_WRITE_TIMEOUT = "TcpReadWriteTimeout";
	public static final String UDP_READ_WRITE_TIMEOUT = "UdpReadWriteTimeout";
	public static final String MAX_SESSION_COUNT = "MaxSessionCount";
	public static final String PID_FILE = "PidFile";
	public static final String LIMIT_NOFILE = "LimitNofile";
	public static final String TUNNEL_MTU = "TunnelMtu";
	public static final String TUNNEL_NAME = "TunnelName";
	public static final String TUNNEL_MULTI_QUEUE = "TunnelMultiQueue";
	public static final String TUNNEL_IPV4 = "TunnelIpv4";
	public static final String TUNNEL_IPV6 = "TunnelIpv6";
	public static final String TUNNEL_POST_UP_SCRIPT = "TunnelPostUpScript";
	public static final String TUNNEL_PRE_DOWN_SCRIPT = "TunnelPreDownScript";
	public static final String CHNROUTES_ENABLED = "ChnroutesEnabled";
	public static final String ACL_ENABLED = "AclEnabled";
	public static final String DNS_SPLIT_TUNNEL_ENABLED = "DnsSplitTunnelEnabled";
	public static final String DNS_FOREIGN_SERVERS = "DnsForeignServers";
	public static final String DNS_FORWARDER_ENABLED = "DnsForwarderEnabled";
	public static final String DNS_VIRTUAL_IP4 = "DnsVirtualIp4";
	public static final String DNS_VIRTUAL_IP6 = "DnsVirtualIp6";
	public static final String DNS_TARGET_IP4 = "DnsTargetIp4";
	public static final String DNS_TARGET_IP6 = "DnsTargetIp6";
	public static final String DNS_LATENCY_OPTIMIZE_ENABLED = "DnsLatencyOptimizeEnabled";
	public static final String DNS_LATENCY_OPTIMIZE_TIMEOUT = "DnsLatencyOptimizeTimeout";
	public static final String SMART_PROXY_ENABLED = "SmartProxyEnabled";
	public static final String SMART_PROXY_TIMEOUT = "SmartProxyTimeout";
	public static final String SMART_PROXY_BLOCKED_IP_EXPIRY = "SmartProxyBlockedIpExpiry";
	public static final String SMART_PROXY_PROBE_PORTS = "SmartProxyProbePorts";
	public static final String BYPASS_LAN = "BypassLan";

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
		return prefs.getString(MAPDNS_ADDRESS, "198.18.0.2");
	}

	public void setMappedDns(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(MAPDNS_ADDRESS, addr);
		editor.commit();
	}

	public String getMapdnsAddress() {
		return prefs.getString(MAPDNS_ADDRESS, "198.18.0.2");
	}

	public void setMapdnsAddress(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(MAPDNS_ADDRESS, value);
		editor.commit();
	}

	public String getMapdnsAddress6() {
		return prefs.getString(MAPDNS_ADDRESS6, "fd00::1");
	}

	public void setMapdnsAddress6(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(MAPDNS_ADDRESS6, value);
		editor.commit();
	}

	public int getMapdnsPort() {
		return prefs.getInt(MAPDNS_PORT, 53);
	}

	public void setMapdnsPort(int port) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(MAPDNS_PORT, port);
		editor.commit();
	}

	public String getMapdnsNetwork() {
		return prefs.getString(MAPDNS_NETWORK, "240.0.0.0");
	}

	public void setMapdnsNetwork(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(MAPDNS_NETWORK, value);
		editor.commit();
	}

	public String getMapdnsNetmask() {
		return prefs.getString(MAPDNS_NETMASK, "240.0.0.0");
	}

	public void setMapdnsNetmask(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(MAPDNS_NETMASK, value);
		editor.commit();
	}

	public String getMapdnsNetwork6() {
		return prefs.getString(MAPDNS_NETWORK6, "fd00::");
	}

	public void setMapdnsNetwork6(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(MAPDNS_NETWORK6, value);
		editor.commit();
	}

	public int getMapdnsPrefixlen() {
		return prefs.getInt(MAPDNS_PREFIXLEN, 96);
	}

	public void setMapdnsPrefixlen(int value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(MAPDNS_PREFIXLEN, value);
		editor.commit();
	}

	public int getMapdnsCacheSize() {
		return prefs.getInt(MAPDNS_CACHE_SIZE, 10000);
	}

	public void setMapdnsCacheSize(int value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(MAPDNS_CACHE_SIZE, value);
		editor.commit();
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
		return prefs.getInt(TUNNEL_MTU, 8500);
	}

	public void setTunnelMtu(int mtu) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(TUNNEL_MTU, mtu);
		editor.commit();
	}

	public String getTunnelName() {
		return prefs.getString(TUNNEL_NAME, "tun0");
	}

	public void setTunnelName(String name) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(TUNNEL_NAME, name);
		editor.commit();
	}

	public boolean getTunnelMultiQueue() {
		return prefs.getBoolean(TUNNEL_MULTI_QUEUE, false);
	}

	public void setTunnelMultiQueue(boolean enable) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(TUNNEL_MULTI_QUEUE, enable);
		editor.commit();
	}

	public String getTunnelIpv4() {
		return prefs.getString(TUNNEL_IPV4, "198.18.0.1");
	}

	public void setTunnelIpv4(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(TUNNEL_IPV4, addr);
		editor.commit();
	}

	public String getTunnelIpv6() {
		return prefs.getString(TUNNEL_IPV6, "fc00::1");
	}

	public void setTunnelIpv6(String addr) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(TUNNEL_IPV6, addr);
		editor.commit();
	}

	public String getTunnelPostUpScript() {
		return prefs.getString(TUNNEL_POST_UP_SCRIPT, "");
	}

	public void setTunnelPostUpScript(String script) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(TUNNEL_POST_UP_SCRIPT, script);
		editor.commit();
	}

	public String getTunnelPreDownScript() {
		return prefs.getString(TUNNEL_PRE_DOWN_SCRIPT, "");
	}

	public void setTunnelPreDownScript(String script) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(TUNNEL_PRE_DOWN_SCRIPT, script);
		editor.commit();
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

	public String getLogLevel() {
		return prefs.getString(LOG_LEVEL, "debug");
	}

	public void setLogLevel(String level) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(LOG_LEVEL, level);
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

	public boolean getChnroutesEnabled() {
		return prefs.getBoolean(CHNROUTES_ENABLED, false);
	}

	public void setChnroutesEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(CHNROUTES_ENABLED, enabled);
		editor.commit();
	}

	public boolean getAclEnabled() {
		return prefs.getBoolean(ACL_ENABLED, false);
	}

	public void setAclEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(ACL_ENABLED, enabled);
		editor.commit();
	}

	public boolean getDnsSplitTunnelEnabled() {
		return prefs.getBoolean(DNS_SPLIT_TUNNEL_ENABLED, false);
	}

	public void setDnsSplitTunnelEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(DNS_SPLIT_TUNNEL_ENABLED, enabled);
		editor.commit();
	}

	/**
	 * Get foreign DNS servers list as a JSON array string
	 * Default: ["1.1.1.1", "8.8.8.8", "2606:4700:4700::1111", "2001:4860:4860::8888"]
	 */
	public String getDnsForeignServersJson() {
		return prefs.getString(DNS_FOREIGN_SERVERS, "[\"1.1.1.1\",\"8.8.8.8\",\"2606:4700:4700::1111\",\"2001:4860:4860::8888\"]");
	}

	/**
	 * Set foreign DNS servers list as a JSON array string
	 */
	public void setDnsForeignServersJson(String json) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DNS_FOREIGN_SERVERS, json);
		editor.commit();
	}

	/**
	 * Get foreign DNS servers as a List<String>
	 * Default: ["1.1.1.1", "8.8.8.8", "2606:4700:4700::1111", "2001:4860:4860::8888"]
	 */
	public java.util.List<String> getDnsForeignServersList() {
		String json = getDnsForeignServersJson();
		java.util.List<String> result = new java.util.ArrayList<String>();
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(json);
			for (int i = 0; i < jsonArray.length(); i++) {
				String server = jsonArray.optString(i);
				if (!server.isEmpty()) {
					result.add(server);
				}
			}
		} catch (Exception e) {
			// If JSON parsing fails, return default list
			result.add("1.1.1.1");
			result.add("8.8.8.8");
			result.add("2606:4700:4700::1111");
			result.add("2001:4860:4860::8888");
		}
		return result;
	}

	/**
	 * Set foreign DNS servers from a List<String>
	 */
	public void setDnsForeignServersList(java.util.List<String> servers) {
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray();
			for (String server : servers) {
				if (server != null && !server.trim().isEmpty()) {
					jsonArray.put(server.trim());
				}
			}
			setDnsForeignServersJson(jsonArray.toString());
		} catch (Exception e) {
			// If JSON creation fails, save empty array
			setDnsForeignServersJson("[]");
		}
	}

	// DNS Forwarder preferences
	public String getDnsVirtualIp4() {
		return prefs.getString(DNS_VIRTUAL_IP4, "");
	}

	public void setDnsVirtualIp4(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DNS_VIRTUAL_IP4, value);
		editor.commit();
	}

	public String getDnsVirtualIp6() {
		return prefs.getString(DNS_VIRTUAL_IP6, "");
	}

	public void setDnsVirtualIp6(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DNS_VIRTUAL_IP6, value);
		editor.commit();
	}

	public String getDnsTargetIp4() {
		return prefs.getString(DNS_TARGET_IP4, "");
	}

	public void setDnsTargetIp4(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DNS_TARGET_IP4, value);
		editor.commit();
	}

	public String getDnsTargetIp6() {
		return prefs.getString(DNS_TARGET_IP6, "");
	}

	public void setDnsTargetIp6(String value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(DNS_TARGET_IP6, value);
		editor.commit();
	}

	// DNS Forwarder preferences
	public boolean getDnsForwarderEnabled() {
		return prefs.getBoolean(DNS_FORWARDER_ENABLED, false);
	}

	public void setDnsForwarderEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(DNS_FORWARDER_ENABLED, enabled);
		editor.commit();
	}

	// DNS Latency Optimize preferences
	public boolean getDnsLatencyOptimizeEnabled() {
		return prefs.getBoolean(DNS_LATENCY_OPTIMIZE_ENABLED, false);
	}

	public void setDnsLatencyOptimizeEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(DNS_LATENCY_OPTIMIZE_ENABLED, enabled);
		editor.commit();
	}

	public int getDnsLatencyOptimizeTimeout() {
		return prefs.getInt(DNS_LATENCY_OPTIMIZE_TIMEOUT, 3000);
	}

	public void setDnsLatencyOptimizeTimeout(int timeout) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(DNS_LATENCY_OPTIMIZE_TIMEOUT, timeout);
		editor.commit();
	}

	// Smart Proxy preferences
	public boolean getSmartProxyEnabled() {
		return prefs.getBoolean(SMART_PROXY_ENABLED, false);
	}

	public void setSmartProxyEnabled(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(SMART_PROXY_ENABLED, enabled);
		editor.commit();
	}

	public int getSmartProxyTimeout() {
		return prefs.getInt(SMART_PROXY_TIMEOUT, 0);
	}

	public void setSmartProxyTimeout(int timeout) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(SMART_PROXY_TIMEOUT, timeout);
		editor.commit();
	}

	public int getSmartProxyBlockedIpExpiry() {
		return prefs.getInt(SMART_PROXY_BLOCKED_IP_EXPIRY, 0);
	}

	public void setSmartProxyBlockedIpExpiry(int expiry) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(SMART_PROXY_BLOCKED_IP_EXPIRY, expiry);
		editor.commit();
	}

	public String getSmartProxyProbePortsJson() {
		return prefs.getString(SMART_PROXY_PROBE_PORTS, "[80,443]");
	}

	public void setSmartProxyProbePortsJson(String json) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SMART_PROXY_PROBE_PORTS, json);
		editor.commit();
	}

	public java.util.List<Integer> getSmartProxyProbePortsList() {
		String json = getSmartProxyProbePortsJson();
		java.util.List<Integer> result = new java.util.ArrayList<Integer>();
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(json);
			for (int i = 0; i < jsonArray.length(); i++) {
				int port = jsonArray.optInt(i, 0);
				if (port > 0) {
					result.add(port);
				}
			}
		} catch (Exception e) {
			// If JSON parsing fails, return default list
			result.add(80);
			result.add(443);
		}
		return result;
	}

	public void setSmartProxyProbePortsList(java.util.List<Integer> ports) {
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray();
			for (int port : ports) {
				if (port > 0) {
					jsonArray.put(port);
				}
			}
			setSmartProxyProbePortsJson(jsonArray.toString());
		} catch (Exception e) {
			// If JSON creation fails, save empty array
			setSmartProxyProbePortsJson("[]");
		}
	}

	public boolean getBypassLan() {
		return prefs.getBoolean(BYPASS_LAN, false);
	}

	public void setBypassLan(boolean enabled) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(BYPASS_LAN, enabled);
		editor.commit();
	}
}
