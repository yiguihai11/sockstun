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
import android.os.Build;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.net.VpnService;
import android.widget.TextView;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements View.OnClickListener {
	private Preferences prefs;
	private EditText edittext_socks_addr;
	private EditText edittext_socks_port;
	private EditText edittext_socks_user;
	private EditText edittext_socks_pass;
	private EditText edittext_dns_ipv4;
	private EditText edittext_dns_ipv6;
	private EditText edittext_bypass_ip_ranges;
	private CheckBox checkbox_udp_in_tcp;
	private CheckBox checkbox_global;
	private CheckBox checkbox_ipv4;
	private CheckBox checkbox_ipv6;
	private CheckBox checkbox_bypass_cn;
	private LinearLayout layout_bypass_ip_container;
	private Button button_apps;
	private Button button_save;
	private Button button_control;
	private TextView logText;
	private static final int MAX_LOG_LENGTH = 10000;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = new Preferences(this);
		setContentView(R.layout.main);

		edittext_socks_addr = (EditText) findViewById(R.id.socks_addr);
		edittext_socks_port = (EditText) findViewById(R.id.socks_port);
		edittext_socks_user = (EditText) findViewById(R.id.socks_user);
		edittext_socks_pass = (EditText) findViewById(R.id.socks_pass);
		edittext_dns_ipv4 = (EditText) findViewById(R.id.dns_ipv4);
		edittext_dns_ipv6 = (EditText) findViewById(R.id.dns_ipv6);
		edittext_bypass_ip_ranges = (EditText) findViewById(R.id.bypass_ip_ranges);
		checkbox_ipv4 = (CheckBox) findViewById(R.id.ipv4);
		checkbox_ipv6 = (CheckBox) findViewById(R.id.ipv6);
		checkbox_global = (CheckBox) findViewById(R.id.global);
		checkbox_udp_in_tcp = (CheckBox) findViewById(R.id.udp_in_tcp);
		checkbox_bypass_cn = (CheckBox) findViewById(R.id.bypass_cn);
		layout_bypass_ip_container = (LinearLayout) findViewById(R.id.bypass_ip_container);
		button_apps = (Button) findViewById(R.id.apps);
		button_save = (Button) findViewById(R.id.save);
		button_control = (Button) findViewById(R.id.control);
		logText = findViewById(R.id.log_text);

		// 检查Android版本，如果低于API 33则隐藏绕过中国IP相关功能
		if (Build.VERSION.SDK_INT < 33) {
			checkbox_bypass_cn.setVisibility(View.GONE);
			layout_bypass_ip_container.setVisibility(View.GONE);
			// 如果之前启用了这个功能，在低版本系统中需要禁用它
			if (prefs.getBypassCN()) {
				prefs.setBypassCN(false);
				prefs.setBypassIpRanges("");
			}
		}

		checkbox_udp_in_tcp.setOnClickListener(this);
		checkbox_global.setOnClickListener(this);
		checkbox_bypass_cn.setOnClickListener(this);
		button_apps.setOnClickListener(this);
		button_save.setOnClickListener(this);
		button_control.setOnClickListener(this);
		updateUI();

		/* Request VPN permission */
		Intent intent = VpnService.prepare(MainActivity.this);
		if (intent != null)
		  startActivityForResult(intent, 0);
		else
		  onActivityResult(0, RESULT_OK, null);

		// 启动定时器更新日志
		startLogUpdateTimer();
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
		if (view == checkbox_global) {
			savePrefs();
			updateUI();
		} else if (view == checkbox_bypass_cn) {
			// 在点击事件中也要检查版本
			if (Build.VERSION.SDK_INT >= 33) {
				layout_bypass_ip_container.setVisibility(
					checkbox_bypass_cn.isChecked() ? View.VISIBLE : View.GONE
				);
				savePrefs();
			}
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
		}
	}

	private void updateUI() {
		edittext_socks_addr.setText(prefs.getSocksAddress());
		edittext_socks_port.setText(Integer.toString(prefs.getSocksPort()));
		edittext_socks_user.setText(prefs.getSocksUsername());
		edittext_socks_pass.setText(prefs.getSocksPassword());
		edittext_dns_ipv4.setText(prefs.getDnsIpv4());
		edittext_dns_ipv6.setText(prefs.getDnsIpv6());
		edittext_bypass_ip_ranges.setText(prefs.getBypassIpRanges());
		checkbox_ipv4.setChecked(prefs.getIpv4());
		checkbox_ipv6.setChecked(prefs.getIpv6());
		checkbox_global.setChecked(prefs.getGlobal());
		checkbox_udp_in_tcp.setChecked(prefs.getUdpInTcp());
		
		// 只在Android 13及以上版本显示绕过中国IP功能
		if (Build.VERSION.SDK_INT >= 33) {
			checkbox_bypass_cn.setChecked(prefs.getBypassCN());
			layout_bypass_ip_container.setVisibility(
				prefs.getBypassCN() ? View.VISIBLE : View.GONE
			);
		}

		boolean editable = !prefs.getEnable();
		edittext_socks_addr.setEnabled(editable);
		edittext_socks_port.setEnabled(editable);
		edittext_socks_user.setEnabled(editable);
		edittext_socks_pass.setEnabled(editable);
		edittext_dns_ipv4.setEnabled(editable);
		edittext_dns_ipv6.setEnabled(editable);
		if (Build.VERSION.SDK_INT >= 33) {
			edittext_bypass_ip_ranges.setEnabled(editable);
			checkbox_bypass_cn.setEnabled(editable);
		}
		checkbox_udp_in_tcp.setEnabled(editable);
		checkbox_global.setEnabled(editable);
		checkbox_ipv4.setEnabled(editable);
		checkbox_ipv6.setEnabled(editable);
		button_apps.setEnabled(editable && !prefs.getGlobal());
		button_save.setEnabled(editable);

		if (editable)
		  button_control.setText(R.string.control_enable);
		else
		  button_control.setText(R.string.control_disable);
	}

	private void savePrefs() {
		prefs.setSocksAddress(edittext_socks_addr.getText().toString());
		prefs.setSocksPort(Integer.parseInt(edittext_socks_port.getText().toString()));
		prefs.setSocksUsername(edittext_socks_user.getText().toString());
		prefs.setSocksPassword(edittext_socks_pass.getText().toString());
		prefs.setDnsIpv4(edittext_dns_ipv4.getText().toString());
		prefs.setDnsIpv6(edittext_dns_ipv6.getText().toString());
		if (Build.VERSION.SDK_INT >= 33) {
			prefs.setBypassIpRanges(edittext_bypass_ip_ranges.getText().toString());
			prefs.setBypassCN(checkbox_bypass_cn.isChecked());
		}
		if (!checkbox_ipv4.isChecked() && !checkbox_ipv4.isChecked())
		  checkbox_ipv4.setChecked(prefs.getIpv4());
		prefs.setIpv4(checkbox_ipv4.isChecked());
		prefs.setIpv6(checkbox_ipv6.isChecked());
		prefs.setGlobal(checkbox_global.isChecked());
		prefs.setUdpInTcp(checkbox_udp_in_tcp.isChecked());
	}

	private void startLogUpdateTimer() {
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updateLog();
					}
				});
			}
		}, 0, 1000); // 每秒更新一次
	}

	private void updateLog() {
		if (TProxyService.isRunning()) {
			long[] stats = TProxyService.getStats();
			String log = String.format("发送数据包: %d\n发送字节数: %d\n接收数据包: %d\n接收字节数: %d\n",
				stats[0], stats[1], stats[2], stats[3]);
			appendLog(log);
		}
	}

	private void appendLog(String text) {
		if (logText != null) {
			String currentText = logText.getText().toString();
			String newText = currentText + text;
			
			// 如果日志太长，删除旧的内容
			if (newText.length() > MAX_LOG_LENGTH) {
				newText = newText.substring(newText.length() - MAX_LOG_LENGTH);
			}
			
			logText.setText(newText);
			
			// 滚动到底部
			final int scrollAmount = logText.getLayout().getLineTop(logText.getLineCount()) - logText.getHeight();
			if (scrollAmount > 0) {
				logText.scrollTo(0, scrollAmount);
			}
		}
	}
}
