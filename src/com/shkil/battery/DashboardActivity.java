package com.shkil.battery;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

public class DashboardActivity extends Activity implements OnClickListener {

	static final int DASHBOARD_DIALOG_ID = 1;

	Dialog dialog;
	TextView batteryLevelValue;
	TextView batteryStatusValue;

	final BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
		private int lastRawLevel;
		private int lastStatus;
		private int lastPlugged;
		@Override
		public void onReceive(Context context, Intent intent) {
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			if (level != lastRawLevel) {
				lastRawLevel = level;
				int percent = level * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				batteryLevelValue.setText(percent + "%");
			}
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
			int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
			if (status != lastStatus || plugged != lastPlugged) {
				lastStatus = status;
				lastPlugged = plugged;
				int statusStringId;
				switch (status) {
					case BatteryManager.BATTERY_STATUS_CHARGING:
						statusStringId = R.string.statusCharging;
						break;
					case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
						statusStringId = R.string.statusNotCharging;
						break;
					case BatteryManager.BATTERY_STATUS_FULL:
						statusStringId = R.string.statusFullyCharged;
						break;
					case BatteryManager.BATTERY_STATUS_DISCHARGING:
						statusStringId = R.string.statusDischarging;
						break;
					default:
						statusStringId = R.string.statusUnknown;
				}
				batteryStatusValue.setText(statusStringId);
				if (plugged == BatteryManager.BATTERY_PLUGGED_USB) {
					batteryStatusValue.append(getString(R.string.usb_suffix));
				}
			}
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (settings.getBoolean(Settings.STARTED, true) && !BatteryNotifierService.isRunning(this)) {
			new Thread() {
				@Override
				public void run() {
					BatteryNotifierService.start(DashboardActivity.this);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(DashboardActivity.this, R.string.service_started, Toast.LENGTH_SHORT).show();
						}
					});
				}
			}.start();
		}
		showDialog(DASHBOARD_DIALOG_ID);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		dialog = new Dialog(this) {
			@Override
			public boolean onCreateOptionsMenu(Menu menu) {
				menu.add("Battery use").setIntent(
						new Intent().setClassName("com.android.settings", "com.android.settings.fuelgauge.PowerUsageSummary")
				);
				menu.add("Battery info").setIntent(
						new Intent().setClassName("com.android.settings", "com.android.settings.BatteryInfo")
				);
				return true;
			}
			@Override
			protected void onStart() {
				super.onStart();
				registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			}
			@Override
			protected void onStop() {
				unregisterReceiver(batteryInfoReceiver);
				super.onStop();
				DashboardActivity.this.finish();
			}
			@Override
			public void onBackPressed() {
				super.onBackPressed();
			}
		};
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.dashboard);
		batteryLevelValue = (TextView) dialog.findViewById(R.id.batteryLevelValue);
		batteryStatusValue = (TextView) dialog.findViewById(R.id.batteryStatusValue);
		dialog.findViewById(R.id.snoozeAlertsButton).setOnClickListener(this);
		dialog.findViewById(R.id.unsnoozeAlertsButton).setOnClickListener(this);
		dialog.findViewById(R.id.settingsButton).setOnClickListener(this);
		dialog.findViewById(R.id.snoozeSetButton).setOnClickListener(this);
		dialog.findViewById(R.id.snoozeCancelButton).setOnClickListener(this);
		return dialog;
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.snoozeAlertsButton: {
				View snoozedText = dialog.findViewById(R.id.snoozedText);
				View unsnoozeAlertsButton = dialog.findViewById(R.id.unsnoozeAlertsButton);
//				dialog.findViewById(R.id.snoozeAlertsButton).setVisibility(View.INVISIBLE);
//				dialog.findViewById(R.id.settingsButton).setVisibility(View.INVISIBLE);
//				unsnoozeAlertsButton.setVisibility(View.VISIBLE);
//				snoozedText.setVisibility(View.VISIBLE);
				View snoozeLayout = dialog.findViewById(R.id.snoozeLayout);
				snoozeLayout.setVisibility(View.VISIBLE);
//				dialog.findViewById(R.id.snoozeDue).setVisibility(View.VISIBLE);
				Toast.makeText(this, "sss", Toast.LENGTH_LONG).show();
				break;
			}
			case R.id.unsnoozeAlertsButton: {
				View snoozeAlertsButton = dialog.findViewById(R.id.snoozeAlertsButton);
				View snoozedText = dialog.findViewById(R.id.snoozedText);
				View unsnoozeAlertsButton = dialog.findViewById(R.id.unsnoozeAlertsButton);
				unsnoozeAlertsButton.setVisibility(View.INVISIBLE);
				snoozedText.setVisibility(View.INVISIBLE);
				snoozeAlertsButton.setVisibility(View.VISIBLE);
				Animation hyperspaceJump = AnimationUtils.loadAnimation(this, R.anim.unsnooze);
				snoozeAlertsButton.startAnimation(hyperspaceJump);
				Toast.makeText(this, "Alerts on", Toast.LENGTH_SHORT);
				break;
			}
			case R.id.settingsButton:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.snoozeSetButton:
				dialog.findViewById(R.id.snoozeAlertsButton).setVisibility(View.INVISIBLE);
				dialog.findViewById(R.id.snoozedText).setVisibility(View.VISIBLE);
				dialog.findViewById(R.id.unsnoozeAlertsButton).setVisibility(View.VISIBLE);
				dialog.findViewById(R.id.snoozeLayout).setVisibility(View.GONE);
				break;
			case R.id.snoozeCancelButton:
				dialog.findViewById(R.id.snoozeLayout).setVisibility(View.GONE);
				break;
		}
	}

}
