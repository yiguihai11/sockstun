/*
 ============================================================================
 Name        : MainActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Main Activity
 ============================================================================
 */

package hev.sockstun;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.net.VpnService;
import android.util.Log;

public class MainActivity extends Activity implements View.OnClickListener {
	private static final String TAG = "MainActivity";

	private Preferences prefs;
	private ConfigManager configManager;
	private Handler logHandler;
	private Runnable logRunnable;

	// TCP SOCKS5
	private EditText edittext_socks_addr;
	private EditText edittext_socks_port;
	private EditText edittext_socks_user;
	private EditText edittext_socks_pass;

	// UDP SOCKS5
	private EditText edittext_udp_addr;
	private EditText edittext_udp_port;
	private EditText edittext_udp_user;
	private EditText edittext_udp_pass;
	private Spinner spinner_udp_relay_mode;

	// DNS
	private EditText edittext_dns_ipv4;
	private EditText edittext_dns_ipv6;

	// 功能开关
	private CheckBox checkbox_chnroutes_enabled;
	private CheckBox checkbox_acl_enabled;
	private CheckBox checkbox_smart_proxy_enabled;
	private CheckBox checkbox_remote_dns;
	private CheckBox checkbox_global;
	private CheckBox checkbox_ipv4;
	private CheckBox checkbox_ipv6;

	// 智能代理配置
	private EditText edittext_smart_proxy_timeout;
	private EditText edittext_smart_proxy_block_expiry;

	// 按钮
	private Button button_apps;
	private Button button_save;
	private Button button_control;

	// 日志显示
	private ScrollView log_scrollview;
	private TextView log_textview;
	private Button button_log_refresh;
	private Button button_log_clear;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = new Preferences(this);
		configManager = new ConfigManager(this);

		// 初始化配置文件
		if (!configManager.initializeConfigs()) {
			Toast.makeText(this, "配置文件初始化失败", Toast.LENGTH_LONG).show();
			Log.e(TAG, "Failed to initialize config files");
		}

		setContentView(R.layout.main);

		// 初始化界面控件
		initializeViews();

		// 设置监听器
		setupListeners();

		// 初始化日志刷新Handler
		logHandler = new Handler();
		logRunnable = new Runnable() {
			@Override
			public void run() {
				refreshLog();
				// 每2秒自动刷新一次日志
				logHandler.postDelayed(this, 2000);
			}
		};

		// 更新界面状态
		updateUI();

		/* Request VPN permission */
		Intent intent = VpnService.prepare(MainActivity.this);
		if (intent != null)
		  startActivityForResult(intent, 0);
		else
		  onActivityResult(0, RESULT_OK, null);
	}

	private void initializeViews() {
		// TCP SOCKS5
		edittext_socks_addr = (EditText) findViewById(R.id.socks_addr);
		edittext_socks_port = (EditText) findViewById(R.id.socks_port);
		edittext_socks_user = (EditText) findViewById(R.id.socks_user);
		edittext_socks_pass = (EditText) findViewById(R.id.socks_pass);

		// UDP SOCKS5
		edittext_udp_addr = (EditText) findViewById(R.id.udp_addr);
		edittext_udp_port = (EditText) findViewById(R.id.udp_port);
		edittext_udp_user = (EditText) findViewById(R.id.udp_user);
		edittext_udp_pass = (EditText) findViewById(R.id.udp_pass);
		spinner_udp_relay_mode = (Spinner) findViewById(R.id.udp_relay_mode);

		// DNS
		edittext_dns_ipv4 = (EditText) findViewById(R.id.dns_ipv4);
		edittext_dns_ipv6 = (EditText) findViewById(R.id.dns_ipv6);

		// 功能开关
		checkbox_chnroutes_enabled = (CheckBox) findViewById(R.id.chnroutes_enabled);
		checkbox_acl_enabled = (CheckBox) findViewById(R.id.acl_enabled);
		checkbox_smart_proxy_enabled = (CheckBox) findViewById(R.id.smart_proxy_enabled);
		checkbox_remote_dns = (CheckBox) findViewById(R.id.remote_dns);
		checkbox_global = (CheckBox) findViewById(R.id.global);
		checkbox_ipv4 = (CheckBox) findViewById(R.id.ipv4);
		checkbox_ipv6 = (CheckBox) findViewById(R.id.ipv6);

		// 智能代理配置
		edittext_smart_proxy_timeout = (EditText) findViewById(R.id.smart_proxy_timeout);
		edittext_smart_proxy_block_expiry = (EditText) findViewById(R.id.smart_proxy_block_expiry);

		// 按钮
		button_apps = (Button) findViewById(R.id.apps);
		button_save = (Button) findViewById(R.id.save);
		button_control = (Button) findViewById(R.id.control);

		// 日志显示
		log_scrollview = (ScrollView) findViewById(R.id.log_scrollview);
		log_textview = (TextView) findViewById(R.id.log_textview);
		button_log_refresh = (Button) findViewById(R.id.log_refresh);
		button_log_clear = (Button) findViewById(R.id.log_clear);
	}

	private void setupListeners() {
		button_apps.setOnClickListener(this);
		button_save.setOnClickListener(this);
		button_control.setOnClickListener(this);
		checkbox_remote_dns.setOnClickListener(this);
		checkbox_global.setOnClickListener(this);
		checkbox_smart_proxy_enabled.setOnClickListener(this);
		button_log_refresh.setOnClickListener(this);
		button_log_clear.setOnClickListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// 启动日志自动刷新
		if (logHandler != null && logRunnable != null) {
			logHandler.post(logRunnable);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		// 暂停日志自动刷新
		if (logHandler != null && logRunnable != null) {
			logHandler.removeCallbacks(logRunnable);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// 停止日志自动刷新
		if (logHandler != null && logRunnable != null) {
			logHandler.removeCallbacks(logRunnable);
		}
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		if ((result == RESULT_OK) && prefs.getEnable()) {
			Intent intent = new Intent(this, TProxyService.class);
			startService(intent.setAction(TProxyService.ACTION_CONNECT));
		}
	}

	@Override
	public void onClick(View view) {
		if (view == checkbox_global || view == checkbox_remote_dns || view == checkbox_smart_proxy_enabled) {
			savePrefs();
			updateUI();
		} else if (view == button_apps) {
			startActivity(new Intent(this, AppListActivity.class));
		} else if (view == button_save) {
			savePrefs();
			Context context = getApplicationContext();
			Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
		} else if (view == button_control) {
			boolean isEnable = prefs.getEnable();
			prefs.setEnable(!isEnable);
			savePrefs();
			updateUI();
			Intent intent = new Intent(this, TProxyService.class);
			if (isEnable)
			  startService(intent.setAction(TProxyService.ACTION_DISCONNECT));
			else
			  startService(intent.setAction(TProxyService.ACTION_CONNECT));
		} else if (view == button_log_refresh) {
			refreshLog();
		} else if (view == button_log_clear) {
			log_textview.setText("日志已清空。\n");
		}
	}

	private void updateUI() {
		// TCP SOCKS5
		edittext_socks_addr.setText(prefs.getSocksAddr());
		edittext_socks_port.setText(prefs.getSocksPortStr());
		edittext_socks_user.setText(prefs.getSocksUser());
		edittext_socks_pass.setText(prefs.getSocksPass());

		// UDP SOCKS5
		edittext_udp_addr.setText(prefs.getUdpAddr());
		edittext_udp_port.setText(prefs.getUdpPort());
		edittext_udp_user.setText(prefs.getUdpUser());
		edittext_udp_pass.setText(prefs.getUdpPass());

		// 设置UDP转发模式
		String udpRelayMode = prefs.getUdpRelayMode();
		if ("tcp".equals(udpRelayMode)) {
			spinner_udp_relay_mode.setSelection(0);
		} else {
			spinner_udp_relay_mode.setSelection(1);
		}

		// DNS
		edittext_dns_ipv4.setText(prefs.getDnsIpv4());
		edittext_dns_ipv6.setText(prefs.getDnsIpv6());

		// 功能开关
		checkbox_chnroutes_enabled.setChecked(prefs.isChnroutesEnabled());
		checkbox_acl_enabled.setChecked(prefs.isAclEnabled());
		checkbox_smart_proxy_enabled.setChecked(prefs.isSmartProxyEnabled());
		checkbox_remote_dns.setChecked(prefs.getRemoteDns());
		checkbox_global.setChecked(prefs.getGlobal());
		checkbox_ipv4.setChecked(prefs.getIpv4());
		checkbox_ipv6.setChecked(prefs.getIpv6());

		// 智能代理配置
		edittext_smart_proxy_timeout.setText(prefs.getSmartProxyTimeout());
		edittext_smart_proxy_block_expiry.setText(prefs.getSmartProxyBlockExpiry());

		boolean editable = !prefs.getEnable();

		// 设置控件可编辑状态
		edittext_socks_addr.setEnabled(editable);
		edittext_socks_port.setEnabled(editable);
		edittext_socks_user.setEnabled(editable);
		edittext_socks_pass.setEnabled(editable);
		edittext_udp_addr.setEnabled(editable);
		edittext_udp_port.setEnabled(editable);
		edittext_udp_user.setEnabled(editable);
		edittext_udp_pass.setEnabled(editable);
		spinner_udp_relay_mode.setEnabled(editable);
		edittext_dns_ipv4.setEnabled(editable && !prefs.getRemoteDns());
		edittext_dns_ipv6.setEnabled(editable && !prefs.getRemoteDns());
		checkbox_chnroutes_enabled.setEnabled(editable);
		checkbox_acl_enabled.setEnabled(editable);
		checkbox_smart_proxy_enabled.setEnabled(editable);
		checkbox_remote_dns.setEnabled(editable);
		checkbox_global.setEnabled(editable);
		checkbox_ipv4.setEnabled(editable);
		checkbox_ipv6.setEnabled(editable);

		// 智能代理输入框状态：只有启用智能代理时才可编辑
		edittext_smart_proxy_timeout.setEnabled(editable && prefs.isSmartProxyEnabled());
		edittext_smart_proxy_block_expiry.setEnabled(editable && prefs.isSmartProxyEnabled());
		button_apps.setEnabled(editable && !prefs.getGlobal());
		button_save.setEnabled(editable);

		if (editable)
			button_control.setText(R.string.control_enable);
		else
			button_control.setText(R.string.control_disable);
	}

	private void savePrefs() {
		// TCP SOCKS5
		prefs.setSocksAddr(edittext_socks_addr.getText().toString());
		prefs.setSocksPortStr(edittext_socks_port.getText().toString());
		prefs.setSocksUser(edittext_socks_user.getText().toString());
		prefs.setSocksPass(edittext_socks_pass.getText().toString());

		// UDP SOCKS5
		prefs.setUdpAddr(edittext_udp_addr.getText().toString());
		prefs.setUdpPort(edittext_udp_port.getText().toString());
		prefs.setUdpUser(edittext_udp_user.getText().toString());
		prefs.setUdpPass(edittext_udp_pass.getText().toString());

		// UDP转发模式
		String udpRelayMode = spinner_udp_relay_mode.getSelectedItemPosition() == 0 ? "tcp" : "udp";
		prefs.setUdpRelayMode(udpRelayMode);

		// DNS
		prefs.setDnsIpv4(edittext_dns_ipv4.getText().toString());
		prefs.setDnsIpv6(edittext_dns_ipv6.getText().toString());

		// 功能开关
		prefs.setChnroutesEnabled(checkbox_chnroutes_enabled.isChecked());
		prefs.setAclEnabled(checkbox_acl_enabled.isChecked());
		prefs.setSmartProxyEnabled(checkbox_smart_proxy_enabled.isChecked());
		prefs.setRemoteDns(checkbox_remote_dns.isChecked());

		// 智能代理配置
		prefs.setSmartProxyTimeout(edittext_smart_proxy_timeout.getText().toString());
		prefs.setSmartProxyBlockExpiry(edittext_smart_proxy_block_expiry.getText().toString());

		// IP协议
		if (!checkbox_ipv4.isChecked() && !checkbox_ipv6.isChecked())
			checkbox_ipv4.setChecked(prefs.getIpv4());
		prefs.setIpv4(checkbox_ipv4.isChecked());
		prefs.setIpv6(checkbox_ipv6.isChecked());
		prefs.setGlobal(checkbox_global.isChecked());

		// 更新配置文件
		if (configManager.updateMainYaml(prefs)) {
			Log.i(TAG, "Configuration file updated successfully");
		} else {
			Log.e(TAG, "Failed to update configuration file");
			Toast.makeText(this, "配置文件更新失败", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * 刷新日志显示
	 */
	private void refreshLog() {
		try {
			int logSize = TProxyService.getLogSize();
			if (logSize > 0) {
				// 读取最多8KB的日志数据
				int readSize = Math.min(logSize, 8192);
				byte[] logData = TProxyService.getLog(readSize);

				if (logData != null && logData.length > 0) {
					String logText = new String(logData, "UTF-8");

					// 只显示最后10行日志
					String[] lines = logText.split("\n");
					StringBuilder displayLines = new StringBuilder();
					int start = Math.max(0, lines.length - 10);

					for (int i = start; i < lines.length; i++) {
						if (!lines[i].trim().isEmpty()) {
							displayLines.append(lines[i]).append("\n");
						}
					}

					final String finalLogText = displayLines.toString();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							log_textview.setText(finalLogText);
							// 自动滚动到底部
							log_scrollview.post(new Runnable() {
								@Override
								public void run() {
									log_scrollview.fullScroll(ScrollView.FOCUS_DOWN);
								}
							});
						}
					});
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error refreshing log", e);
		}
	}
}
