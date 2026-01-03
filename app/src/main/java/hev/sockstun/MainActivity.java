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
import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost;
import android.net.VpnService;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;

public class MainActivity extends TabActivity implements View.OnClickListener {
	private Preferences prefs;
	private TabHost tabHost;
	private BroadcastReceiver vpnStateReceiver;
	private EditText edittext_socks_addr;
	private EditText edittext_socks_udp_addr;
	private EditText edittext_socks_udp_port;
	private EditText edittext_socks_udp_user;
	private EditText edittext_socks_udp_pass;
	private EditText edittext_socks_port;
	private EditText edittext_socks_user;
	private EditText edittext_socks_pass;
	private EditText edittext_dns_ipv4;
	private EditText edittext_dns_ipv6;
	private CheckBox checkbox_udp_in_tcp;
	private CheckBox checkbox_remote_dns;
	private CheckBox checkbox_global;
	private CheckBox checkbox_ipv4;
	private CheckBox checkbox_ipv6;
	private Button button_apps;
	private Button button_logs;
	private Button button_save;
	private Button button_control;
	private Spinner spinner_log_level;
	private TextView textview_github_link;
	private EditText edittext_task_stack_size;
	private EditText edittext_tcp_buffer_size;
	private EditText edittext_udp_recv_buffer_size;
	private EditText edittext_udp_copy_buffer_nums;
	private EditText edittext_connect_timeout;
	private EditText edittext_tcp_read_write_timeout;
	private EditText edittext_udp_read_write_timeout;
	private EditText edittext_max_session_count;
	private EditText edittext_pid_file;
	private EditText edittext_limit_nofile;
	private EditText edittext_tunnel_mtu;
	private EditText edittext_tunnel_name;
	private CheckBox checkbox_tunnel_multi_queue;
	private EditText edittext_tunnel_ipv4;
	private EditText edittext_tunnel_ipv6;
	private EditText edittext_tunnel_post_up_script;
	private EditText edittext_tunnel_pre_down_script;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = new Preferences(this);
		setContentView(R.layout.main);

		tabHost = getTabHost();

		// Setup tabs
		tabHost.addTab(tabHost.newTabSpec("socks")
			.setIndicator("SOCKS")
			.setContent(R.id.tab_socks));
		tabHost.addTab(tabHost.newTabSpec("tunnel")
			.setIndicator("Tunnel")
			.setContent(R.id.tab_tunnel));
		tabHost.addTab(tabHost.newTabSpec("misc")
			.setIndicator("Misc")
			.setContent(R.id.tab_misc));
		tabHost.addTab(tabHost.newTabSpec("network")
			.setIndicator("Network")
			.setContent(R.id.tab_network));
		tabHost.addTab(tabHost.newTabSpec("advanced")
			.setIndicator("Advanced")
			.setContent(R.id.tab_advanced));

		edittext_socks_addr = (EditText) findViewById(R.id.socks_addr);
		edittext_socks_udp_addr = (EditText) findViewById(R.id.socks_udp_addr);
		edittext_socks_udp_port = (EditText) findViewById(R.id.socks_udp_port);
		edittext_socks_udp_user = (EditText) findViewById(R.id.socks_udp_user);
		edittext_socks_udp_pass = (EditText) findViewById(R.id.socks_udp_pass);
		edittext_socks_port = (EditText) findViewById(R.id.socks_port);
		edittext_socks_user = (EditText) findViewById(R.id.socks_user);
		edittext_socks_pass = (EditText) findViewById(R.id.socks_pass);
		edittext_dns_ipv4 = (EditText) findViewById(R.id.dns_ipv4);
		edittext_dns_ipv6 = (EditText) findViewById(R.id.dns_ipv6);
		checkbox_ipv4 = (CheckBox) findViewById(R.id.ipv4);
		checkbox_ipv6 = (CheckBox) findViewById(R.id.ipv6);
		checkbox_global = (CheckBox) findViewById(R.id.global);
		checkbox_udp_in_tcp = (CheckBox) findViewById(R.id.udp_in_tcp);
		checkbox_remote_dns = (CheckBox) findViewById(R.id.remote_dns);
		button_apps = (Button) findViewById(R.id.apps);
		button_logs = (Button) findViewById(R.id.logs);
		button_save = (Button) findViewById(R.id.save);
		button_control = (Button) findViewById(R.id.control);

		edittext_task_stack_size = (EditText) findViewById(R.id.task_stack_size);
		edittext_tcp_buffer_size = (EditText) findViewById(R.id.tcp_buffer_size);
		edittext_udp_recv_buffer_size = (EditText) findViewById(R.id.udp_recv_buffer_size);
		edittext_udp_copy_buffer_nums = (EditText) findViewById(R.id.udp_copy_buffer_nums);
		edittext_connect_timeout = (EditText) findViewById(R.id.connect_timeout);
		edittext_tcp_read_write_timeout = (EditText) findViewById(R.id.tcp_read_write_timeout);
		edittext_udp_read_write_timeout = (EditText) findViewById(R.id.udp_read_write_timeout);
		edittext_max_session_count = (EditText) findViewById(R.id.max_session_count);
		edittext_pid_file = (EditText) findViewById(R.id.pid_file);
		edittext_limit_nofile = (EditText) findViewById(R.id.limit_nofile);
		edittext_tunnel_mtu = (EditText) findViewById(R.id.tunnel_mtu);
		edittext_tunnel_name = (EditText) findViewById(R.id.tunnel_name);
		checkbox_tunnel_multi_queue = (CheckBox) findViewById(R.id.tunnel_multi_queue);
		edittext_tunnel_ipv4 = (EditText) findViewById(R.id.tunnel_ipv4);
		edittext_tunnel_ipv6 = (EditText) findViewById(R.id.tunnel_ipv6);
		edittext_tunnel_post_up_script = (EditText) findViewById(R.id.tunnel_post_up_script);
		edittext_tunnel_pre_down_script = (EditText) findViewById(R.id.tunnel_pre_down_script);

		// Setup clickable GitHub link
		textview_github_link = (TextView) findViewById(R.id.github_link);
		String fullText = textview_github_link.getText().toString();
		SpannableString spannableString = new SpannableString(fullText);
		String repoName = "yiguihai11/sockstun";
		int startIndex = fullText.indexOf(repoName);
		int endIndex = startIndex + repoName.length();

		// Make repo name clickable and blue
		spannableString.setSpan(new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yiguihai11/sockstun"));
				startActivity(browserIntent);
			}
		}, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		spannableString.setSpan(new ForegroundColorSpan(0xFF2196F3), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		textview_github_link.setText(spannableString);
		textview_github_link.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

		// Setup log level spinner
		spinner_log_level = (Spinner) findViewById(R.id.log_level);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
			R.array.log_level_entries, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_log_level.setAdapter(adapter);

		checkbox_udp_in_tcp.setOnClickListener(this);
		checkbox_remote_dns.setOnClickListener(this);
		checkbox_global.setOnClickListener(this);
		button_apps.setOnClickListener(this);
		button_logs.setOnClickListener(this);
		button_save.setOnClickListener(this);
		button_control.setOnClickListener(this);
		updateUI();

		// Register VPN state receiver
		vpnStateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if ("hev.sockstun.VPN_STOPPED".equals(intent.getAction())) {
					updateUI();
				}
			}
		};
		IntentFilter filter = new IntentFilter("hev.sockstun.VPN_STOPPED");
		registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

		/* Request VPN permission */
		Intent intent = VpnService.prepare(MainActivity.this);
		if (intent != null)
		  startActivityForResult(intent, 0);
		else
		  onActivityResult(0, RESULT_OK, null);
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		if ((result == RESULT_OK) && prefs.getEnable()) {
			Intent intent = new Intent(this, TProxyService.class);
			startService(intent.setAction(TProxyService.ACTION_CONNECT));
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (vpnStateReceiver != null) {
			unregisterReceiver(vpnStateReceiver);
			vpnStateReceiver = null;
		}
	}

	@Override
	public void onClick(View view) {
		if (view == checkbox_global || view == checkbox_remote_dns) {
			savePrefs();
			updateUI();
		} else if (view == button_apps) {
			startActivity(new Intent(this, AppListActivity.class));
		} else if (view == button_logs) {
			startActivity(new Intent(this, LogActivity.class));
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
		edittext_socks_udp_addr.setText(prefs.getSocksUdpAddress());
		int udpPort = prefs.getSocksUdpPort();
		edittext_socks_udp_port.setText(udpPort > 0 ? Integer.toString(udpPort) : "");
		edittext_socks_udp_user.setText(prefs.getSocksUdpUsername());
		edittext_socks_udp_pass.setText(prefs.getSocksUdpPassword());
		edittext_socks_port.setText(Integer.toString(prefs.getSocksPort()));
		edittext_socks_user.setText(prefs.getSocksUsername());
		edittext_socks_pass.setText(prefs.getSocksPassword());
		edittext_dns_ipv4.setText(prefs.getDnsIpv4());
		edittext_dns_ipv6.setText(prefs.getDnsIpv6());
		checkbox_ipv4.setChecked(prefs.getIpv4());
		checkbox_ipv6.setChecked(prefs.getIpv6());
		checkbox_global.setChecked(prefs.getGlobal());
		checkbox_udp_in_tcp.setChecked(prefs.getUdpInTcp());
		checkbox_remote_dns.setChecked(prefs.getRemoteDns());

		edittext_task_stack_size.setText(Integer.toString(prefs.getTaskStackSize()));

		// Set log level spinner selection
		String logLevel = prefs.getLogLevel();
		String[] logLevelValues = getResources().getStringArray(R.array.log_level_values);
		for (int i = 0; i < logLevelValues.length; i++) {
			if (logLevelValues[i].equals(logLevel)) {
				spinner_log_level.setSelection(i);
				break;
			}
		}
		edittext_tcp_buffer_size.setText(Integer.toString(prefs.getTcpBufferSize()));
		edittext_udp_recv_buffer_size.setText(Integer.toString(prefs.getUdpRecvBufferSize()));
		edittext_udp_copy_buffer_nums.setText(Integer.toString(prefs.getUdpCopyBufferNums()));
		edittext_connect_timeout.setText(Integer.toString(prefs.getConnectTimeout()));
		edittext_tcp_read_write_timeout.setText(Integer.toString(prefs.getTcpReadWriteTimeout()));
		edittext_udp_read_write_timeout.setText(Integer.toString(prefs.getUdpReadWriteTimeout()));
		edittext_max_session_count.setText(Integer.toString(prefs.getMaxSessionCount()));
		edittext_pid_file.setText(prefs.getPidFile());
		edittext_limit_nofile.setText(Integer.toString(prefs.getLimitNofile()));

		edittext_tunnel_mtu.setText(Integer.toString(prefs.getTunnelMtu()));
		edittext_tunnel_name.setText(prefs.getTunnelName());
		checkbox_tunnel_multi_queue.setChecked(prefs.getTunnelMultiQueue());
		edittext_tunnel_ipv4.setText(prefs.getTunnelIpv4());
		edittext_tunnel_ipv6.setText(prefs.getTunnelIpv6());
		edittext_tunnel_post_up_script.setText(prefs.getTunnelPostUpScript());
		edittext_tunnel_pre_down_script.setText(prefs.getTunnelPreDownScript());

		boolean editable = !prefs.getEnable();
		edittext_socks_addr.setEnabled(editable);
		edittext_socks_udp_addr.setEnabled(editable);
		edittext_socks_udp_port.setEnabled(editable);
		edittext_socks_udp_user.setEnabled(editable);
		edittext_socks_udp_pass.setEnabled(editable);
		edittext_socks_port.setEnabled(editable);
		edittext_socks_user.setEnabled(editable);
		edittext_socks_pass.setEnabled(editable);
		edittext_dns_ipv4.setEnabled(editable && !prefs.getRemoteDns());
		edittext_dns_ipv6.setEnabled(editable && !prefs.getRemoteDns());
		checkbox_udp_in_tcp.setEnabled(editable);
		checkbox_remote_dns.setEnabled(editable);
		checkbox_global.setEnabled(editable);
		checkbox_ipv4.setEnabled(editable);
		checkbox_ipv6.setEnabled(editable);
		button_apps.setEnabled(editable && !prefs.getGlobal());
		button_save.setEnabled(editable);

		// Tunnel options
		edittext_tunnel_mtu.setEnabled(editable);
		edittext_tunnel_name.setEnabled(editable);
		// Tunnel options: always disabled (not supported)
		checkbox_tunnel_multi_queue.setEnabled(false);
		edittext_tunnel_ipv4.setEnabled(editable);
		edittext_tunnel_ipv6.setEnabled(editable);
		edittext_tunnel_post_up_script.setEnabled(false);
		edittext_tunnel_pre_down_script.setEnabled(false);

		// Misc options
		edittext_task_stack_size.setEnabled(editable);
		spinner_log_level.setEnabled(editable);
		// Misc options: always disabled except task_stack_size
		edittext_tcp_buffer_size.setEnabled(false);
		edittext_udp_recv_buffer_size.setEnabled(false);
		edittext_udp_copy_buffer_nums.setEnabled(false);
		edittext_connect_timeout.setEnabled(false);
		edittext_tcp_read_write_timeout.setEnabled(false);
		edittext_udp_read_write_timeout.setEnabled(false);
		edittext_max_session_count.setEnabled(false);
		edittext_pid_file.setEnabled(false);
		edittext_limit_nofile.setEnabled(false);

		if (editable)
		  button_control.setText(R.string.control_enable);
		else
		  button_control.setText(R.string.control_disable);
	}

	private void savePrefs() {
		prefs.setSocksAddress(edittext_socks_addr.getText().toString());
		prefs.setSocksUdpAddress(edittext_socks_udp_addr.getText().toString());

		String udpPortStr = edittext_socks_udp_port.getText().toString();
		int udpPort = udpPortStr.isEmpty() ? 0 : Integer.parseInt(udpPortStr);
		prefs.setSocksUdpPort(udpPort);

		prefs.setSocksUdpUsername(edittext_socks_udp_user.getText().toString());
		prefs.setSocksUdpPassword(edittext_socks_udp_pass.getText().toString());

		prefs.setSocksPort(Integer.parseInt(edittext_socks_port.getText().toString()));
		prefs.setSocksUsername(edittext_socks_user.getText().toString());
		prefs.setSocksPassword(edittext_socks_pass.getText().toString());
		prefs.setDnsIpv4(edittext_dns_ipv4.getText().toString());
		prefs.setDnsIpv6(edittext_dns_ipv6.getText().toString());
		if (!checkbox_ipv4.isChecked() && !checkbox_ipv6.isChecked())
		  checkbox_ipv4.setChecked(prefs.getIpv4());
		prefs.setIpv4(checkbox_ipv4.isChecked());
		prefs.setIpv6(checkbox_ipv6.isChecked());
		prefs.setGlobal(checkbox_global.isChecked());
		prefs.setUdpInTcp(checkbox_udp_in_tcp.isChecked());
		prefs.setRemoteDns(checkbox_remote_dns.isChecked());

		prefs.setTaskStackSize(Integer.parseInt(edittext_task_stack_size.getText().toString()));

		// Save log level from spinner
		String[] logLevelValues = getResources().getStringArray(R.array.log_level_values);
		int selectedPosition = spinner_log_level.getSelectedItemPosition();
		prefs.setLogLevel(logLevelValues[selectedPosition]);
		prefs.setTcpBufferSize(Integer.parseInt(edittext_tcp_buffer_size.getText().toString()));
		prefs.setUdpRecvBufferSize(Integer.parseInt(edittext_udp_recv_buffer_size.getText().toString()));
		prefs.setUdpCopyBufferNums(Integer.parseInt(edittext_udp_copy_buffer_nums.getText().toString()));
		prefs.setConnectTimeout(Integer.parseInt(edittext_connect_timeout.getText().toString()));
		prefs.setTcpReadWriteTimeout(Integer.parseInt(edittext_tcp_read_write_timeout.getText().toString()));
		prefs.setUdpReadWriteTimeout(Integer.parseInt(edittext_udp_read_write_timeout.getText().toString()));
		prefs.setMaxSessionCount(Integer.parseInt(edittext_max_session_count.getText().toString()));
		prefs.setPidFile(edittext_pid_file.getText().toString());
		prefs.setLimitNofile(Integer.parseInt(edittext_limit_nofile.getText().toString()));

		prefs.setTunnelMtu(Integer.parseInt(edittext_tunnel_mtu.getText().toString()));
		prefs.setTunnelName(edittext_tunnel_name.getText().toString());
		prefs.setTunnelMultiQueue(checkbox_tunnel_multi_queue.isChecked());
		prefs.setTunnelIpv4(edittext_tunnel_ipv4.getText().toString());
		prefs.setTunnelIpv6(edittext_tunnel_ipv6.getText().toString());
		prefs.setTunnelPostUpScript(edittext_tunnel_post_up_script.getText().toString());
		prefs.setTunnelPreDownScript(edittext_tunnel_pre_down_script.getText().toString());
	}
}
