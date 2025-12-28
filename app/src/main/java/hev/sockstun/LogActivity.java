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
							textview_log.setText(logs);
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
}
