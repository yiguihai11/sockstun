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

import androidx.core.app.NotificationCompat;

public class TProxyService extends VpnService {
	private static native void TProxyStartService(String config_path, int fd);
	private static native void TProxyStopService();
	private static native long[] TProxyGetStats();
	private Thread nativeThread;

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
		String channelName = "socks5";
		initNotificationChannel(channelName);
		createNotification(channelName);
		TProxyStartService(tproxy_file.getAbsolutePath(), tunFd.getFd());
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
