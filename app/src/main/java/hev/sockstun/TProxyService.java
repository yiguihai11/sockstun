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
import android.net.IpPrefix;

import androidx.core.app.NotificationCompat;

public class TProxyService extends VpnService {
	private static native void TProxyStartService(String config_path, int fd);
	private static native void TProxyStopService();
	private static native long[] TProxyGetStats();
	private static native String TProxyGetLogs(int max_lines);

	private Thread nativeThread;

	public static String getLogs(int maxLines) {
		return TProxyGetLogs(maxLines);
	}

	public static final String ACTION_CONNECT = "hev.sockstun.CONNECT";
	public static final String ACTION_DISCONNECT = "hev.sockstun.DISCONNECT";

	static {
		System.loadLibrary("hev-socks5-tunnel");
	}

	private ParcelFileDescriptor tunFd = null;

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
		super.onDestroy();
	}

	@Override
	public void onRevoke() {
		stopService();
		super.onRevoke();
	}

	public void startService() {
		if (tunFd != null)
		  return;

		Preferences prefs = new Preferences(this);

		/* VPN */
		String session = new String();
		VpnService.Builder builder = new VpnService.Builder();
		builder.setBlocking(false);
		builder.setMtu(prefs.getTunnelMtu());

		// Bypass LAN routes (API 33+) - MUST be called before addRoute
		if (Build.VERSION.SDK_INT >= 33 && prefs.getBypassLan()) {
			// IPv4 private/local routes
			if (prefs.getIpv4()) {
				try {
					builder.excludeRoute(new IpPrefix("10.0.0.0/8"));
					builder.excludeRoute(new IpPrefix("100.64.0.0/10"));
					builder.excludeRoute(new IpPrefix("127.0.0.0/8"));
					builder.excludeRoute(new IpPrefix("169.254.0.0/16"));
					builder.excludeRoute(new IpPrefix("172.16.0.0/12"));
					builder.excludeRoute(new IpPrefix("192.168.0.0/16"));
				} catch (Exception e) {
					// Silently ignore errors for excludeRoute
				}
			}
			// IPv6 private/local routes
			if (prefs.getIpv6()) {
				try {
					builder.excludeRoute(new IpPrefix("::1/128"));
					builder.excludeRoute(new IpPrefix("::ffff:0:0/96"));
					builder.excludeRoute(new IpPrefix("fc00::/7"));
					builder.excludeRoute(new IpPrefix("fe80::/10"));
				} catch (Exception e) {
					// Silently ignore errors for excludeRoute
				}
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
			session += "IPv4";
		}
		if (prefs.getIpv6()) {
			String addr = prefs.getTunnelIpv6Address();
			int prefix = prefs.getTunnelIpv6Prefix();
			String dns = prefs.getDnsIpv6();
			builder.addAddress(addr, prefix);
			builder.addRoute("::", 0);
			if (!prefs.getRemoteDns() && !dns.isEmpty())
			  builder.addDnsServer(dns);
			if (!session.isEmpty())
			  session += " + ";
			session += "IPv6";
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
			session += "/Global";
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
			session += "/per-App";
		}
		if (disallowSelf) {
			String selfName = getApplicationContext().getPackageName();
			try {
				builder.addDisallowedApplication(selfName);
			} catch (NameNotFoundException e) {
			}
		}
		builder.setSession(session);
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

		// Start native process directly (blocks until exit)
		TProxyStartService(tproxy_file.getAbsolutePath(), tunFd.getFd());

		prefs.setEnable(true);

		String channelName = "socks5";
		initNotificationChannel(channelName);
		createNotification(channelName);
	}

	public void stopService() {
		if (tunFd == null)
			return;

		stopForeground(true);

		/* TProxy */
		TProxyStopService();

		/* VPN */
		try {
			tunFd.close();
		} catch (IOException e) {
		}
		tunFd = null;

		// Update enable state
		Preferences prefs = new Preferences(this);
		prefs.setEnable(false);

		// Send broadcast to notify MainActivity
		Intent intent = new Intent("hev.sockstun.VPN_STOPPED");
		intent.setPackage(getPackageName());
		sendBroadcast(intent);

		System.exit(0);
	}

	private void createNotification(String channelName) {
		Intent i = new Intent(this, MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
		NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelName);
		Notification notify = notification
				.setContentTitle(getString(R.string.app_name))
				.setSmallIcon(android.R.drawable.sym_def_app_icon)
				.setContentIntent(pi)
				.build();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(1, notify);
		} else {
			startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		}
	}

	// create NotificationChannel
	private void initNotificationChannel(String channelName) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			CharSequence name = getString(R.string.app_name);
			NotificationChannel channel = new NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_DEFAULT);
			notificationManager.createNotificationChannel(channel);
		}
	}
}
