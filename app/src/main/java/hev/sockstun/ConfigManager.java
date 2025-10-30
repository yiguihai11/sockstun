/*
 ============================================================================
 Name        : ConfigManager.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Configuration Manager for hev-socks5-tunnel
 ============================================================================
 */

package hev.sockstun;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;

public class ConfigManager {
    private static final String TAG = "ConfigManager";
    private static final String[] CONFIG_FILES = {
        "acl.txt",
        "chnroutes.txt",
        "main.yml"
    };

    private Context context;
    private File configDir;

    public ConfigManager(Context context) {
        this.context = context;
        this.configDir = new File(context.getCacheDir(), "hev-socks5-tunnel-conf");
    }

    /**
     * 初始化配置文件，将assets中的配置文件释放到缓存目录
     */
    public boolean initializeConfigs() {
        try {
            // 确保配置目录存在
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            // 释放配置文件
            for (String configFile : CONFIG_FILES) {
                if (!extractConfigFile(configFile)) {
                    Log.e(TAG, "Failed to extract config file: " + configFile);
                    return false;
                }
            }

            Log.i(TAG, "Configuration files initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing configuration files", e);
            return false;
        }
    }

    /**
     * 从assets中释放单个配置文件
     */
    private boolean extractConfigFile(String fileName) {
        try {
            File targetFile = new File(configDir, fileName);

            // 如果文件已存在，则跳过
            if (targetFile.exists()) {
                Log.d(TAG, "Config file already exists: " + fileName);
                return true;
            }

            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open(fileName);
            OutputStream outputStream = new FileOutputStream(targetFile);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            inputStream.close();
            outputStream.flush();
            outputStream.close();

            Log.d(TAG, "Extracted config file: " + fileName);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error extracting config file: " + fileName, e);
            return false;
        }
    }

    /**
     * 更新main.yml文件中的用户配置项
     */
    public boolean updateMainYaml(Preferences prefs) {
        try {
            File mainYamlFile = new File(configDir, "main.yml");

            // 确保配置文件存在
            if (!mainYamlFile.exists()) {
                Log.e(TAG, "main.yml file not found, please initialize first");
                return false;
            }

            // 读取现有配置文件
            Yaml yaml = new Yaml();
            Map<String, Object> config;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(mainYamlFile)) {
                config = yaml.load(fis);
            }

            if (config == null) {
                config = new LinkedHashMap<>();
            }

            // 更新特定的配置项
            updateConfigStructure(config, prefs);

            // 写入文件
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            Yaml yamlDumper = new Yaml(options);

            try (FileOutputStream fos = new FileOutputStream(mainYamlFile);
                 java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(fos, "UTF-8")) {
                yamlDumper.dump(config, writer);
                writer.flush();
            }

            Log.i(TAG, "Updated main.yml successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error updating main.yml", e);
            return false;
        }
    }

    /**
     * 读取配置文件内容
     */
    private String readConfigFile(String fileName) {
        try {
            File file = new File(configDir, fileName);
            if (!file.exists()) {
                // 尝试从assets读取
                AssetManager assetManager = context.getAssets();
                InputStream inputStream = assetManager.open(fileName);

                StringBuilder content = new StringBuilder();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    content.append(new String(buffer, 0, read));
                }
                inputStream.close();

                return content.toString();
            } else {
                // 从缓存目录读取
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                StringBuilder content = new StringBuilder();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    content.append(new String(buffer, 0, read));
                }
                fis.close();

                return content.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading config file: " + fileName, e);
            return null;
        }
    }

    /**
     * 读取文件内容
     */
    private String readFileContent(File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            StringBuilder content = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                content.append(new String(buffer, 0, read));
            }
            fis.close();
            return content.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + file.getName(), e);
            return null;
        }
    }

    /**
     * 更新配置结构中的特定配置项
     */
    private void updateConfigStructure(Map<String, Object> config, Preferences prefs) {
        // 更新SOCKS5配置
        Map<String, Object> socks5Config = (Map<String, Object>) config.computeIfAbsent("socks5", k -> new LinkedHashMap<>());

        // 更新TCP配置
        Map<String, Object> tcpConfig = (Map<String, Object>) socks5Config.computeIfAbsent("tcp", k -> new LinkedHashMap<>());
        tcpConfig.put("address", prefs.getSocksAddr());
        tcpConfig.put("port", Integer.parseInt(prefs.getSocksPortStr()));
        if (!prefs.getSocksUser().isEmpty()) {
            tcpConfig.put("username", prefs.getSocksUser());
        }
        if (!prefs.getSocksPass().isEmpty()) {
            tcpConfig.put("password", prefs.getSocksPass());
        }

        // 更新UDP配置
        Map<String, Object> udpConfig = (Map<String, Object>) socks5Config.computeIfAbsent("udp", k -> new LinkedHashMap<>());
        String udpAddr = prefs.getUdpAddr();
        String udpPort = prefs.getUdpPort();
        String udpUser = prefs.getUdpUser();
        String udpPass = prefs.getUdpPass();

        if (!udpAddr.isEmpty()) {
            udpConfig.put("address", udpAddr);
        }
        if (!udpPort.isEmpty()) {
            udpConfig.put("port", Integer.parseInt(udpPort));
        }
        if (!udpUser.isEmpty()) {
            udpConfig.put("username", udpUser);
        }
        if (!udpPass.isEmpty()) {
            udpConfig.put("password", udpPass);
        }
        udpConfig.put("udp-relay", prefs.getUdpRelayMode());

        // 更新DNS转发器配置
        Map<String, Object> dnsConfig = (Map<String, Object>) config.computeIfAbsent("dns-forwarder", k -> new LinkedHashMap<>());
        dnsConfig.put("target-ip4", prefs.getDnsIpv4() + ":53");
        dnsConfig.put("target-ip6", "[" + prefs.getDnsIpv6() + "]:53");

        // 更新中国路由配置
        if (prefs.isChnroutesEnabled()) {
            Map<String, Object> chnroutesConfig = (Map<String, Object>) config.computeIfAbsent("chnroutes", k -> new LinkedHashMap<>());
            chnroutesConfig.put("file-path", getChnroutesPath());
        } else {
            config.remove("chnroutes");
        }

        // 更新ACL配置
        if (prefs.isAclEnabled()) {
            Map<String, Object> aclConfig = (Map<String, Object>) config.computeIfAbsent("acl", k -> new LinkedHashMap<>());
            aclConfig.put("file-path", getAclPath());
        } else {
            config.remove("acl");
        }

        // 更新智能代理配置
        if (prefs.isSmartProxyEnabled()) {
            Map<String, Object> smartProxyConfig = (Map<String, Object>) config.computeIfAbsent("smart-proxy", k -> new LinkedHashMap<>());
            try {
                smartProxyConfig.put("timeout-ms", Integer.parseInt(prefs.getSmartProxyTimeout()));
            } catch (NumberFormatException e) {
                smartProxyConfig.put("timeout-ms", 2000); // 默认值
            }
            try {
                smartProxyConfig.put("blocked-ip-expiry-minutes", Integer.parseInt(prefs.getSmartProxyBlockExpiry()));
            } catch (NumberFormatException e) {
                smartProxyConfig.put("blocked-ip-expiry-minutes", 360); // 默认值
            }
        } else {
            config.remove("smart-proxy");
        }
    }

    
    /**
     * 替换配置文件中的占位符 (保留用于首次生成)
     */
    private String replaceConfigPlaceholders(String template, Preferences prefs) {
        Map<String, String> replacements = new HashMap<>();

        // SOCKS5 TCP配置
        replacements.put("address: 64.69.34.166",
            "address: " + prefs.getSocksAddr());
        replacements.put("port: 1080",
            "port: " + prefs.getSocksPort());
        replacements.put("username: 'yiguihai'",
            "username: '" + prefs.getSocksUser() + "'");
        replacements.put("password: 'ygh15177542493'",
            "password: '" + prefs.getSocksPass() + "'");

        // SOCKS5 UDP配置
        String udpAddr = prefs.getUdpAddr();
        String udpPort = prefs.getUdpPort();
        String udpUser = prefs.getUdpUser();
        String udpPass = prefs.getUdpPass();

        if (!udpAddr.isEmpty()) {
            replacements.put("address: 64.69.34.166",
                "address: " + udpAddr);
        }
        if (!udpPort.isEmpty()) {
            replacements.put("port: 1080",
                "port: " + prefs.getSocksPortStr());
        }
        if (!udpUser.isEmpty()) {
            replacements.put("username: 'yiguihai'",
                "username: '" + udpUser + "'");
        }
        if (!udpPass.isEmpty()) {
            replacements.put("password: 'ygh15177542493'",
                "password: '" + udpPass + "'");
        }

        // UDP转发模式
        String udpRelayMode = prefs.getUdpRelayMode();
        replacements.put("udp-relay: 'tcp'",
            "udp-relay: '" + udpRelayMode + "'");

        // DNS配置
        replacements.put("target-ip4: 127.0.0.1:1053",
            "target-ip4: " + prefs.getDnsIpv4() + ":53");
        replacements.put("target-ip6: '[2606:4700:4700::1111]:53'",
            "target-ip6: '[" + prefs.getDnsIpv6() + "]:53'");

        // 功能开关
        replacements.put("file-path: \"conf/chnroutes.txt\"",
            prefs.isChnroutesEnabled() ?
                "file-path: \"" + getChnroutesPath() + "\"" :
                "# file-path: \"conf/chnroutes.txt\"");

        replacements.put("file-path: \"conf/acl.txt\"",
            prefs.isAclEnabled() ?
                "file-path: \"" + getAclPath() + "\"" :
                "# file-path: \"conf/acl.txt\"");

        // 智能代理
        if (prefs.isSmartProxyEnabled()) {
            replacements.put("# timeout-ms: 2000", "timeout-ms: 2000");
            replacements.put("# blocked-ip-expiry-minutes: 360", "blocked-ip-expiry-minutes: 360");
        } else {
            replacements.put("timeout-ms: 2000", "# timeout-ms: 2000");
            replacements.put("blocked-ip-expiry-minutes: 360", "# blocked-ip-expiry-minutes: 360");
        }

        // 应用替换
        String result = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }

    /**
     * 获取配置文件目录路径
     */
    public String getConfigDirPath() {
        return configDir.getAbsolutePath();
    }

    /**
     * 获取main.yml文件路径
     */
    public String getMainYamlPath() {
        return new File(configDir, "main.yml").getAbsolutePath();
    }

    /**
     * 读取main.yml文件内容
     */
    public String getMainYamlContent() {
        try {
            File mainYamlFile = new File(configDir, "main.yml");
            if (!mainYamlFile.exists()) {
                return "配置文件不存在，请先点击保存按钮生成配置文件。";
            }

            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(mainYamlFile));
            StringBuilder content = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            return content.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error reading main.yml", e);
            return "读取配置文件失败：" + e.getMessage();
        }
    }

    /**
     * 获取acl.txt文件路径
     */
    public String getAclPath() {
        return new File(configDir, "acl.txt").getAbsolutePath();
    }

    /**
     * 获取chnroutes.txt文件路径
     */
    public String getChnroutesPath() {
        return new File(configDir, "chnroutes.txt").getAbsolutePath();
    }

    /**
     * 清理配置文件
     */
    public void cleanup() {
        try {
            if (configDir.exists()) {
                File[] files = configDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted config file: " + file.getName());
                        }
                    }
                }
                if (configDir.delete()) {
                    Log.d(TAG, "Deleted config directory");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up configuration files", e);
        }
    }
}