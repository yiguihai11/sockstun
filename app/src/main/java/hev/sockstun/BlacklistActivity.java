/*
 ============================================================================
 Name        : BlacklistActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : Blacklist Viewer Activity
 ============================================================================
 */

package hev.sockstun;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class BlacklistActivity extends Activity implements View.OnClickListener {

	private TextView textview_count;
	private TextView textview_msg;
	private ListView listview_blacklist;
	private Button button_refresh;
	private BlacklistAdapter adapter;
	private Preferences prefs;

	private static class BlacklistEntry {
		String type;
		String value;
		long expiry;
		long hits;

		BlacklistEntry(String type, String value, long expiry, long hits) {
			this.type = type;
			this.value = value;
			this.expiry = expiry;
			this.hits = hits;
		}
	}

	private class BlacklistAdapter extends ArrayAdapter<BlacklistEntry> {
		BlacklistAdapter(Activity context, List<BlacklistEntry> entries) {
			super(context, R.layout.blacklist_item, entries);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(R.layout.blacklist_item, parent, false);
			}

			BlacklistEntry entry = getItem(position);
			TextView typeView = (TextView) convertView.findViewById(R.id.blacklist_item_type);
			TextView valueView = (TextView) convertView.findViewById(R.id.blacklist_item_value);
			TextView expiryView = (TextView) convertView.findViewById(R.id.blacklist_item_expiry);
			TextView hitsView = (TextView) convertView.findViewById(R.id.blacklist_item_hits);

			typeView.setText(entry.type);
			valueView.setText(entry.value);
			expiryView.setText(getContext().getString(R.string.blacklist_expiry, entry.expiry));
			hitsView.setText(getContext().getString(R.string.blacklist_hits, entry.hits));

			return convertView;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.blacklist);
		setTitle(R.string.blacklist);

		prefs = new Preferences(this);
		textview_count = (TextView) findViewById(R.id.blacklist_count);
		textview_msg = (TextView) findViewById(R.id.vpn_not_running_msg);
		listview_blacklist = (ListView) findViewById(R.id.blacklist_list);
		button_refresh = (Button) findViewById(R.id.refresh_blacklist);

		button_refresh.setOnClickListener(this);
		listview_blacklist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				BlacklistEntry entry = (BlacklistEntry) parent.getAdapter().getItem(position);
				ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("Blacklist Value", entry.value);
				clipboard.setPrimaryClip(clip);
				String msg = getString(R.string.copied_to_clipboard, entry.value);
				Toast.makeText(BlacklistActivity.this, msg, Toast.LENGTH_SHORT).show();
			}
		});

		refreshBlacklist();
	}

	@Override
	public void onClick(View v) {
		if (v == button_refresh) {
			refreshBlacklist();
		}
	}

	private void refreshBlacklist() {
		if (!prefs.getEnable()) {
			textview_count.setText(getString(R.string.blacklist_count, 0));
			textview_msg.setVisibility(View.VISIBLE);
			listview_blacklist.setVisibility(View.GONE);
			return;
		}

		textview_msg.setVisibility(View.GONE);
		listview_blacklist.setVisibility(View.VISIBLE);

		String[] data = TProxyService.getBlacklist();
		List<BlacklistEntry> entries = new ArrayList<BlacklistEntry>();

		if (data != null) {
			for (String line : data) {
				LogActivity.d(this, "BlacklistRawData", "Raw Line: " + line);
				String[] parts = line.split("\\|");
				if (parts.length >= 4) {
					try {
						entries.add(new BlacklistEntry(
							parts[0],
							parts[1],
							Long.parseLong(parts[2]),
							Long.parseLong(parts[3])
						));
					} catch (NumberFormatException e) {
						// Ignore invalid entries
					}
				}
			}
		}

		textview_count.setText(getString(R.string.blacklist_count, entries.size()));
		adapter = new BlacklistAdapter(this, entries);
		listview_blacklist.setAdapter(adapter);
	}
}
