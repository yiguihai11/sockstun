/*
 ============================================================================
 Name        : LogActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : Log Viewer Activity with Java and Native logs
 ============================================================================
 */

package hev.sockstun;

import android.os.Bundle;
import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TabHost;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogActivity extends TabActivity implements View.OnClickListener {
	private static final int MAX_LOG_SIZE = 100 * 1024; // 100KB max

	// Java log UI elements
	private TextView textview_java_log;
	private ScrollView scrollview_java_log;
	private Button button_java_refresh;
	private Button button_java_clear;
	private EditText edittext_java_log_search;
	private String originalJavaLogs; // Store original logs for filtering

	// Native log UI elements
	private TextView textview_native_log;
	private ScrollView scrollview_native_log;
	private Button button_native_refresh;
	private Button button_native_clear;
	private EditText edittext_native_log_search;
	private String originalNativeLogs; // Store original logs for filtering

	private TabHost tabHost;
	private Handler handler;

	// Log colors for dark theme
	private static final int COLOR_DEBUG_DARK = 0xFFAAAAAA; // Gray
	private static final int COLOR_INFO_DARK = 0xFF00FF00;  // Green
	private static final int COLOR_WARN_DARK = 0xFFFFFF00;  // Yellow
	private static final int COLOR_ERROR_DARK = 0xFFFF0000; // Red
	private static final int COLOR_DEFAULT_DARK = 0xFF00FF00; // Green

	// Log colors for light theme
	private static final int COLOR_DEBUG_LIGHT = 0xFF808080; // Gray
	private static final int COLOR_INFO_LIGHT = 0xFF008000;  // Dark Green
	private static final int COLOR_WARN_LIGHT = 0xFFB8860B;  // Dark Goldenrod
	private static final int COLOR_ERROR_LIGHT = 0xFFCC0000; // Dark Red
	private static final int COLOR_DEFAULT_LIGHT = 0xFF008000; // Dark Green

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log);

		tabHost = getTabHost();

		// Setup Java log tab
		tabHost.addTab(tabHost.newTabSpec("java_log")
			.setIndicator("Java Log")
			.setContent(R.id.tab_java_log));

		// Setup Native log tab
		tabHost.addTab(tabHost.newTabSpec("native_log")
			.setIndicator("Native Log")
			.setContent(R.id.tab_native_log));

		// Java log UI elements
		textview_java_log = (TextView) findViewById(R.id.java_log_text);
		scrollview_java_log = (ScrollView) findViewById(R.id.java_log_scroll);
		button_java_refresh = (Button) findViewById(R.id.java_log_refresh);
		button_java_clear = (Button) findViewById(R.id.java_log_clear);
		edittext_java_log_search = (EditText) findViewById(R.id.java_log_search);

		// Native log UI elements
		textview_native_log = (TextView) findViewById(R.id.native_log_text);
		scrollview_native_log = (ScrollView) findViewById(R.id.native_log_scroll);
		button_native_refresh = (Button) findViewById(R.id.native_log_refresh);
		button_native_clear = (Button) findViewById(R.id.native_log_clear);
		edittext_native_log_search = (EditText) findViewById(R.id.native_log_search);

		// Setup click listeners
		button_java_refresh.setOnClickListener(this);
		button_java_clear.setOnClickListener(this);
		button_native_refresh.setOnClickListener(this);
		button_native_clear.setOnClickListener(this);

		// Setup search listeners
		setupSearchListeners();

		handler = new Handler(Looper.getMainLooper());

		// Initial load
		refreshJavaLogs();
		refreshNativeLogs();
	}

	@Override
	public void onClick(View view) {
		if (view == button_java_refresh) {
			refreshJavaLogs();
		} else if (view == button_java_clear) {
			clearJavaLogs();
		} else if (view == button_native_refresh) {
			refreshNativeLogs();
		} else if (view == button_native_clear) {
			clearNativeLogs();
		}
	}

	private void refreshJavaLogs() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				final String logs = readLogsFromFile("java.log");
				handler.post(new Runnable() {
					@Override
					public void run() {
						if (logs != null && !logs.isEmpty()) {
							originalJavaLogs = logs;
							applyJavaLogFilter();
						} else {
							originalJavaLogs = "";
							textview_java_log.setText("No Java logs available yet.");
						}
					}
				});
			}
		}).start();
	}

	private void refreshNativeLogs() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				final String config = readConfigFile();
				final String logs = readLogsFromFile("tunnel.log");
				handler.post(new Runnable() {
					@Override
					public void run() {
						StringBuilder display = new StringBuilder();

						// Show config at the top
						if (config != null && !config.isEmpty()) {
							display.append("========== tproxy.conf ==========\n");
							display.append(config);
							display.append("\n========== End of Config ==========\n\n");
						}

						// Show logs below
						if (logs != null && !logs.isEmpty()) {
							display.append("========== tunnel.log ==========\n");
							display.append(logs);
							display.append("\n========== End of Logs ==========");
							originalNativeLogs = display.toString();
							applyNativeLogFilter();
						} else if (config != null && !config.isEmpty()) {
							display.append("========== tunnel.log ==========\n");
							display.append("No logs available. Make sure VPN is running.");
							originalNativeLogs = display.toString();
							applyNativeLogFilter();
						} else {
							originalNativeLogs = "";
							textview_native_log.setText("No logs or config available. Make sure VPN is running.");
						}
					}
				});
			}
		}).start();
	}

	private void clearJavaLogs() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					File logFile = new File(getCacheDir(), "java.log");
					if (logFile.exists()) {
						logFile.delete();
					}
					handler.post(new Runnable() {
						@Override
						public void run() {
							textview_java_log.setText("Java logs cleared.");
						}
					});
				} catch (Exception e) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							textview_java_log.setText("Failed to clear logs: " + e.getMessage());
						}
					});
				}
			}
		}).start();
	}

	private void clearNativeLogs() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					File logFile = new File(getCacheDir(), "tunnel.log");
					if (logFile.exists()) {
						logFile.delete();
					}
					handler.post(new Runnable() {
						@Override
						public void run() {
							textview_native_log.setText("Native logs cleared.");
						}
					});
				} catch (Exception e) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							textview_native_log.setText("Failed to clear logs: " + e.getMessage());
						}
					});
				}
			}
		}).start();
	}

	private void setupSearchListeners() {
		// Java log search listener
		edittext_java_log_search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				applyJavaLogFilter();
			}

			@Override
			public void afterTextChanged(android.text.Editable s) {}
		});

		// Native log search listener
		edittext_native_log_search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				applyNativeLogFilter();
			}

			@Override
			public void afterTextChanged(android.text.Editable s) {}
		});
	}

	private void applyJavaLogFilter() {
		String filter = edittext_java_log_search.getText().toString().trim();
		String filteredLogs = filterLogs(originalJavaLogs, filter);
		if (filteredLogs != null && !filteredLogs.isEmpty()) {
			textview_java_log.setText(colorizeLog(filteredLogs));
		} else {
			textview_java_log.setText("No matching logs found.");
		}
		// Scroll to bottom
		scrollview_java_log.post(new Runnable() {
			@Override
			public void run() {
				scrollview_java_log.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});
	}

	private void applyNativeLogFilter() {
		String filter = edittext_native_log_search.getText().toString().trim();
		String filteredLogs = filterLogs(originalNativeLogs, filter);
		if (filteredLogs != null && !filteredLogs.isEmpty()) {
			textview_native_log.setText(colorizeLog(filteredLogs));
		} else {
			textview_native_log.setText("No matching logs found.");
		}
		// Scroll to bottom
		scrollview_native_log.post(new Runnable() {
			@Override
			public void run() {
				scrollview_native_log.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});
	}

	private String filterLogs(String originalLogs, String filter) {
		if (originalLogs == null || originalLogs.isEmpty()) {
			return originalLogs;
		}
		if (filter == null || filter.isEmpty()) {
			return originalLogs;
		}

		// Case-insensitive filtering
		String lowerFilter = filter.toLowerCase();
		String[] lines = originalLogs.split("\n");
		StringBuilder filtered = new StringBuilder();

		for (String line : lines) {
			if (line.toLowerCase().contains(lowerFilter)) {
				filtered.append(line).append("\n");
			}
		}

		return filtered.toString();
	}

	private String readConfigFile() {
		File configFile = new File(getCacheDir(), "tproxy.conf");
		if (!configFile.exists()) {
			return null;
		}

		try {
			StringBuilder sb = new StringBuilder();
			String line;
			BufferedReader reader = new BufferedReader(new FileReader(configFile));
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			reader.close();
			return sb.toString();
		} catch (Exception e) {
			return "Error reading config: " + e.getMessage();
		}
	}

	private String readLogsFromFile(String filename) {
		File logFile = new File(getCacheDir(), filename);
		if (!logFile.exists()) {
			return null;
		}

		try {
			long fileLength = logFile.length();
			if (fileLength <= 0) {
				return null;
			}

			// Read last MAX_LOG_SIZE bytes
			long startPos = 0;
			if (fileLength > MAX_LOG_SIZE) {
				startPos = fileLength - MAX_LOG_SIZE;
			}

			RandomAccessFile raf = new RandomAccessFile(logFile, "r");
			raf.seek(startPos);

			StringBuilder sb = new StringBuilder();
			String line;
			BufferedReader reader = new BufferedReader(new FileReader(raf.getFD()));
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			reader.close();
			raf.close();

			return sb.toString();
		} catch (Exception e) {
			return "Error reading logs: " + e.getMessage();
		}
	}

	private SpannableString colorizeLog(String log) {
		SpannableString spannable = new SpannableString(log);

		// Detect if light theme is being used
		boolean isLightTheme = (getResources().getConfiguration().uiMode &
			Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;

		// Choose colors based on theme
		int colorDebug = isLightTheme ? COLOR_DEBUG_LIGHT : COLOR_DEBUG_DARK;
		int colorInfo = isLightTheme ? COLOR_INFO_LIGHT : COLOR_INFO_DARK;
		int colorWarn = isLightTheme ? COLOR_WARN_LIGHT : COLOR_WARN_DARK;
		int colorError = isLightTheme ? COLOR_ERROR_LIGHT : COLOR_ERROR_DARK;
		int colorDefault = isLightTheme ? COLOR_DEFAULT_LIGHT : COLOR_DEFAULT_DARK;

		int start = 0;
		while (start < log.length()) {
			// Find log level marker [D], [I], [W], [E], [?]
			int levelStart = log.indexOf("[", start);
			if (levelStart == -1) break;

			int levelEnd = log.indexOf("]", levelStart);
			if (levelEnd == -1 || levelEnd - levelStart != 2) {
				start = levelStart + 1;
				continue;
			}

			char level = log.charAt(levelStart + 1);
			int color = colorDefault;

			switch (level) {
			case 'D':
				color = colorDebug;
				break;
			case 'I':
				color = colorInfo;
				break;
			case 'W':
				color = colorWarn;
				break;
			case 'E':
				color = colorError;
				break;
			default:
				continue;
			}

			// Color from the timestamp to end of line
			int lineStart = log.lastIndexOf("\n", levelStart - 1) + 1;
			int lineEnd = log.indexOf("\n", levelStart);
			if (lineEnd == -1) lineEnd = log.length();

			spannable.setSpan(new ForegroundColorSpan(color), lineStart,
					  lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

			start = lineEnd;
		}

		return spannable;
	}

	/**
	 * Static method to write Java log entries
	 * Can be called from TProxyService and other components
	 */
	public static void log(Context context, String level, String tag, String message) {
		if (context == null) return;

		try {
			File logFile = new File(context.getCacheDir(), "java.log");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
			String timestamp = sdf.format(new Date());
			String logLine = String.format("[%s] %s: %s\n", timestamp, tag, message);

			FileWriter writer = new FileWriter(logFile, true);
			writer.write(logLine);
			writer.close();
		} catch (IOException e) {
			// Silently fail if logging fails
		}
	}

	/**
	 * Convenience method for debug logs
	 */
	public static void d(Context context, String tag, String message) {
		log(context, "D", tag, message);
	}

	/**
	 * Convenience method for info logs
	 */
	public static void i(Context context, String tag, String message) {
		log(context, "I", tag, message);
	}

	/**
	 * Convenience method for warning logs
	 */
	public static void w(Context context, String tag, String message) {
		log(context, "W", tag, message);
	}

	/**
	 * Convenience method for error logs
	 */
	public static void e(Context context, String tag, String message) {
		log(context, "E", tag, message);
	}
}
