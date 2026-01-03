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
    private final StringBuilder config;

    public ConfigGenerator(Preferences prefs, File logFile) {
        this.prefs = prefs;
        this.logFile = logFile;
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
        appendChnroutesSection();
        appendMiscSection();

        return config.toString();
    }

    private void appendTunnelSection() {
        config.append("tunnel:\n");
        config.append("  name: tun0\n");
        config.append("  mtu: ").append(prefs.getTunnelMtu()).append("\n");
        // multi-queue: Android not supported, always false
        config.append("  multi-queue: false\n");
        // ipv4/ipv6: Managed by Android VPN Service
        config.append("  ipv4: ").append(prefs.getTunnelIpv4()).append("\n");
        config.append("  ipv6: '").append(prefs.getTunnelIpv6()).append("'\n");

        // Post up script (optional, Android not supported)
        // String postUpScript = prefs.getTunnelPostUpScript();
        // if (!postUpScript.isEmpty()) {
        //     config.append("  post-up-script: '").append(postUpScript).append("'\n");
        // }

        // Pre down script (optional, Android not supported)
        // String preDownScript = prefs.getTunnelPreDownScript();
        // if (!preDownScript.isEmpty()) {
        //     config.append("  pre-down-script: '").append(preDownScript).append("'\n");
        // }
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
        config.append("  split-tunnel: true\n");
        config.append("  foreign-dns:\n");
        config.append("    - \"1.1.1.1\"\n");
        config.append("    - \"8.8.8.8\"\n");
        config.append("    - \"2606:4700:4700::1111\"\n");
        config.append("    - \"2001:4860:4860::8888\"\n");
    }

    private void appendChnroutesSection() {
        config.append("chnroutes:\n");
        config.append("  enabled: true\n");
        config.append("  file-path: \"conf/chnroutes.txt\"\n");
    }

    private void appendMiscSection() {
        config.append("misc:\n");
        config.append("  task-stack-size: ").append(prefs.getTaskStackSize()).append("\n");

        // Disabled options (not recommended to modify on Android)
        // config.append("  tcp-buffer-size: ").append(prefs.getTcpBufferSize()).append("\n");
        // config.append("  udp-recv-buffer-size: ").append(prefs.getUdpRecvBufferSize()).append("\n");
        // config.append("  udp-copy-buffer-nums: ").append(prefs.getUdpCopyBufferNums()).append("\n");

        // int maxSessionCount = prefs.getMaxSessionCount();
        // if (maxSessionCount > 0) {
        //     config.append("  max-session-count: ").append(maxSessionCount).append("\n");
        // }

        // config.append("  connect-timeout: ").append(prefs.getConnectTimeout()).append("\n");
        // config.append("  tcp-read-write-timeout: ").append(prefs.getTcpReadWriteTimeout()).append("\n");
        // config.append("  udp-read-write-timeout: ").append(prefs.getUdpReadWriteTimeout()).append("\n");

        config.append("  log-file: '").append(logFile.getAbsolutePath()).append("'\n");
        config.append("  log-level: debug\n");

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
