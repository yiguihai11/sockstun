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
			LogActivity.i(this, "TProxyService", "onStartCommand: ACTION_DISCONNECT received, calling stopService()");
			stopService();
			return START_NOT_STICKY;
		}
		LogActivity.i(this, "TProxyService", "onStartCommand: calling startService()");
		startService();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onRevoke() {
		LogActivity.w(this, "TProxyService", "onRevoke: VPN permission revoked by system, calling stopService()");
		stopService();
		super.onRevoke();
	}

	public void startService() {
		LogActivity.i(this, "TProxyService", "startService() called");
		if (tunFd != null) {
			LogActivity.d(this, "TProxyService", "startService: tunFd already exists, returning");
		  return;
		}

		Preferences prefs = new Preferences(this);

		/* VPN */
		String session = new String();
		VpnService.Builder builder = new VpnService.Builder();
		builder.setBlocking(false);
		builder.setMtu(prefs.getTunnelMtu());
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
			builder.addDnsServer(prefs.getMappedDns());
		}
		boolean disallowSelf = true;
		if (prefs.getGlobal()) {
			session += "/Global";
		} else {
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
			LogActivity.e(this, "TProxyService", "startService: builder.establish() returned null, stopping");
			stopSelf();
			return;
		}
		LogActivity.i(this, "TProxyService", "startService: VPN established successfully, fd=" + tunFd.getFd());

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
			LogActivity.e(this, "TProxyService", "startService: failed to write config: " + e.getMessage());
			return;
		}
		// Start native process in monitoring thread
		LogActivity.i(this, "TProxyService", "startService: starting native thread with config=" + tproxy_file.getAbsolutePath());
		nativeThread = new Thread(new Runnable() {
			@Override
			public void run() {
				LogActivity.i(TProxyService.this, "TProxyService", "nativeThread: calling TProxyStartService with fd=" + tunFd.getFd());
				// This blocks until native process exits
				TProxyStartService(tproxy_file.getAbsolutePath(), tunFd.getFd());

				// Native process exited, stop VPN
				LogActivity.w(TProxyService.this, "TProxyService", "nativeThread: TProxyStartService returned, calling stopService()");
				if (tunFd != null) {
					stopService();
				}
			}
		});
		nativeThread.start();
		LogActivity.i(this, "TProxyService", "startService: nativeThread started");

		prefs.setEnable(true);

		String channelName = "socks5";
		initNotificationChannel(channelName);
		createNotification(channelName);
	}

	public void stopService() {
		LogActivity.i(this, "TProxyService", "stopService() called");
		if (tunFd == null) {
			LogActivity.d(this, "TProxyService", "stopService: tunFd is null, returning");
			return;
		}

		// Interrupt monitoring thread if still running
		if (nativeThread != null && nativeThread.isAlive()) {
			LogActivity.d(this, "TProxyService", "stopService: interrupting nativeThread");
			nativeThread.interrupt();
		}

		stopForeground(true);

		/* TProxy */
		LogActivity.i(this, "TProxyService", "stopService: calling TProxyStopService()");
		TProxyStopService();

		/* VPN */
		try {
			LogActivity.d(this, "TProxyService", "stopService: closing tunFd");
			tunFd.close();
		} catch (IOException e) {
			LogActivity.e(this, "TProxyService", "stopService: error closing tunFd: " + e.getMessage());
		}
		tunFd = null;
		LogActivity.i(this, "TProxyService", "stopService: tunFd set to null");

		// Update enable state
		Preferences prefs = new Preferences(this);
		prefs.setEnable(false);
		LogActivity.d(this, "TProxyService", "stopService: prefs.setEnable(false) called");

		// Send broadcast to notify MainActivity
		Intent intent = new Intent("hev.sockstun.VPN_STOPPED");
		intent.setPackage(getPackageName());
		sendBroadcast(intent);
		LogActivity.d(this, "TProxyService", "stopService: VPN_STOPPED broadcast sent");

		LogActivity.i(this, "TProxyService", "stopService: calling stopSelf()");
		stopSelf();
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
