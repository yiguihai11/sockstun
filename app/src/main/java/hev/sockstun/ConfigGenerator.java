/*
 ============================================================================
 Name        : ConfigGenerator.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : YAML Configuration Generator for hev-socks5-tunnel
 ============================================================================
 */

package hev.sockstun;

import java.io.File;

/**
 * Generates YAML configuration for hev-socks5-tunnel.
 * This class handles all configuration file generation logic.
 */
public class ConfigGenerator {

    private final Preferences prefs;
    private final File logFile;
    private final File cacheDir;
    private final StringBuilder config;

    public ConfigGenerator(Preferences prefs, File logFile, File cacheDir) {
        this.prefs = prefs;
        this.logFile = logFile;
        this.cacheDir = cacheDir;
        this.config = new StringBuilder();
    }

    /**
     * Generate the complete YAML configuration.
     * @return YAML configuration string
     */
    public String generate() {
        config.setLength(0);

        appendTunnelSection();
        appendSocks5Section();
        appendDnsSplitTunnelSection();
        appendDnsForwarderSection();
        appendChnroutesSection();
        appendMiscSection();

        return config.toString();
    }

    private void appendTunnelSection() {
        config.append("tunnel:\n");
        config.append("  mtu: ").append(prefs.getTunnelMtu()).append("\n");
    }

    private void appendSocks5Section() {
        config.append("socks5:\n");

        appendTcpConfig();
        appendUdpConfig();
    }

    private void appendTcpConfig() {
        config.append("  tcp:\n");
        config.append("    port: ").append(prefs.getSocksPort()).append("\n");
        config.append("    address: '").append(prefs.getSocksAddress()).append("'");

        appendAuthentication(prefs.getSocksUsername(), prefs.getSocksPassword());
    }

    private void appendUdpConfig() {
        config.append("  udp:\n");

        // UDP address (fallback to TCP address if not set)
        String udpAddr = prefs.getSocksUdpAddress();
        if (udpAddr.isEmpty()) {
            udpAddr = prefs.getSocksAddress();
        }
        config.append("    address: '").append(udpAddr).append("'\n");

        // UDP port (fallback to TCP port if not set)
        int udpPort = prefs.getSocksUdpPort();
        if (udpPort == 0) {
            udpPort = prefs.getSocksPort();
        }
        config.append("    port: ").append(udpPort).append("\n");

        // UDP relay mode
        String udpRelay = prefs.getUdpInTcp() ? "tcp" : "udp";
        config.append("    udp-relay: '").append(udpRelay).append("'");

        // UDP authentication (use TCP credentials if not set)
        String udpUser = prefs.getSocksUdpUsername();
        String udpPass = prefs.getSocksUdpPassword();
        if (udpUser.isEmpty() && udpPass.isEmpty()) {
            udpUser = prefs.getSocksUsername();
            udpPass = prefs.getSocksPassword();
        }
        appendAuthentication(udpUser, udpPass);
    }

    /**
     * Append authentication if both username and password are provided.
     */
    private void appendAuthentication(String username, String password) {
        if (!username.isEmpty() && !password.isEmpty()) {
            config.append("\n");
            config.append("    username: '").append(username).append("'\n");
            config.append("    password: '").append(password).append("'");
        }
        config.append("\n");
    }

    private void appendDnsSplitTunnelSection() {
        config.append("dns-split-tunnel:\n");
        config.append("  split-tunnel: ").append(prefs.getDnsSplitTunnelEnable() ? "true" : "false").append("\n");
        config.append("  foreign-dns:\n");

        // Get DNS servers list (can contain both IPv4 and IPv6)
        java.util.List<String> servers = prefs.getDnsForeignServersList();
        for (String server : servers) {
            if (!server.isEmpty()) {
                config.append("    - \"").append(server).append("\"\n");
            }
        }
    }

    private void appendDnsForwarderSection() {
        // Check if any dns-forwarder options are set
        String virtualIp4 = prefs.getDnsVirtualIp4();
        String virtualIp6 = prefs.getDnsVirtualIp6();
        String targetIp4 = prefs.getDnsTargetIp4();
        String targetIp6 = prefs.getDnsTargetIp6();

        // Only add dns-forwarder section if at least one pair is complete
        boolean hasIp4Pair = !virtualIp4.isEmpty() && !targetIp4.isEmpty();
        boolean hasIp6Pair = !virtualIp6.isEmpty() && !targetIp6.isEmpty();

        if (!hasIp4Pair && !hasIp6Pair) {
            return;
        }

        config.append("dns-forwarder:\n");

        // IPv4 pair - both must be set
        if (hasIp4Pair) {
            config.append("  virtual-ip4: ").append(virtualIp4).append("\n");
            config.append("  target-ip4: ").append(targetIp4).append("\n");
        }

        // IPv6 pair - both must be set
        if (hasIp6Pair) {
            config.append("  virtual-ip6: '").append(virtualIp6).append("'\n");
            config.append("  target-ip6: '").append(targetIp6).append("'\n");
        }
    }

    private void appendChnroutesSection() {
        config.append("chnroutes:\n");
        config.append("  enabled: ").append(prefs.getChnroutesEnabled() ? "true" : "false").append("\n");
        config.append("  file-path: \"").append(new File(cacheDir, "chnroutes.txt").getAbsolutePath()).append("\"\n");
    }

    private void appendMiscSection() {
        config.append("misc:\n");
        config.append("  task-stack-size: ").append(prefs.getTaskStackSize()).append("\n");

        config.append("  tcp-buffer-size: ").append(prefs.getTcpBufferSize()).append("\n");
        config.append("  udp-recv-buffer-size: ").append(prefs.getUdpRecvBufferSize()).append("\n");
        config.append("  udp-copy-buffer-nums: ").append(prefs.getUdpCopyBufferNums()).append("\n");

        int maxSessionCount = prefs.getMaxSessionCount();
        if (maxSessionCount > 0) {
            config.append("  max-session-count: ").append(maxSessionCount).append("\n");
        }

        config.append("  connect-timeout: ").append(prefs.getConnectTimeout()).append("\n");
        config.append("  tcp-read-write-timeout: ").append(prefs.getTcpReadWriteTimeout()).append("\n");
        config.append("  udp-read-write-timeout: ").append(prefs.getUdpReadWriteTimeout()).append("\n");

        config.append("  log-file: '").append(logFile.getAbsolutePath()).append("'\n");
        config.append("  log-level: ").append(prefs.getLogLevel()).append("\n");

        // PID File: Not supported on Android
        // String pidFile = prefs.getPidFile();
        // if (!pidFile.isEmpty()) {
        //     config.append("  pid-file: '").append(pidFile).append("'\n");
        // }

        // Limit Nofile: Limited by Android sandbox
        // int limitNofile = prefs.getLimitNofile();
        // if (limitNofile > 0) {
        //     config.append("  limit-nofile: ").append(limitNofile).append("\n");
        // }
    }
}
