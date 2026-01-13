/*
 ============================================================================
 Name        : TProxyService.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : TProxy Service
 ============================================================================
 */

package hev.sockstun;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

public class TProxyService extends VpnService {
	private static native void TProxyStartService(String config_path, int fd);
	private static native void TProxyStopService();
	private static native long[] TProxyGetStats();

	public static final String ACTION_CONNECT = "hev.sockstun.CONNECT";
	public static final String ACTION_DISCONNECT = "hev.sockstun.DISCONNECT";
	private static final int STATS_UPDATE_INTERVAL_MS = 2000;

	static {
		System.loadLibrary("hev-socks5-tunnel");
	}

	private ParcelFileDescriptor tunFd = null;
	private String channelName = "socks5";
	private volatile boolean isStopping = false;  // Prevent race conditions

	// Traffic stats
	private Handler statsHandler;
	private Runnable statsRunnable;
	private long lastTxPackets = 0;
	private long lastTxBytes = 0;
	private long lastRxPackets = 0;
	private long lastRxBytes = 0;
	private long lastTime = 0;
	private long totalTxBytes = 0;
	private long totalRxBytes = 0;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
			stopService();
			return START_NOT_STICKY;
		}
		startService();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (statsHandler != null && statsRunnable != null) {
			statsHandler.removeCallbacks(statsRunnable);
		}
		super.onDestroy();
	}

	@Override
	public void onRevoke() {
		stopService();
		super.onRevoke();
	}

	public void startService() {
		if (tunFd != null || isStopping)
		  return;

		// Reset stopping flag
		isStopping = false;

		// Reset traffic stats
		lastTxPackets = 0;
		lastTxBytes = 0;
		lastRxPackets = 0;
		lastRxBytes = 0;
		lastTime = 0;
		totalTxBytes = 0;
		totalRxBytes = 0;

		Preferences prefs = new Preferences(this);

		/* VPN */
		VpnService.Builder builder = new VpnService.Builder();
		builder.setBlocking(false);
		builder.setMtu(prefs.getTunnelMtu());

		// Bypass LAN routes (API 33+) - MUST be called before addRoute
		// Use reflection for IpPrefix class (added in API 28) to maintain minSdk 24
		if (Build.VERSION.SDK_INT >= 33 && prefs.getBypassLan()) {
			try {
				Class<?> ipPrefixClass = Class.forName("android.net.IpPrefix");
				java.lang.reflect.Method excludeRouteMethod = VpnService.Builder.class.getMethod("excludeRoute", ipPrefixClass);

				// IPv4 private/local routes
				if (prefs.getIpv4()) {
					String[] ipv4Routes = {"10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
					                      "169.254.0.0/16", "172.16.0.0/12", "192.168.0.0/16"};
					for (String route : ipv4Routes) {
						try {
							Object ipPrefix = ipPrefixClass.getConstructor(String.class).newInstance(route);
							excludeRouteMethod.invoke(builder, ipPrefix);
						} catch (Exception e) {
							// Silently ignore errors for individual routes
						}
					}
				}
				// IPv6 private/local routes
				if (prefs.getIpv6()) {
					String[] ipv6Routes = {"::1/128", "::ffff:0:0/96", "fc00::/7", "fe80::/10"};
					for (String route : ipv6Routes) {
						try {
							Object ipPrefix = ipPrefixClass.getConstructor(String.class).newInstance(route);
							excludeRouteMethod.invoke(builder, ipPrefix);
						} catch (Exception e) {
							// Silently ignore errors for individual routes
						}
					}
				}
			} catch (Exception e) {
				// Silently ignore reflection errors (class not found, method not found, etc.)
			}
		}

		if (prefs.getIpv4()) {
			String addr = prefs.getTunnelIpv4Address();
			int prefix = prefs.getTunnelIpv4Prefix();
			String dns = prefs.getDnsIpv4();
			builder.addAddress(addr, prefix);
			builder.addRoute("0.0.0.0", 0);
			if (!prefs.getRemoteDns() && !dns.isEmpty())
			  builder.addDnsServer(dns);
		}
		if (prefs.getIpv6()) {
			String addr = prefs.getTunnelIpv6Address();
			int prefix = prefs.getTunnelIpv6Prefix();
			String dns = prefs.getDnsIpv6();
			builder.addAddress(addr, prefix);
			builder.addRoute("::", 0);
			if (!prefs.getRemoteDns() && !dns.isEmpty())
			  builder.addDnsServer(dns);
		}
		if (prefs.getRemoteDns()) {
			// Add mapped DNS servers based on enabled IP versions
			if (prefs.getIpv4()) {
				builder.addDnsServer(prefs.getMappedDns());
			}
			if (prefs.getIpv6()) {
				String mappedDns6 = prefs.getMapdnsAddress6();
				if (!mappedDns6.isEmpty()) {
					builder.addDnsServer(mappedDns6);
				}
			}
		}

		boolean disallowSelf = true;
		if (prefs.getGlobal()) {
			// In global mode, exclude selected apps (blacklist)
			for (String appName : prefs.getApps()) {
				try {
					builder.addDisallowedApplication(appName);
				} catch (NameNotFoundException e) {
				}
			}
		} else {
			// In per-app mode, only selected apps use VPN (whitelist)
			for (String appName : prefs.getApps()) {
				try {
					builder.addAllowedApplication(appName);
					disallowSelf = false;
				} catch (NameNotFoundException e) {
				}
			}
		}
		if (disallowSelf) {
			String selfName = getApplicationContext().getPackageName();
			try {
				builder.addDisallowedApplication(selfName);
			} catch (NameNotFoundException e) {
			}
		}
		tunFd = builder.establish();
		if (tunFd == null) {
			stopSelf();
			return;
		}

		/* TProxy */
		File log_file = new File(getCacheDir(), "tunnel.log");
		File tproxy_file = new File(getCacheDir(), "tproxy.conf");
		try {
			tproxy_file.createNewFile();
			FileOutputStream fos = new FileOutputStream(tproxy_file, false);

			ConfigGenerator configGen = new ConfigGenerator(prefs, log_file, getCacheDir());
			String tproxy_conf = configGen.generate();

			fos.write(tproxy_conf.getBytes());
			fos.close();
		} catch (IOException e) {
			return;
		}

		prefs.setEnable(true);

		// Create notification BEFORE starting native process
		// Must be done within 5 seconds of service start or system will kill the service
		initNotificationChannel(channelName);
		createNotification(channelName);
		TProxyStartService(tproxy_file.getAbsolutePath(), tunFd.getFd());

		// Start traffic stats update
		startStatsUpdate();
	}
	
	public void stopService() {
		if (tunFd == null || isStopping)
		  return;

		// Set stopping flag to prevent race conditions
		isStopping = true;

		// Stop traffic stats update
		stopStatsUpdate();

		// Immediately remove notification and clear foreground state
		stopForeground(true);

		// Stop native service first - this will signal tunnel to stop
		// The tunnel will stop reading from TUN device
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Wait for tunnel to stop gracefully
				TProxyStopService();

				// NOW close TUN device after tunnel has stopped reading from it
				try {
					if (tunFd != null) {
						tunFd.close();
					}
				} catch (IOException e) {
				}
				tunFd = null;

				// Clear stopping flag and stop the service
				isStopping = false;
				stopSelf();
			}
		}, "TunnelStopThread").start();
	}

	private void createNotification(String channelName) {
		createNotification(channelName, "↑ --  ↓ --", null, null, null, null, true);
	}

	private void createNotification(String channelName, String contentText,
	                                String bigText, String totalTx, String totalRx,
	                                String packetInfo, boolean isFirstTime) {
		Intent i = new Intent(this, MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
		NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelName);

		notification
				.setContentTitle(getString(R.string.app_name))
				.setContentText(contentText)
				.setSmallIcon(android.R.drawable.sym_def_app_icon)
				.setContentIntent(pi)
				.setOngoing(true)
				.setOnlyAlertOnce(true);

		// Add big text style if detailed info is available
		if (bigText != null) {
			NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
			bigTextStyle.bigText(bigText);
			notification.setStyle(bigTextStyle);
		}

		Notification notify = notification.build();

		if (isFirstTime) {
			// First time - use startForeground
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				startForeground(1, notify);
			} else {
				startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
			}
		} else {
			// Update - use NotificationManager (no sound)
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(1, notify);
		}
	}

	// create NotificationChannel
	private void initNotificationChannel(String channelName) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			CharSequence name = getString(R.string.app_name);
			NotificationChannel channel = new NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_LOW);
			channel.setSound(null, null);
			channel.enableVibration(false);
			notificationManager.createNotificationChannel(channel);
		}
	}

	private void startStatsUpdate() {
		statsHandler = new Handler(Looper.getMainLooper());
		statsRunnable = new Runnable() {
			@Override
			public void run() {
				updateTrafficStats();
				statsHandler.postDelayed(this, STATS_UPDATE_INTERVAL_MS);
			}
		};
		statsHandler.post(statsRunnable);
	}

	private void stopStatsUpdate() {
		if (statsHandler != null && statsRunnable != null) {
			statsHandler.removeCallbacks(statsRunnable);
		}
	}

	private void updateTrafficStats() {
		long[] stats = TProxyGetStats();
		if (stats == null || stats.length < 4) {
			return;
		}

		long curTxPackets = stats[0];
		long curTxBytes = stats[1];
		long curRxPackets = stats[2];
		long curRxBytes = stats[3];
		long currentTime = System.currentTimeMillis();

		String contentText;
		String bigText = null;
		String totalTx = null;
		String totalRx = null;
		String packetInfo = null;

		if (lastTime > 0) {
			long timeDelta = (currentTime - lastTime) / 1000;
			if (timeDelta > 0) {
				long txSpeed = (curTxBytes - lastTxBytes) / timeDelta;
				long rxSpeed = (curRxBytes - lastRxBytes) / timeDelta;
				long txPacketsRate = (curTxPackets - lastTxPackets) / timeDelta;
				long rxPacketsRate = (curRxPackets - lastRxPackets) / timeDelta;

				contentText = "↑ " + formatSpeed(txSpeed) + "  ↓ " + formatSpeed(rxSpeed);

				// Build big text with detailed info
				totalTx = formatBytes(curTxBytes);
				totalRx = formatBytes(curRxBytes);
				packetInfo = curTxPackets + " / " + curRxPackets;

				bigText = getString(R.string.notif_upload_speed) + " " + formatSpeed(txSpeed) + "\n" +
				          getString(R.string.notif_sent_data) + " " + totalTx + "\n" +
				          getString(R.string.notif_sent_packets) + " " + curTxPackets + "\n\n" +
				          getString(R.string.notif_download_speed) + " " + formatSpeed(rxSpeed) + "\n" +
				          getString(R.string.notif_received_data) + " " + totalRx + "\n" +
				          getString(R.string.notif_received_packets) + " " + curRxPackets;
			} else {
				contentText = "↑ --  ↓ --";
			}
		} else {
			contentText = "↑ --  ↓ --";
		}

		lastTxPackets = curTxPackets;
		lastTxBytes = curTxBytes;
		lastRxPackets = curRxPackets;
		lastRxBytes = curRxBytes;
		lastTime = currentTime;

		// Update notification with big text
		createNotification(channelName, contentText, bigText, totalTx, totalRx, packetInfo, false);
	}

	private String formatSpeed(long bytesPerSecond) {
		if (bytesPerSecond < 1024) {
			return bytesPerSecond + " B/s";
		} else if (bytesPerSecond < 1024 * 1024) {
			return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
		} else {
			return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
		}
	}

	private String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		} else if (bytes < 1024 * 1024 * 1024) {
			return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
		} else {
			return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
		}
	}
}
