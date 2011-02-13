package com.shkil.battery;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

class DashboardDialog extends Dialog implements OnClickListener {

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
					batteryStatusValue.append(getContext().getString(R.string.usb_suffix));
				}
			}
		}
	};

	public DashboardDialog(Context context) {
		super(context);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);
		findViewById(R.id.snoozeAlertsButton).setOnClickListener(this);
		findViewById(R.id.unsnoozeAlertsButton).setOnClickListener(this);
		findViewById(R.id.settingsButton).setOnClickListener(this);
		findViewById(R.id.snoozeSetButton).setOnClickListener(this);
		findViewById(R.id.snoozeCancelButton).setOnClickListener(this);
		batteryLevelValue = (TextView) findViewById(R.id.batteryLevelValue);
		batteryStatusValue = (TextView) findViewById(R.id.batteryStatusValue);
	}

	@Override
	protected void onStart() {
		super.onStart();
		getContext().registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}

	@Override
	protected void onStop() {
		getContext().unregisterReceiver(batteryInfoReceiver);
		super.onStop();
		getOwnerActivity().finish();
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.settingsButton: {
				Context context = getContext();
				context.startActivity(new Intent(context, SettingsActivity.class));
				break;
			}
			case R.id.snoozeAlertsButton:
				findViewById(R.id.snoozeLayout).setVisibility(View.VISIBLE);
				findViewById(R.id.mainLayout).setVisibility(View.INVISIBLE);
				break;
			case R.id.unsnoozeAlertsButton: {
				View snoozeAlertsButton = findViewById(R.id.snoozeAlertsButton);
				View snoozedText = findViewById(R.id.snoozedText);
				View unsnoozeAlertsButton = findViewById(R.id.unsnoozeAlertsButton);
				snoozedText.setVisibility(View.INVISIBLE);
				snoozeAlertsButton.setVisibility(View.VISIBLE);
				unsnoozeAlertsButton.setVisibility(View.INVISIBLE);
				Animation hyperspaceJump = AnimationUtils.loadAnimation(getContext(), R.anim.unsnooze);
				snoozeAlertsButton.startAnimation(hyperspaceJump);
				break;
			}
			case R.id.snoozeSetButton:
				findViewById(R.id.snoozeAlertsButton).setVisibility(View.INVISIBLE);
				findViewById(R.id.snoozedText).setVisibility(View.VISIBLE);
				findViewById(R.id.unsnoozeAlertsButton).setVisibility(View.VISIBLE);
				findViewById(R.id.snoozeLayout).setVisibility(View.GONE);
				findViewById(R.id.mainLayout).setVisibility(View.VISIBLE);
				break;
			case R.id.snoozeCancelButton:
				findViewById(R.id.snoozeLayout).setVisibility(View.GONE);
				findViewById(R.id.mainLayout).setVisibility(View.VISIBLE);
				break;
		}
	}

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

}