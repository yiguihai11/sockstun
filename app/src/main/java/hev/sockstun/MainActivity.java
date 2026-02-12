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
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.content.IntentFilter;
import android.view.View;
import android.widget.TabWidget;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.net.VpnService;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.IOException;

public class MainActivity extends TabActivity implements View.OnClickListener {
	private Preferences prefs;
	private TabHost tabHost;
	private HorizontalScrollView tabScroller;
	private TabWidget tabWidget;
	private BroadcastReceiver vpnStateReceiver;
	private EditText edittext_socks_addr;
	private EditText edittext_socks_udp_addr;
	private EditText edittext_socks_udp_port;
	private EditText edittext_socks_udp_user;
	private EditText edittext_socks_udp_pass;
	private EditText edittext_socks_port;
	private EditText edittext_socks_user;
	private EditText edittext_socks_pass;
	private TextView textview_socks_pass_toggle;
	private TextView textview_socks_udp_pass_toggle;
	private EditText edittext_dns_ipv4;
	private EditText edittext_dns_ipv6;
	private LinearLayout linearlayout_system_dns_v4_container;
	private LinearLayout linearlayout_system_dns_v6_container;
	private CheckBox checkbox_udp_in_tcp;
	private CheckBox checkbox_remote_dns;
	private CheckBox checkbox_global;
	private CheckBox checkbox_ipv4;
	private CheckBox checkbox_ipv6;
	private CheckBox checkbox_bypass_lan;
	private Button button_apps;
	private Button button_logs;
	private Button button_blacklist;
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
	// Chnroutes
	private CheckBox checkbox_chnroutes_enabled;
	private Button button_chnroutes_upload;
	private Button button_chnroutes_extract;
	private Button button_chnroutes_clear;
	private Button button_chnroutes_refresh;
	private Button button_chnroutes_save;
	private EditText edittext_chnroutes_content;
	private TextView textview_chnroutes_path_info;
	private static final int CHNROUTES_UPLOAD_REQUEST_CODE = 100;
	private boolean chnroutesLoaded = false;

	// ACL
	private CheckBox checkbox_acl_enabled;
	private Button button_acl_upload;
	private Button button_acl_extract;
	private Button button_acl_clear;
	private Button button_acl_refresh;
	private Button button_acl_save;
	private EditText edittext_acl_content;
	private TextView textview_acl_path_info;
	private static final int ACL_UPLOAD_REQUEST_CODE = 101;
	private boolean aclLoaded = false;

	// DNS Split Tunnel UI elements
	private CheckBox checkbox_dns_split_tunnel_enable;
	private LinearLayout dns_entries_container;
	private Button dns_add_button;
	private java.util.List<EditText> dns_entry_edit_texts = new java.util.ArrayList<EditText>();

	// DNS Forwarder UI elements
	private CheckBox checkbox_dns_forwarder_enabled;
	private EditText edittext_dns_virtual_ip4;
	private EditText edittext_dns_virtual_ip6;
	private EditText edittext_dns_target_ip4;
	private EditText edittext_dns_target_ip6;

	// Mapped DNS UI elements
	private EditText edittext_mapdns_address;
	private EditText edittext_mapdns_address6;
	private EditText edittext_mapdns_port;
	private EditText edittext_mapdns_network;
	private EditText edittext_mapdns_netmask;
	private EditText edittext_mapdns_network6;
	private EditText edittext_mapdns_prefixlen;
	private EditText edittext_mapdns_cache_size;

	// DNS Latency Optimize UI elements
	private CheckBox checkbox_dns_latency_optimize_enabled;
	private EditText edittext_dns_latency_optimize_timeout;

	// Smart Proxy UI elements
	private CheckBox checkbox_smart_proxy_enabled;
	private EditText edittext_smart_proxy_timeout;
	private EditText edittext_smart_proxy_blocked_ip_expiry;
	private LinearLayout probe_ports_container;
	private Button probe_port_add_button;
	private java.util.List<EditText> probe_port_edit_texts = new java.util.ArrayList<EditText>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = new Preferences(this);
		setContentView(R.layout.main);

		tabHost = getTabHost();

		// Setup tabs
		tabHost.addTab(tabHost.newTabSpec("socks")
			.setIndicator(getString(R.string.tab_socks))
			.setContent(R.id.tab_socks));
		tabHost.addTab(tabHost.newTabSpec("tunnel")
			.setIndicator(getString(R.string.tab_tunnel))
			.setContent(R.id.tab_tunnel));
		tabHost.addTab(tabHost.newTabSpec("network")
			.setIndicator(getString(R.string.tab_network))
			.setContent(R.id.tab_network));
		tabHost.addTab(tabHost.newTabSpec("smartproxy")
			.setIndicator(getString(R.string.tab_smart_proxy))
			.setContent(R.id.tab_smartproxy));
		tabHost.addTab(tabHost.newTabSpec("dns")
			.setIndicator(getString(R.string.tab_dns))
			.setContent(R.id.tab_dns));
		tabHost.addTab(tabHost.newTabSpec("chnroutes")
			.setIndicator(getString(R.string.tab_chnroutes))
			.setContent(R.id.tab_chnroutes));
		tabHost.addTab(tabHost.newTabSpec("acl")
			.setIndicator(getString(R.string.tab_acl))
			.setContent(R.id.tab_acl));
		tabHost.addTab(tabHost.newTabSpec("misc")
			.setIndicator(getString(R.string.tab_misc))
			.setContent(R.id.tab_misc));
		tabHost.addTab(tabHost.newTabSpec("about")
			.setIndicator(getString(R.string.tab_about_label))
			.setContent(R.id.tab_about));

		// Setup tab scroller for auto-scroll on tab change
		tabScroller = (HorizontalScrollView) findViewById(R.id.tab_scroller);
		tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
			@Override
			public void onTabChanged(String tabId) {
				scrollTabToView(tabId);
				// Lazy load chnroutes content when tab is first selected
				if ("chnroutes".equals(tabId) && !chnroutesLoaded) {
					chnroutesLoaded = true;
					loadChnroutesContent();
				}
				// Lazy load acl content when tab is first selected
				if ("acl".equals(tabId) && !aclLoaded) {
					aclLoaded = true;
					loadAclContent();
				}
				// Refresh system DNS when network tab is selected
				if ("network".equals(tabId)) {
					updateSystemDns();
				}
			}
		});

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
		linearlayout_system_dns_v4_container = (LinearLayout) findViewById(R.id.system_dns_v4_container);
		linearlayout_system_dns_v6_container = (LinearLayout) findViewById(R.id.system_dns_v6_container);
		checkbox_ipv4 = (CheckBox) findViewById(R.id.ipv4);
		checkbox_ipv6 = (CheckBox) findViewById(R.id.ipv6);
		checkbox_bypass_lan = (CheckBox) findViewById(R.id.bypass_lan);
		checkbox_global = (CheckBox) findViewById(R.id.global);
		checkbox_udp_in_tcp = (CheckBox) findViewById(R.id.udp_in_tcp);
		checkbox_remote_dns = (CheckBox) findViewById(R.id.remote_dns);
		button_apps = (Button) findViewById(R.id.apps);
		button_logs = (Button) findViewById(R.id.logs);
		button_blacklist = (Button) findViewById(R.id.blacklist);
		button_save = (Button) findViewById(R.id.save);
		button_control = (Button) findViewById(R.id.control);
		textview_socks_pass_toggle = (TextView) findViewById(R.id.socks_pass_toggle);
		textview_socks_udp_pass_toggle = (TextView) findViewById(R.id.socks_udp_pass_toggle);

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

		// Chnroutes UI elements
		checkbox_chnroutes_enabled = (CheckBox) findViewById(R.id.chnroutes_enabled);
		button_chnroutes_upload = (Button) findViewById(R.id.chnroutes_upload);
		button_chnroutes_extract = (Button) findViewById(R.id.chnroutes_extract);
		button_chnroutes_clear = (Button) findViewById(R.id.chnroutes_clear);
		button_chnroutes_refresh = (Button) findViewById(R.id.chnroutes_refresh);
		button_chnroutes_save = (Button) findViewById(R.id.chnroutes_save);
		edittext_chnroutes_content = (EditText) findViewById(R.id.chnroutes_content);
		textview_chnroutes_path_info = (TextView) findViewById(R.id.chnroutes_path_info);

		// ACL UI elements
		checkbox_acl_enabled = (CheckBox) findViewById(R.id.acl_enabled);
		button_acl_upload = (Button) findViewById(R.id.acl_upload);
		button_acl_extract = (Button) findViewById(R.id.acl_extract);
		button_acl_clear = (Button) findViewById(R.id.acl_clear);
		button_acl_refresh = (Button) findViewById(R.id.acl_refresh);
		button_acl_save = (Button) findViewById(R.id.acl_save);
		edittext_acl_content = (EditText) findViewById(R.id.acl_content);
		textview_acl_path_info = (TextView) findViewById(R.id.acl_path_info);

		// DNS Split Tunnel UI elements
		checkbox_dns_split_tunnel_enable = (CheckBox) findViewById(R.id.dns_split_tunnel_enable);
		dns_entries_container = (LinearLayout) findViewById(R.id.dns_entries_container);
		dns_add_button = (Button) findViewById(R.id.dns_add_button);
		dns_add_button.setOnClickListener(this);

		// DNS Forwarder UI elements
		checkbox_dns_forwarder_enabled = (CheckBox) findViewById(R.id.dns_forwarder_enabled);
		edittext_dns_virtual_ip4 = (EditText) findViewById(R.id.dns_virtual_ip4);
		edittext_dns_virtual_ip6 = (EditText) findViewById(R.id.dns_virtual_ip6);
		edittext_dns_target_ip4 = (EditText) findViewById(R.id.dns_target_ip4);
		edittext_dns_target_ip6 = (EditText) findViewById(R.id.dns_target_ip6);

		// Mapped DNS UI elements
		edittext_mapdns_address = (EditText) findViewById(R.id.mapdns_address);
		edittext_mapdns_address6 = (EditText) findViewById(R.id.mapdns_address6);
		edittext_mapdns_port = (EditText) findViewById(R.id.mapdns_port);
		edittext_mapdns_network = (EditText) findViewById(R.id.mapdns_network);
		edittext_mapdns_netmask = (EditText) findViewById(R.id.mapdns_netmask);
		edittext_mapdns_network6 = (EditText) findViewById(R.id.mapdns_network6);
		edittext_mapdns_prefixlen = (EditText) findViewById(R.id.mapdns_prefixlen);
		edittext_mapdns_cache_size = (EditText) findViewById(R.id.mapdns_cache_size);

		// DNS Latency Optimize UI elements
		checkbox_dns_latency_optimize_enabled = (CheckBox) findViewById(R.id.dns_latency_optimize_enabled);
		edittext_dns_latency_optimize_timeout = (EditText) findViewById(R.id.dns_latency_optimize_timeout);

		// Smart Proxy UI elements
		checkbox_smart_proxy_enabled = (CheckBox) findViewById(R.id.smart_proxy_enabled);
		edittext_smart_proxy_timeout = (EditText) findViewById(R.id.smart_proxy_timeout);
		edittext_smart_proxy_blocked_ip_expiry = (EditText) findViewById(R.id.smart_proxy_blocked_ip_expiry);
		probe_ports_container = (LinearLayout) findViewById(R.id.probe_ports_container);
		probe_port_add_button = (Button) findViewById(R.id.probe_port_add_button);
		probe_port_add_button.setOnClickListener(this);

		// Setup chnroutes path info
		textview_chnroutes_path_info.setText("File path: " + getCacheDir().getAbsolutePath() + "/chnroutes.txt");

		// Don't load chnroutes content on startup - lazy load when tab is selected

		// Setup chnroutes button listeners
		checkbox_chnroutes_enabled.setOnClickListener(this);
		button_chnroutes_upload.setOnClickListener(this);
		button_chnroutes_extract.setOnClickListener(this);
		button_chnroutes_clear.setOnClickListener(this);
		button_chnroutes_refresh.setOnClickListener(this);
		button_chnroutes_save.setOnClickListener(this);

		// Setup ACL path info
		textview_acl_path_info.setText("File path: " + getCacheDir().getAbsolutePath() + "/acl.txt");

		// Don't load ACL content on startup - lazy load when tab is selected

		// Setup ACL button listeners
		checkbox_acl_enabled.setOnClickListener(this);
		button_acl_upload.setOnClickListener(this);
		button_acl_extract.setOnClickListener(this);
		button_acl_clear.setOnClickListener(this);
		button_acl_refresh.setOnClickListener(this);
		button_acl_save.setOnClickListener(this);

		// Setup clickable GitHub link
		textview_github_link = (TextView) findViewById(R.id.github_link);
		String versionName = "v1.0";
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			versionName = "v" + packageInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			// Keep default version
		}
		String fullText = getString(R.string.about_text, versionName);
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

		// Hide bypass_lan checkbox on API < 33
		if (android.os.Build.VERSION.SDK_INT < 33) {
			checkbox_bypass_lan.setVisibility(View.GONE);
		}

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
		button_blacklist.setOnClickListener(this);
		button_save.setOnClickListener(this);
		button_control.setOnClickListener(this);

		// Password visibility toggle listeners
		textview_socks_pass_toggle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				togglePasswordVisibility(edittext_socks_pass, textview_socks_pass_toggle);
			}
		});
		textview_socks_udp_pass_toggle.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				togglePasswordVisibility(edittext_socks_udp_pass, textview_socks_udp_pass_toggle);
			}
		});

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

		/* Request notification permission (Android 13+) */
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
					!= android.content.pm.PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 0);
			}
		}

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
		// Handle chnroutes file upload
		if (request == CHNROUTES_UPLOAD_REQUEST_CODE && result == RESULT_OK && data != null) {
			Uri uri = data.getData();
			try {
				copyFileFromUri(uri);
			} catch (IOException e) {
				Toast.makeText(this, getString(R.string.upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
			}
		}
		// Handle ACL file upload
		if (request == ACL_UPLOAD_REQUEST_CODE && result == RESULT_OK && data != null) {
			Uri uri = data.getData();
			try {
				copyFileFromUriForAcl(uri);
			} catch (IOException e) {
				Toast.makeText(this, getString(R.string.upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
			}
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
		if (view == checkbox_global || view == checkbox_remote_dns || view == checkbox_chnroutes_enabled) {
			savePrefs();
			updateUI();
		} else if (view == button_apps) {
			startActivity(new Intent(this, AppListActivity.class));
		} else if (view == button_logs) {
			startActivity(new Intent(this, LogActivity.class));
		} else if (view == button_blacklist) {
			startActivity(new Intent(this, BlacklistActivity.class));
		} else if (view == button_save) {
			savePrefs();
			Context context = getApplicationContext();
			Toast.makeText(context, getString(R.string.saved), Toast.LENGTH_SHORT).show();
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
		} else if (view == button_chnroutes_upload) {
			// Open file picker for uploading chnroutes file
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			startActivityForResult(intent, CHNROUTES_UPLOAD_REQUEST_CODE);
		} else if (view == button_chnroutes_extract) {
			// Extract chnroutes.txt from APK assets
			extractChnroutesFromAssets();
		} else if (view == button_chnroutes_clear) {
			// Clear the editor
			edittext_chnroutes_content.setText("");
		} else if (view == button_chnroutes_refresh) {
			// Refresh content from file
			loadChnroutesContent();
		} else if (view == button_chnroutes_save) {
			// Save content to file
			saveChnroutesContent();
		} else if (view == button_acl_upload) {
			// Upload ACL file
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			startActivityForResult(intent, ACL_UPLOAD_REQUEST_CODE);
		} else if (view == button_acl_extract) {
			// Extract acl.txt from APK assets
			extractAclFromAssets();
		} else if (view == button_acl_clear) {
			// Clear the editor
			edittext_acl_content.setText("");
		} else if (view == button_acl_refresh) {
			// Refresh content from file
			loadAclContent();
		} else if (view == button_acl_save) {
			// Save content to file
			saveAclContent();
		} else if (view == dns_add_button) {
			// Add new DNS entry
			addDnsEntryView("", true);
		} else if (view == probe_port_add_button) {
			// Add new probe port entry
			addProbePortEntryView(0, true);
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
		checkbox_bypass_lan.setChecked(prefs.getBypassLan());
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

		checkbox_chnroutes_enabled.setChecked(prefs.getChnroutesEnabled());
		checkbox_acl_enabled.setChecked(prefs.getAclEnabled());

		// DNS Split Tunnel preferences
		checkbox_dns_split_tunnel_enable.setChecked(prefs.getDnsSplitTunnelEnabled());
		loadDnsEntries();

		// DNS Forwarder preferences
		checkbox_dns_forwarder_enabled.setChecked(prefs.getDnsForwarderEnabled());
		edittext_dns_virtual_ip4.setText(prefs.getDnsVirtualIp4());
		edittext_dns_virtual_ip6.setText(prefs.getDnsVirtualIp6());
		edittext_dns_target_ip4.setText(prefs.getDnsTargetIp4());
		edittext_dns_target_ip6.setText(prefs.getDnsTargetIp6());

		// Mapped DNS preferences
		edittext_mapdns_address.setText(prefs.getMapdnsAddress());
		edittext_mapdns_address6.setText(prefs.getMapdnsAddress6());
		edittext_mapdns_port.setText(Integer.toString(prefs.getMapdnsPort()));
		edittext_mapdns_network.setText(prefs.getMapdnsNetwork());
		edittext_mapdns_netmask.setText(prefs.getMapdnsNetmask());
		edittext_mapdns_network6.setText(prefs.getMapdnsNetwork6());
		edittext_mapdns_prefixlen.setText(Integer.toString(prefs.getMapdnsPrefixlen()));
		edittext_mapdns_cache_size.setText(Integer.toString(prefs.getMapdnsCacheSize()));

		// DNS Latency Optimize preferences
		checkbox_dns_latency_optimize_enabled.setChecked(prefs.getDnsLatencyOptimizeEnabled());
		edittext_dns_latency_optimize_timeout.setText(Integer.toString(prefs.getDnsLatencyOptimizeTimeout()));

		// Smart Proxy preferences
		checkbox_smart_proxy_enabled.setChecked(prefs.getSmartProxyEnabled());
		edittext_smart_proxy_timeout.setText(Integer.toString(prefs.getSmartProxyTimeout()));
		edittext_smart_proxy_blocked_ip_expiry.setText(Integer.toString(prefs.getSmartProxyBlockedIpExpiry()));
		loadProbePortEntries();

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
		checkbox_bypass_lan.setEnabled(editable && android.os.Build.VERSION.SDK_INT >= 33);
		button_apps.setEnabled(editable);
		button_save.setEnabled(editable);

		// Tunnel options
		edittext_tunnel_mtu.setEnabled(editable);
		edittext_tunnel_name.setEnabled(false);
		// Tunnel options: always disabled (not supported)
		checkbox_tunnel_multi_queue.setEnabled(false);
		edittext_tunnel_ipv4.setEnabled(editable);
		edittext_tunnel_ipv6.setEnabled(editable);
		edittext_tunnel_post_up_script.setEnabled(false);
		edittext_tunnel_pre_down_script.setEnabled(false);

		// Misc options
		edittext_task_stack_size.setEnabled(editable);
		spinner_log_level.setEnabled(editable);
		// Misc options: enabled for user configuration
		edittext_tcp_buffer_size.setEnabled(editable);
		edittext_udp_recv_buffer_size.setEnabled(editable);
		edittext_udp_copy_buffer_nums.setEnabled(editable);
		edittext_connect_timeout.setEnabled(editable);
		edittext_tcp_read_write_timeout.setEnabled(editable);
		edittext_udp_read_write_timeout.setEnabled(editable);
		edittext_max_session_count.setEnabled(editable);
		// Not supported on Android
		edittext_pid_file.setEnabled(false);
		edittext_limit_nofile.setEnabled(false);

		// Chnroutes elements
		checkbox_chnroutes_enabled.setEnabled(editable);
		button_chnroutes_upload.setEnabled(editable);
		button_chnroutes_extract.setEnabled(editable);
		button_chnroutes_clear.setEnabled(editable);
		button_chnroutes_refresh.setEnabled(editable);
		button_chnroutes_save.setEnabled(editable);
		edittext_chnroutes_content.setEnabled(editable);

		// ACL elements
		checkbox_acl_enabled.setEnabled(editable);
		button_acl_upload.setEnabled(editable);
		button_acl_extract.setEnabled(editable);
		button_acl_clear.setEnabled(editable);
		button_acl_refresh.setEnabled(editable);
		button_acl_save.setEnabled(editable);
		edittext_acl_content.setEnabled(editable);

		// DNS Split Tunnel enable/disable
		checkbox_dns_split_tunnel_enable.setEnabled(editable);
		dns_add_button.setEnabled(editable);
		for (EditText edit : dns_entry_edit_texts) {
			edit.setEnabled(editable);
		}

		// DNS Forwarder enable/disable
		checkbox_dns_forwarder_enabled.setEnabled(editable);
		edittext_dns_virtual_ip4.setEnabled(editable);
		edittext_dns_virtual_ip6.setEnabled(editable);
		edittext_dns_target_ip4.setEnabled(editable);
		edittext_dns_target_ip6.setEnabled(editable);

		// Mapped DNS enable/disable
		edittext_mapdns_address.setEnabled(editable);
		edittext_mapdns_address6.setEnabled(editable);
		edittext_mapdns_port.setEnabled(editable);
		edittext_mapdns_network.setEnabled(editable);
		edittext_mapdns_netmask.setEnabled(editable);
		edittext_mapdns_network6.setEnabled(editable);
		edittext_mapdns_prefixlen.setEnabled(editable);
		edittext_mapdns_cache_size.setEnabled(editable);

		// DNS Latency Optimize enable/disable
		checkbox_dns_latency_optimize_enabled.setEnabled(editable);
		edittext_dns_latency_optimize_timeout.setEnabled(editable);

		// Smart Proxy enable/disable
		checkbox_smart_proxy_enabled.setEnabled(editable);
		edittext_smart_proxy_timeout.setEnabled(editable);
		edittext_smart_proxy_blocked_ip_expiry.setEnabled(editable);
		probe_port_add_button.setEnabled(editable);
		for (EditText edit : probe_port_edit_texts) {
			edit.setEnabled(editable);
		}

		if (editable)
		  button_control.setText(R.string.control_enable);
		else
		  button_control.setText(R.string.control_disable);
	}

	private void updateSystemDns() {
		try {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm == null) return;

			Network network = cm.getActiveNetwork();
			if (network == null) return;

			LinkProperties lp = cm.getLinkProperties(network);
			if (lp == null) return;

			java.util.List<java.net.InetAddress> dnsServers = lp.getDnsServers();
			if (dnsServers == null || dnsServers.isEmpty()) return;

			java.util.List<String> v4DnsList = new java.util.ArrayList<String>();
			java.util.List<String> v6DnsList = new java.util.ArrayList<String>();

			for (java.net.InetAddress dns : dnsServers) {
				String addr = dns.getHostAddress();
				if (dns instanceof java.net.Inet4Address) {
					v4DnsList.add(addr);
				} else if (dns instanceof java.net.Inet6Address) {
					v6DnsList.add(addr);
				}
			}

			// Create buttons for IPv4 DNS
			linearlayout_system_dns_v4_container.removeAllViews();
			if (!v4DnsList.isEmpty()) {
				// Add "System:" label
				TextView label = new TextView(this);
				label.setText(getString(R.string.system_prefix));
				label.setTextSize(11);
				label.setTextColor(0xFF808080);
				LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
				labelParams.gravity = android.view.Gravity.CENTER_VERTICAL;
				linearlayout_system_dns_v4_container.addView(label, labelParams);

				for (String dns : v4DnsList) {
					Button dnsButton = createDnsButton(dns, edittext_dns_ipv4);

					// Enable/disable based on VPN state
					boolean editable = !prefs.getEnable();
					dnsButton.setEnabled(editable && !prefs.getRemoteDns());

					LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
					params.setMargins(0, 0, 8, 0);
					params.gravity = android.view.Gravity.CENTER_VERTICAL;
					linearlayout_system_dns_v4_container.addView(dnsButton, params);
				}
			}

			// Create buttons for IPv6 DNS
			linearlayout_system_dns_v6_container.removeAllViews();
			if (!v6DnsList.isEmpty()) {
				// Add "System:" label
				TextView label = new TextView(this);
				label.setText(getString(R.string.system_prefix));
				label.setTextSize(11);
				label.setTextColor(0xFF808080);
				LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
				labelParams.gravity = android.view.Gravity.CENTER_VERTICAL;
				linearlayout_system_dns_v6_container.addView(label, labelParams);

				for (String dns : v6DnsList) {
					Button dnsButton = createDnsButton(dns, edittext_dns_ipv6);

					// Enable/disable based on VPN state
					boolean editable = !prefs.getEnable();
					dnsButton.setEnabled(editable && !prefs.getRemoteDns());

					LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
					params.setMargins(0, 0, 8, 0);
					params.gravity = android.view.Gravity.CENTER_VERTICAL;
					linearlayout_system_dns_v6_container.addView(dnsButton, params);
				}
			}
		} catch (Exception e) {
			// Silently ignore errors
		}
	}

	/**
	 * Create a styled DNS button that looks like a chip/tag
	 */
	private Button createDnsButton(final String dns, final EditText targetEditText) {
		Button dnsButton = new Button(this);
		dnsButton.setText(dns);
		dnsButton.setTextSize(11);
		dnsButton.setTextColor(0xFF2196F3); // Blue text color

		// Remove default button styling
		dnsButton.setBackgroundColor(0x00000000); // Transparent background
		dnsButton.setPadding(12, 4, 12, 4);
		dnsButton.setMinimumHeight(0);
		dnsButton.setMinHeight(0);
		dnsButton.setMinimumWidth(0);

		// Add rounded border background
		android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
		background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
		background.setCornerRadius(12); // Rounded corners
		background.setStroke(2, 0xFF2196F3); // Blue border
		background.setColor(0x00000000); // Transparent fill

		// Create state list drawable for pressed state
		android.graphics.drawable.StateListDrawable stateList = new android.graphics.drawable.StateListDrawable();

		// Pressed state background
		android.graphics.drawable.GradientDrawable pressedBackground = new android.graphics.drawable.GradientDrawable();
		pressedBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
		pressedBackground.setCornerRadius(12);
		pressedBackground.setStroke(2, 0xFF2196F3);
		pressedBackground.setColor(0x202196F3); // Light blue fill when pressed

		// Disabled state background
		android.graphics.drawable.GradientDrawable disabledBackground = new android.graphics.drawable.GradientDrawable();
		disabledBackground.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
		disabledBackground.setCornerRadius(12);
		disabledBackground.setStroke(2, 0xFF808080); // Gray border when disabled
		disabledBackground.setColor(0x00000000);

		stateList.addState(new int[]{android.R.attr.state_pressed}, pressedBackground);
		stateList.addState(new int[]{-android.R.attr.state_enabled}, disabledBackground);
		stateList.addState(new int[]{}, background);

		dnsButton.setBackground(stateList);

		// Set click listener
		dnsButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				targetEditText.setText(dns);
			}
		});

		// Add state list for text color (gray when disabled)
		android.content.res.ColorStateList textColorStateList = new android.content.res.ColorStateList(
			new int[][]{
				new int[]{-android.R.attr.state_enabled}, // disabled
				new int[]{} // default
			},
			new int[]{
				0xFF808080, // gray when disabled
				0xFF2196F3  // blue when enabled
			}
		);
		dnsButton.setTextColor(textColorStateList);

		return dnsButton;
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
		prefs.setBypassLan(checkbox_bypass_lan.isChecked());
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

		prefs.setChnroutesEnabled(checkbox_chnroutes_enabled.isChecked());
		prefs.setAclEnabled(checkbox_acl_enabled.isChecked());

		// DNS Split Tunnel preferences
		prefs.setDnsSplitTunnelEnabled(checkbox_dns_split_tunnel_enable.isChecked());
		saveDnsEntries();

		// DNS Forwarder preferences
		prefs.setDnsForwarderEnabled(checkbox_dns_forwarder_enabled.isChecked());
		prefs.setDnsVirtualIp4(edittext_dns_virtual_ip4.getText().toString());
		prefs.setDnsVirtualIp6(edittext_dns_virtual_ip6.getText().toString());
		prefs.setDnsTargetIp4(edittext_dns_target_ip4.getText().toString());
		prefs.setDnsTargetIp6(edittext_dns_target_ip6.getText().toString());

		// Mapped DNS preferences
		prefs.setMapdnsAddress(edittext_mapdns_address.getText().toString());
		prefs.setMapdnsAddress6(edittext_mapdns_address6.getText().toString());
		prefs.setMapdnsPort(Integer.parseInt(edittext_mapdns_port.getText().toString()));
		prefs.setMapdnsNetwork(edittext_mapdns_network.getText().toString());
		prefs.setMapdnsNetmask(edittext_mapdns_netmask.getText().toString());
		prefs.setMapdnsNetwork6(edittext_mapdns_network6.getText().toString());
		prefs.setMapdnsPrefixlen(Integer.parseInt(edittext_mapdns_prefixlen.getText().toString()));
		prefs.setMapdnsCacheSize(Integer.parseInt(edittext_mapdns_cache_size.getText().toString()));

		// DNS Latency Optimize preferences
		prefs.setDnsLatencyOptimizeEnabled(checkbox_dns_latency_optimize_enabled.isChecked());
		prefs.setDnsLatencyOptimizeTimeout(Integer.parseInt(edittext_dns_latency_optimize_timeout.getText().toString()));

		// Smart Proxy preferences
		prefs.setSmartProxyEnabled(checkbox_smart_proxy_enabled.isChecked());
		prefs.setSmartProxyTimeout(Integer.parseInt(edittext_smart_proxy_timeout.getText().toString()));
		prefs.setSmartProxyBlockedIpExpiry(Integer.parseInt(edittext_smart_proxy_blocked_ip_expiry.getText().toString()));
		saveProbePortEntries();
	}

	/**
	 * Load chnroutes content from cache directory file
	 */
	private void loadChnroutesContent() {
		File chnroutesFile = new File(getCacheDir(), "chnroutes.txt");
		if (!chnroutesFile.exists()) {
			// File doesn't exist, try to extract from assets
			extractChnroutesFromAssets();
			return;
		}
		StringBuilder content = new StringBuilder();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(chnroutesFile), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
			reader.close();
			edittext_chnroutes_content.setText(content.toString());
		} catch (IOException e) {
			edittext_chnroutes_content.setText(getString(R.string.unable_to_read_file, e.getMessage()));
		}
	}

	/**
	 * Save chnroutes content to cache directory file
	 */
	private void saveChnroutesContent() {
		File chnroutesFile = new File(getCacheDir(), "chnroutes.txt");
		try {
			OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(chnroutesFile), "UTF-8");
			writer.write(edittext_chnroutes_content.getText().toString());
			writer.close();
			Toast.makeText(this, getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Extract chnroutes.txt from APK assets
	 */
	private void extractChnroutesFromAssets() {
		AssetManager assetManager = getAssets();
		File chnroutesFile = new File(getCacheDir(), "chnroutes.txt");
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("chnroutes.txt"), "UTF-8"));
			OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(chnroutesFile), "UTF-8");
			String line;
			while ((line = reader.readLine()) != null) {
				writer.write(line);
				writer.write("\n");
			}
			reader.close();
			writer.close();
			loadChnroutesContent();
			Toast.makeText(this, getString(R.string.extracted_successfully), Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, getString(R.string.extract_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Copy file from URI to chnroutes.txt
	 */
	private void copyFileFromUri(Uri uri) throws IOException {
		File chnroutesFile = new File(getCacheDir(), "chnroutes.txt");
		BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
		OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(chnroutesFile), "UTF-8");
		String line;
		while ((line = reader.readLine()) != null) {
			writer.write(line);
			writer.write("\n");
		}
		reader.close();
		writer.close();
		loadChnroutesContent();
		Toast.makeText(this, getString(R.string.uploaded_successfully), Toast.LENGTH_SHORT).show();
	}

	/**
	 * Load ACL content from cache directory file
	 */
	private void loadAclContent() {
		File aclFile = new File(getCacheDir(), "acl.txt");
		if (!aclFile.exists()) {
			// File doesn't exist, try to extract from assets
			extractAclFromAssets();
			return;
		}
		StringBuilder content = new StringBuilder();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(aclFile), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
			reader.close();
			edittext_acl_content.setText(content.toString());
		} catch (IOException e) {
			edittext_acl_content.setText(getString(R.string.unable_to_read_file, e.getMessage()));
		}
	}

	/**
	 * Save ACL content to cache directory file
	 */
	private void saveAclContent() {
		File aclFile = new File(getCacheDir(), "acl.txt");
		try {
			OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(aclFile), "UTF-8");
			writer.write(edittext_acl_content.getText().toString());
			writer.close();
			Toast.makeText(this, getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Extract acl.txt from APK assets
	 */
	private void extractAclFromAssets() {
		AssetManager assetManager = getAssets();
		File aclFile = new File(getCacheDir(), "acl.txt");
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("acl.txt"), "UTF-8"));
			OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(aclFile), "UTF-8");
			String line;
			while ((line = reader.readLine()) != null) {
				writer.write(line);
				writer.write("\n");
			}
			reader.close();
			writer.close();
			loadAclContent();
			Toast.makeText(this, getString(R.string.extracted_successfully), Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this, getString(R.string.extract_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Copy file from URI to acl.txt
	 */
	private void copyFileFromUriForAcl(Uri uri) throws IOException {
		File aclFile = new File(getCacheDir(), "acl.txt");
		BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
		OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(aclFile), "UTF-8");
		String line;
		while ((line = reader.readLine()) != null) {
			writer.write(line);
			writer.write("\n");
		}
		reader.close();
		writer.close();
		loadAclContent();
		Toast.makeText(this, getString(R.string.uploaded_successfully), Toast.LENGTH_SHORT).show();
	}

	/**
	 * Scroll the selected tab into view
	 */
	private void scrollTabToView(String tabId) {
		if (tabScroller == null) return;

		tabWidget = tabHost.getTabWidget();
		if (tabWidget == null) return;

		int currentTab = tabHost.getCurrentTab();
		if (currentTab >= 0 && currentTab < tabWidget.getTabCount()) {
			View tabView = tabWidget.getChildTabViewAt(currentTab);
			if (tabView != null) {
				// Scroll to position the selected tab at the left
				int scrollX = tabView.getLeft();
				tabScroller.smoothScrollTo(scrollX, 0);
			}
		}
	}

	/**
	 * Load DNS entries from preferences into the UI
	 */
	private void loadDnsEntries() {
		dns_entries_container.removeAllViews();
		dns_entry_edit_texts.clear();

		java.util.List<String> servers = prefs.getDnsForeignServersList();
		for (String server : servers) {
			addDnsEntryView(server, false);
		}
	}

	/**
	 * Save DNS entries from UI to preferences
	 */
	private void saveDnsEntries() {
		java.util.List<String> servers = new java.util.ArrayList<String>();
		for (EditText edit : dns_entry_edit_texts) {
			String value = edit.getText().toString().trim();
			if (!value.isEmpty()) {
				servers.add(value);
			}
		}
		prefs.setDnsForeignServersList(servers);
	}

	/**
	 * Add a new DNS entry view to the container
	 * @param value Initial value for the entry
	 * @param focus Whether to focus the new entry
	 */
	private void addDnsEntryView(String value, boolean focus) {
		LinearLayout entryRow = new LinearLayout(this);
		entryRow.setOrientation(LinearLayout.HORIZONTAL);
		entryRow.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.FILL_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT));

		LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
			0,
			LinearLayout.LayoutParams.WRAP_CONTENT,
			1.0f);

		EditText editText = new EditText(this);
		editText.setLayoutParams(editParams);
		editText.setText(value);
		editText.setHint("1.1.1.1 or 2606:4700:4700::1111");
		editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
		editText.setSingleLine(true);

		boolean editable = !prefs.getEnable();
		editText.setEnabled(editable);

		Button removeButton = new Button(this);
		LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
		buttonParams.setMargins(8, 0, 0, 0);
		removeButton.setLayoutParams(buttonParams);
		removeButton.setText(getString(R.string.button_remove));
		removeButton.setEnabled(editable);

		final EditText thisEdit = editText;
		final View thisRow = entryRow;
		removeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dns_entries_container.removeView(thisRow);
				dns_entry_edit_texts.remove(thisEdit);
			}
		});

		entryRow.addView(editText);
		entryRow.addView(removeButton);
		dns_entries_container.addView(entryRow);
		dns_entry_edit_texts.add(editText);

		if (focus) {
			editText.requestFocus();
		}
	}

	/**
	 * Load probe port entries from preferences into the UI
	 */
	private void loadProbePortEntries() {
		probe_ports_container.removeAllViews();
		probe_port_edit_texts.clear();

		java.util.List<Integer> ports = prefs.getSmartProxyProbePortsList();
		for (int port : ports) {
			addProbePortEntryView(port, false);
		}
	}

	/**
	 * Save probe port entries from UI to preferences
	 */
	private void saveProbePortEntries() {
		java.util.List<Integer> ports = new java.util.ArrayList<Integer>();
		for (EditText edit : probe_port_edit_texts) {
			String value = edit.getText().toString().trim();
			if (!value.isEmpty()) {
				try {
					int port = Integer.parseInt(value);
					if (port > 0 && port <= 65535) {
						ports.add(port);
					}
				} catch (NumberFormatException e) {
					// Skip invalid port numbers
				}
			}
		}
		prefs.setSmartProxyProbePortsList(ports);
	}

	/**
	 * Add a new probe port entry view to the container
	 * @param port Initial port value
	 * @param focus Whether to focus the new entry
	 */
	private void addProbePortEntryView(int port, boolean focus) {
		LinearLayout entryRow = new LinearLayout(this);
		entryRow.setOrientation(LinearLayout.HORIZONTAL);
		entryRow.setLayoutParams(new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.FILL_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT));

		LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
			0,
			LinearLayout.LayoutParams.WRAP_CONTENT,
			1.0f);

		EditText editText = new EditText(this);
		editText.setLayoutParams(editParams);
		editText.setText(port > 0 ? Integer.toString(port) : "");
		editText.setHint("80");
		editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
		editText.setSingleLine(true);

		boolean editable = !prefs.getEnable();
		editText.setEnabled(editable);

		Button removeButton = new Button(this);
		LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
		buttonParams.setMargins(8, 0, 0, 0);
		removeButton.setLayoutParams(buttonParams);
		removeButton.setText(getString(R.string.button_remove));
		removeButton.setEnabled(editable);

		final EditText thisEdit = editText;
		final View thisRow = entryRow;
		removeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				probe_ports_container.removeView(thisRow);
				probe_port_edit_texts.remove(thisEdit);
			}
		});

		entryRow.addView(editText);
		entryRow.addView(removeButton);
		probe_ports_container.addView(entryRow);
		probe_port_edit_texts.add(editText);

		if (focus) {
			editText.requestFocus();
		}
	}

	private void togglePasswordVisibility(EditText editText, TextView toggleButton) {
		int currentInputType = editText.getInputType();
		if (currentInputType == (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
			// Currently hidden, show password
			editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
			toggleButton.setText(R.string.hide_password);
		} else {
			// Currently visible, hide password
			editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
			toggleButton.setText(R.string.show_password);
		}
		// Move cursor to the end
		editText.setSelection(editText.getText().length());
	}
}
