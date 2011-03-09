package com.shkil.battery;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

class DashboardDialog extends Dialog implements OnClickListener {

	private final class BatteryInfoReceiver extends BroadcastReceiver {
		private int lastRawLevel;
		private int lastStatus;
		private int lastPlugged = -1;
		@Override
		public void onReceive(final Context context, Intent intent) {
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			if (level != lastRawLevel) {
				lastRawLevel = level;
				int percent = level * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				batteryLevelValue.setText(percent + "%");
				int batteryLevelBgResource;
				if (percent <= lowBatteryLevel) {
					batteryLevelBgResource = R.drawable.low_level_bg;
				}
				else {
					batteryLevelBgResource = lowBatteryLevel > 0 ? R.drawable.normal_level_bg : 0;
				}
				batteryLevelValue.setBackgroundResource(batteryLevelBgResource);
			}
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
			int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
			boolean plugChanged = plugged != lastPlugged;
			if (status != lastStatus || plugChanged) {
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
					batteryStatusValue.append(context.getString(R.string.usb_suffix));
					if (status == BatteryManager.BATTERY_STATUS_FULL) {
						batteryStatusValue.setTextScaleX(fullyChargedStatusTextScaleX);
					}
					else {
						batteryStatusValue.setTextScaleX(1.0f);
					}
				}
				else {
					batteryStatusValue.setTextScaleX(1.0f);
				}
				if (plugChanged && BatteryNotifierService.isStarted()) {
					updatePluggedState(plugged);
				}
			}
		}
		public void reset() {
			lastRawLevel = lastStatus = 0;
			lastPlugged = -1;
		}
		public int getPluggedState() {
			return lastPlugged;
		}
	}

	static final String TAG = DashboardDialog.class.getSimpleName();

	SharedPreferences settings;
	int lowBatteryLevel;
	TextView batteryLevelValue;
	TextView batteryStatusValue;
	TextView unpluggedSinceValue;
	TextView unpluggedSinceLabel;
	TextView muteDurationView;
	TextView muteUntilView;
	long activityPausedAt;
	long muteDuration = 3600000;
	float fullyChargedStatusTextScaleX;

	final BatteryInfoReceiver batteryInfoReceiver = new BatteryInfoReceiver();

	public DashboardDialog(Context context) {
		super(context);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dashboard);
		batteryLevelValue = (TextView) findViewById(R.id.batteryLevelValue);
		batteryStatusValue = (TextView) findViewById(R.id.batteryStatusValue);
		unpluggedSinceLabel = (TextView) findViewById(R.id.unpluggedSinceLabel);
		unpluggedSinceValue = (TextView) findViewById(R.id.unpluggedSinceValue);
		muteDurationView = (TextView) findViewById(R.id.muteDuration);
		muteUntilView = (TextView) findViewById(R.id.muteUntilText);
		findViewById(R.id.muteAlertsButton).setOnClickListener(this);
		findViewById(R.id.unmuteAlertsButton).setOnClickListener(this);
		findViewById(R.id.settingsButton).setOnClickListener(this);
		findViewById(R.id.muteSetButton).setOnClickListener(this);
		findViewById(R.id.startServiceButton).setOnClickListener(this);
		findViewById(R.id.muteMinus15mButton).setOnClickListener(this);
		findViewById(R.id.muteMinus1hButton).setOnClickListener(this);
		findViewById(R.id.muteMinus12hButton).setOnClickListener(this);
		findViewById(R.id.mutePlus15mButton).setOnClickListener(this);
		findViewById(R.id.mutePlus1hButton).setOnClickListener(this);
		findViewById(R.id.mutePlus12hButton).setOnClickListener(this);
		fullyChargedStatusTextScaleX = getContext().getResources().getInteger(R.integer.fullyChargedStatusTextScaleX) / 100f;
	}

	@Override
	protected void onStop() {
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
			case R.id.muteAlertsButton:
				updateMuteDuration();
				findViewById(R.id.muteLayout).setVisibility(View.VISIBLE);
				findViewById(R.id.mainLayout).setVisibility(View.INVISIBLE);
				hidePressMenuForMore();
				break;
			case R.id.muteSetButton:
				if (muteDuration > 0) {
					settings.edit().putLong(Settings.MUTED_UNTIL_TIME, System.currentTimeMillis() + muteDuration).commit();
					showMuted();
				}
				else {
					settings.edit().putLong(Settings.MUTED_UNTIL_TIME, 0).commit();
					showUnmuted(false);
				}
				break;
			case R.id.unmuteAlertsButton:
				settings.edit().putLong(Settings.MUTED_UNTIL_TIME, 0).commit();
				showUnmuted(true);
				hidePressMenuForMore();
				break;
			case R.id.startServiceButton: {
				Context context = getContext();
				BatteryNotifierService.start(context);
				Toast.makeText(context, R.string.service_started, Toast.LENGTH_SHORT).show();
				unpluggedSinceLabel.setText(batteryInfoReceiver.getPluggedState() > 0 ? R.string.plugged_since : R.string.unplugged_since);
				unpluggedSinceValue.setText(R.string.unknown);
				updateRunningState();
				break;
			}
			case R.id.muteMinus15mButton:
			case R.id.muteMinus1hButton:
			case R.id.muteMinus12hButton:
			case R.id.mutePlus15mButton:
			case R.id.mutePlus1hButton:
			case R.id.mutePlus12hButton:
				muteDuration += Long.parseLong(view.getTag().toString());
				updateMuteDuration();
				break;
		}
	}

	private void hidePressMenuForMore() {
		View pressMenuForMore = findViewById(R.id.pressMenuForMore);
		pressMenuForMore.setVisibility(View.GONE);
		pressMenuForMore.clearAnimation();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(R.string.battery_use).setIntent(
			new Intent().setClassName("com.android.settings", "com.android.settings.fuelgauge.PowerUsageSummary")
		);
		menu.add(R.string.battery_info).setIntent(
			new Intent().setClassName("com.android.settings", "com.android.settings.BatteryInfo")
		);
		menu.add(R.string.battery_history).setIntent(
			new Intent().setClassName("com.android.settings", "com.android.settings.battery_history.BatteryHistory")
		);
		return true;
	}

	void showMuted() {
		Context context = getContext();
		long mutedUntil = settings.getLong(Settings.MUTED_UNTIL_TIME, 0);
		String mutedUntilStr = DateUtils.formatDateTime(context, mutedUntil,
			DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH
		);
		TextView snoozedText = (TextView) findViewById(R.id.mutedText);
		snoozedText.setText(context.getString(R.string.muted_until, mutedUntilStr));
		findViewById(R.id.muteAlertsButton).setVisibility(View.INVISIBLE);
		snoozedText.setVisibility(View.VISIBLE);
		findViewById(R.id.unmuteAlertsButton).setVisibility(View.VISIBLE);
		findViewById(R.id.muteLayout).setVisibility(View.GONE);
		findViewById(R.id.mainLayout).setVisibility(View.VISIBLE);
	}

	void showUnmuted(boolean animate) {
		View snoozeAlertsButton = findViewById(R.id.muteAlertsButton);
		findViewById(R.id.mutedText).setVisibility(View.INVISIBLE);
		snoozeAlertsButton.setVisibility(View.VISIBLE);
		findViewById(R.id.unmuteAlertsButton).setVisibility(View.INVISIBLE);
		findViewById(R.id.muteLayout).setVisibility(View.GONE);
		findViewById(R.id.mainLayout).setVisibility(View.VISIBLE);
		if (animate) {
			Animation unmuteAnim = AnimationUtils.loadAnimation(getContext(), R.anim.unmute);
			snoozeAlertsButton.startAnimation(unmuteAnim);
		}
	}

	public void onActivityResume() {
		if (activityPausedAt > 0) {
			if (System.currentTimeMillis() - activityPausedAt > 60000) {
				View snoozeLayout = findViewById(R.id.muteLayout);
				if (snoozeLayout.getVisibility() == View.VISIBLE) {
					snoozeLayout.setVisibility(View.GONE);
					findViewById(R.id.mainLayout).setVisibility(View.VISIBLE);
				}
			}
			activityPausedAt = 0;
		}
		Context context = getContext();
		settings = PreferenceManager.getDefaultSharedPreferences(context);
		lowBatteryLevel = settings.getInt(Settings.LOW_BATTERY_LEVEL, context.getResources().getInteger(R.integer.default_low_level));
		updateRunningState();
		batteryInfoReceiver.reset();
		context.registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		Animation disappearingAnim = AnimationUtils.loadAnimation(getContext(), R.anim.disappearing);
		View pressMenuforMoreView = findViewById(R.id.pressMenuForMore);
		pressMenuforMoreView.startAnimation(disappearingAnim);
		pressMenuforMoreView.setVisibility(View.VISIBLE);
	}

	public void onActivityPause() {
		getContext().unregisterReceiver(batteryInfoReceiver);
		activityPausedAt = System.currentTimeMillis();
	}

	void updateRunningState() {
		if (BatteryNotifierService.isRunning(getContext())) {
			findViewById(R.id.startServiceButton).setVisibility(View.INVISIBLE);
			long mutedUntil = settings.getLong(Settings.MUTED_UNTIL_TIME, 0);
			if (System.currentTimeMillis() < mutedUntil) {
				showMuted();
			}
			else {
				showUnmuted(false);
			}
		}
		else {
			unpluggedSinceValue.setText(null);
			unpluggedSinceLabel.setText(R.string.service_is_stopped_short);
			findViewById(R.id.mutedText).setVisibility(View.INVISIBLE);
			findViewById(R.id.muteAlertsButton).setVisibility(View.INVISIBLE);
			findViewById(R.id.unmuteAlertsButton).setVisibility(View.INVISIBLE);
			findViewById(R.id.startServiceButton).setVisibility(View.VISIBLE);
		}
	}

	void updatePluggedState(int pluggedState) {
		unpluggedSinceLabel.setText(null);
		unpluggedSinceValue.setText(null);
		if (BatteryNotifierService.isStarted()) {
			if (pluggedState > 0) {
				new Thread() {
					@Override
					public void run() {
						for (int i = 0; i < 40; i++) {
							final long sinceTime = BatteryNotifierService.getPluggedSince();
							if (sinceTime > 0) {
								getOwnerActivity().runOnUiThread(new Runnable() {
									@Override
									public void run() {
										unpluggedSinceLabel.setText(R.string.plugged_since);
										setPluggedSince(sinceTime);
									}
								});
								break;
							}
							safeSleep(250);
						}
					}
				}.start();
			}
			else {
				new Thread() {
					@Override
					public void run() {
						for (int i = 0; i < 40; i++) {
							final long sinceTime = BatteryNotifierService.getUnpluggedSince();
							if (sinceTime > 0) {
								getOwnerActivity().runOnUiThread(new Runnable() {
									@Override
									public void run() {
										unpluggedSinceLabel.setText(R.string.unplugged_since);
										setPluggedSince(sinceTime);
									}
								});
								break;
							}
							safeSleep(250);
						}
					}
				}.start();
			}
		}
	}

	void setPluggedSince(long since) {
		if (since > 0) {
			String dateTimeStr = DateUtils.formatDateTime(getContext(), since,
				DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH
			);
			unpluggedSinceValue.setText(dateTimeStr);
		}
		else {
			unpluggedSinceValue.setText(R.string.unknown);
		}
	}

	void updateMuteDuration() { //TODO perform updates when time goes
		if (muteDuration <= 0) {
			muteDuration = 0;
			muteDurationView.setText("0:00");
			muteUntilView.setText(R.string.alerts_not_muted);
		}
		else {
			Context context = getContext();
			int durationInMinutes = (int) (muteDuration / 60000);
			int days = durationInMinutes / 1440;
			int minutes = durationInMinutes % 60;
			int hours = durationInMinutes % 1440 / 60;
			StringBuilder durationStr = new StringBuilder();
			if (days > 0) {
				durationStr.append(days).append(context.getString(R.string.day_suffix_short));
			}
			durationStr.append(hours).append(":");
			if (minutes < 10) {
				durationStr.append("0");
			}
			durationStr.append(minutes);
			muteDurationView.setText(durationStr);
			String untilDateTime = DateUtils.formatDateTime(context, System.currentTimeMillis() + muteDuration,
				DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL
			);
			muteUntilView.setText(context.getString(R.string.alerts_muted_until, untilDateTime));
		}
	}

	static void safeSleep(long time) {
		try {
			Thread.sleep(time);
		}
		catch (InterruptedException e) {
			//it's okay
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
		if (VERSION.SDK_INT == 4 && keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			onBackPressed();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		View snoozeLayout = findViewById(R.id.muteLayout);
		if (snoozeLayout.getVisibility() == View.VISIBLE) {
			snoozeLayout.setVisibility(View.GONE);
			findViewById(R.id.mainLayout).setVisibility(View.VISIBLE);
		}
		else {
			cancel();
		}
	}

}
