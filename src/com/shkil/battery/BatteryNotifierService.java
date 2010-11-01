package com.shkil.battery;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class BatteryNotifierService extends Service implements OnSharedPreferenceChangeListener {

	static final String TAG = BatteryNotifierService.class.getSimpleName();
	static final long[] VIBRATE_PATTERN = new long[] {0,50,200,100};
	static final int BATTERY_LOW_NOTIFY_ID = 1; 

	static final int STATE_UNKNOWN = 0;
	static final int STATE_OKAY = 1;
	static final int STATE_LOW = 2;
	static final int STATE_CHARGING = 3;

	int lowBatteryLevel;
	int insistInterval;
	int lastBatteryState = STATE_OKAY;
	int lastBatteryLevel;

	NotificationManager notificationService;
	Notification notification;
	PendingIntent insistTimerPendingIntent;
	boolean insistTimerActive;

	final BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
		private int lastRawLevel;
		private int lastStatus;
		@Override
		public void onReceive(Context context, Intent intent) {
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			if (level != lastRawLevel || status != lastStatus) {
				lastRawLevel = level;
				lastStatus = status;
				Log.v(TAG, "batteryInfoReceiver[values changed]: status=" + status + ", level=" + level);
				if (status == BatteryManager.BATTERY_STATUS_FULL) {
					boolean onBattery = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0;
					lastBatteryState = onBattery ? STATE_OKAY : STATE_CHARGING;
					lastBatteryLevel = 100;
				}
				else {
					int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
					int percent = level * 100 / scale;
					if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
						lastBatteryLevel = percent;
						if (lastBatteryState != STATE_CHARGING) {
							if (lastBatteryState != STATE_OKAY) {
								lastBatteryState = STATE_CHARGING;
								onBatteryOkay();
							}
							else {
								lastBatteryState = STATE_CHARGING;
							}
						}
					}
					else if (lastBatteryLevel != percent) {
						lastBatteryLevel = percent;
						checkBatteryLevel();
						updateNotificationInfo();
					}
					else {
						lastBatteryState = STATE_UNKNOWN;
						updateNotificationInfo();
					}
				}
			}
		}
	};

	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
		Intent alarmRecieverIntent = new Intent(this, AlarmReciever.class);
		insistTimerPendingIntent = PendingIntent.getBroadcast(this, 0, alarmRecieverIntent, 0);
		notificationService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notification = new Notification(
			R.drawable.battery_low, getString(R.string.low_battery_level_ticker), System.currentTimeMillis()
		);
		notification.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
		notification.flags |= Notification.FLAG_NO_CLEAR;
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		updateValuesFromSettings(settings, null);
		registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		settings.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		unregisterReceiver(batteryInfoReceiver);
		stopInsist();
		NotificationManager notifyService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notifyService.cancelAll();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind");
		return null;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		updateValuesFromSettings(settings, key);
	}

	void updateValuesFromSettings(SharedPreferences settings, String key) {
		Log.v(TAG, "Updating values from settings...");
		if (Settings.ALERT_VIBRO_ON.equals(key) || Settings.ALERT_SOUND_ON.equals(key)) {
			if (lastBatteryState == STATE_LOW) {
				Resources resources = getResources();
				boolean vibrate = settings.getBoolean(Settings.ALERT_VIBRO_ON, resources.getBoolean(R.bool.default_alert_vibro_on));
				if (vibrate || settings.getBoolean(Settings.ALERT_SOUND_ON, resources.getBoolean(R.bool.default_alert_sound_on))) {
					if (!insistTimerActive) {
						startInsist();
					}
				}
				else if (insistTimerActive) {
					stopInsist();
				}
			}
		}
		else {
			if (key == null || Settings.LOW_BATTERY_LEVEL.equals(key)) {
				try {
					String lowLevelValue = settings.getString(Settings.LOW_BATTERY_LEVEL, null);
					if (lowLevelValue == null) {
						lowLevelValue = getString(R.string.default_low_level);
					}
					setLowBatteryLevel(Integer.parseInt(lowLevelValue));
				}
				catch (NumberFormatException ex) {
					setLowBatteryLevel(getResources().getInteger(R.string.default_low_level));
				}
			}
			if (key == null || Settings.ALERT_INTERVAL.equals(key)) {
				try {
					String intervalValue = settings.getString(Settings.ALERT_INTERVAL, null);
					if (intervalValue == null) {
						intervalValue = getString(R.string.default_alert_interval);
					}
					setInsistInterval(Integer.parseInt(intervalValue));
				}
				catch (NumberFormatException ex) {
					setInsistInterval(getResources().getInteger(R.string.default_alert_interval));
				}
			}
		}
	}

	void setLowBatteryLevel(int level) {
		if (lowBatteryLevel != level) {
			lowBatteryLevel = level;
			Log.d(TAG, "setLowBatteryLevel(): lastBatteryState=" + lastBatteryState);
			if (lastBatteryLevel > 0 && lastBatteryState != STATE_CHARGING) {
				checkBatteryLevel();
			}
		}
	}

	void checkBatteryLevel() {
		Log.d(TAG, "checkBatteryLevel(): lastBatteryState=" + lastBatteryState);
		if (lastBatteryLevel > lowBatteryLevel) {
			if (lastBatteryState != STATE_OKAY) {
				lastBatteryState = STATE_OKAY;
				onBatteryOkay();
			}
		}
		else if (lastBatteryState != STATE_LOW) {
			lastBatteryState = STATE_LOW;
			onBatteryLow();
		}
	}

	void onBatteryLow() {
		Log.d(TAG, "Battery became low");
		showNotification(true);
	}

	void onBatteryOkay() {
		Log.d(TAG, "Battery became okay");
		stopInsist();
		NotificationManager notifyService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notifyService.cancel(BATTERY_LOW_NOTIFY_ID);
	}

	void showNotification(boolean canInsist) {
		stopInsist();
		Resources resources = getResources();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean shouldInsist = false;
		if (settings.getBoolean(Settings.ALERT_VIBRO_ON, resources.getBoolean(R.bool.default_alert_vibro_on))) {
			notification.vibrate = VIBRATE_PATTERN;
			shouldInsist = true;
		}
		if (settings.getBoolean(Settings.ALERT_SOUND_ON, resources.getBoolean(R.bool.default_alert_sound_on))) {
			notification.sound = Settings.getAlertRingtone(settings);
			shouldInsist = true;
		}
		updateNotificationInfo();
		if (canInsist && shouldInsist) {
			startInsist();
		}
	}

	void updateNotificationInfo() {
		Log.d(TAG, "updateNotificationInfo: lastBatteryLevel=" + lastBatteryLevel);
		String contentText = getString(R.string.battery_level_notification_info, lastBatteryLevel);
		notification.setLatestEventInfo(this, getString(R.string.battery_level_is_low), contentText, notification.contentIntent);
		notificationService.notify(BATTERY_LOW_NOTIFY_ID, notification);
	}

	void setInsistInterval(int interval) {
		this.insistInterval = interval;
		if (insistTimerActive) {
			startInsist();
		}
	}

	void startInsist() {
		insistTimerActive = true;
		AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmManager.cancel(insistTimerPendingIntent);
		long firstTime = System.currentTimeMillis() + insistInterval;
		alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstTime, insistInterval, insistTimerPendingIntent);
	}

	void stopInsist() {
		insistTimerActive = false;
		AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		alarmManager.cancel(insistTimerPendingIntent);
	}

}
