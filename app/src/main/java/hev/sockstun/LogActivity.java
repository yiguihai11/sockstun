/*
 ============================================================================
 Name        : LogActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : Log Viewer Activity
 ============================================================================
 */

package hev.sockstun;

import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;

public class LogActivity extends Activity implements View.OnClickListener {
	private static final int MAX_LOG_SIZE = 100 * 1024; // 100KB max
	private TextView textview_log;
	private ScrollView scrollview_log;
	private Button button_refresh;
	private Button button_clear;
	private Handler handler;
	private Runnable refreshRunnable;

	// Log colors
	private static final int COLOR_DEBUG = 0xFFAAAAAA; // Gray
	private static final int COLOR_INFO = 0xFF00FF00;  // Green
	private static final int COLOR_WARN = 0xFFFFFF00;  // Yellow
	private static final int COLOR_ERROR = 0xFFFF0000; // Red
	private static final int COLOR_DEFAULT = 0xFF00FF00; // Green (default)

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log);

		textview_log = (TextView) findViewById(R.id.log_text);
		scrollview_log = (ScrollView) findViewById(R.id.log_scroll);
		button_refresh = (Button) findViewById(R.id.log_refresh);
		button_clear = (Button) findViewById(R.id.log_clear);

		button_refresh.setOnClickListener(this);
		button_clear.setOnClickListener(this);

		handler = new Handler(Looper.getMainLooper());
		refreshRunnable = new Runnable() {
			@Override
			public void run() {
				refreshLogs();
			}
		};

		// Initial load
		refreshLogs();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (handler != null) {
			handler.removeCallbacks(refreshRunnable);
		}
	}

	@Override
	public void onClick(View view) {
		if (view == button_refresh) {
			refreshLogs();
		} else if (view == button_clear) {
			clearLogs();
		}
	}

	private void refreshLogs() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				final String logs = readLogsFromFile();
				handler.post(new Runnable() {
					@Override
					public void run() {
						if (logs != null && !logs.isEmpty()) {
							textview_log.setText(colorizeLog(logs));
							// Scroll to bottom
							scrollview_log.post(new Runnable() {
								@Override
								public void run() {
									scrollview_log.fullScroll(ScrollView.FOCUS_DOWN);
								}
							});
						} else {
							textview_log.setText("No logs available. Make sure VPN is running.");
						}
					}
				});
			}
		}).start();
	}

	private void clearLogs() {
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
							textview_log.setText("Logs cleared.");
						}
					});
				} catch (Exception e) {
					handler.post(new Runnable() {
						@Override
						public void run() {
							textview_log.setText("Failed to clear logs: " + e.getMessage());
						}
					});
				}
			}
		}).start();
	}

	private String readLogsFromFile() {
		File logFile = new File(getCacheDir(), "tunnel.log");
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
			int color = COLOR_DEFAULT;

			switch (level) {
			case 'D':
				color = COLOR_DEBUG;
				break;
			case 'I':
				color = COLOR_INFO;
				break;
			case 'W':
				color = COLOR_WARN;
				break;
			case 'E':
				color = COLOR_ERROR;
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
}
