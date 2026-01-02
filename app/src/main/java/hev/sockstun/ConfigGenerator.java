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

        appendMiscSection();
        appendTunnelSection();
        appendSocks5Section();

        if (prefs.getRemoteDns()) {
            appendMapDnsSection();
        }

        return config.toString();
    }

    private void appendMiscSection() {
        config.append("misc:\n");
        config.append("  task-stack-size: ").append(prefs.getTaskStackSize()).append("\n");
        config.append("  log-file: '").append(logFile.getAbsolutePath()).append("'\n");
        config.append("  log-level: debug\n");
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

    private void appendMapDnsSection() {
        config.append("mapdns:\n");
        config.append("  address: ").append(prefs.getMappedDns()).append("\n");
        config.append("  port: 53\n");
        config.append("  network: 240.0.0.0\n");
        config.append("  netmask: 240.0.0.0\n");
        config.append("  cache-size: 10000\n");
    }
}
