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

public class LogActivity extends Activity implements View.OnClickListener {
	private static final int MAX_LOG_LINES = 500;
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
			textview_log.setText("");
			// Clear native logs by calling with 0 lines
			TProxyService.getLogs(0);
		}
	}

	private void refreshLogs() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				final String logs = TProxyService.getLogs(MAX_LOG_LINES);
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
							textview_log.setText("No logs available.");
						}
					}
				});
			}
		}).start();
	}
}
