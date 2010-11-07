package com.shkil.battery;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
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
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;

public class BatteryNotifierService extends Service implements OnSharedPreferenceChangeListener {

	static final String TAG = BatteryNotifierService.class.getSimpleName();
	static final String CLASS = BatteryNotifierService.class.getName();

	static final long[] VIBRATE_PATTERN = new long[] {0,50,200,100};

	static final int BATTERY_LOW_NOTIFY_ID = 1; 
	static final int BATTERY_FULL_NOTIFY_ID = 2;

	static final int STATE_UNKNOWN = 0;
	static final int STATE_OKAY = 1;
	static final int STATE_LOW = 2;
	static final int STATE_CHARGING = 3;

	int lowBatteryLevel;
	int insistInterval;
	int lastBatteryState = STATE_OKAY;
	int lastBatteryLevel;
	long unpluggedSince;

	SharedPreferences settings;
	NotificationManager notificationService;
	Notification lowBatteryNotification;
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
				Log.v(TAG, "batteryInfoReceiver[values changed]: status=" + status + ", level=" + level);
				if (status == BatteryManager.BATTERY_STATUS_FULL) {
					if (lastStatus != BatteryManager.BATTERY_STATUS_FULL) {
						lastStatus = status;
						onBatteryFull();
					}
					else {
						lastStatus = status;
					}
					boolean onBattery = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0;
					if (onBattery) {
						lastBatteryState = STATE_OKAY;
					}
					else {
						lastBatteryState = STATE_CHARGING;
					}
					lastBatteryLevel = 100;
				}
				else {
					if (lastStatus == BatteryManager.BATTERY_STATUS_FULL) {
						notificationService.cancel(BATTERY_FULL_NOTIFY_ID);
					}
					lastStatus = status;
					int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
					int percent = level * 100 / scale;
					lastBatteryLevel = percent;
					if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
						if (lastBatteryState != STATE_CHARGING) {
							unpluggedSince = 0;
							if (lastBatteryState != STATE_OKAY) {
								lastBatteryState = STATE_CHARGING;
								onBatteryOkay();
							}
							else {
								lastBatteryState = STATE_CHARGING;
							}
						}
					}
					else {
						if (lastBatteryState == STATE_CHARGING) {
							unpluggedSince = System.currentTimeMillis();
						}
						checkBatteryLevel();
						if (lastBatteryState == STATE_LOW) {
							updateLowBatteryNotificationInfo(true);
						}
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
		lowBatteryNotification = new Notification(
			R.drawable.battery_low, getString(R.string.low_battery_level_ticker), System.currentTimeMillis()
		);
		lowBatteryNotification.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
		lowBatteryNotification.flags |= Notification.FLAG_NO_CLEAR;
		settings = PreferenceManager.getDefaultSharedPreferences(this);
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
		if (Settings.SHOW_LEVEL_IN_ICON.equals(key)) {
			if (lastBatteryState == STATE_LOW) {
				updateLowBatteryNotificationInfo(true);
			}
		}
		else if (Settings.SOUND_MODE.equals(key) || Settings.VIBRO_MODE.equals(key)) {
			if (lastBatteryState == STATE_LOW) {
				if (!Settings.isSoundDisabled(settings) || !Settings.isVibroDisabled(settings)) {
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
			Resources resources = getResources();
			if (key == null || Settings.LOW_BATTERY_LEVEL.equals(key)) {
				int lowLevelValue = settings.getInt(Settings.LOW_BATTERY_LEVEL, resources.getInteger(R.integer.default_low_level));
				setLowBatteryLevel(lowLevelValue);
			}
			if (key == null || Settings.ALERT_INTERVAL.equals(key)) {
				try {
					String intervalValue = settings.getString(Settings.ALERT_INTERVAL, null);
					if (intervalValue == null) {
						intervalValue = resources.getString(R.string.default_alert_interval);
					}
					setInsistInterval(Integer.parseInt(intervalValue));
				}
				catch (NumberFormatException ex) {
					setInsistInterval(resources.getInteger(R.string.default_alert_interval));
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
		if (lastBatteryLevel > lowBatteryLevel || lowBatteryLevel == 0) {
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
		showLowBatteryNotification(true);
	}

	void onBatteryOkay() {
		Log.d(TAG, "Battery became okay");
		stopInsist();
		NotificationManager notifyService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notifyService.cancel(BATTERY_LOW_NOTIFY_ID);
	}

	void onBatteryFull() {
		Log.d(TAG, "Battery became full");
		SharedPreferences settings = this.settings;
		Resources resources = getResources();
		boolean notifyFullBattery = resources.getBoolean(R.bool.default_notify_full_battery);
		if (settings.getBoolean(Settings.NOTIFY_FULL_BATTERY, notifyFullBattery)) {
			Notification fullBatteryNotification = new Notification(
				R.drawable.battery_full, getString(R.string.full_battery_level_ticker), System.currentTimeMillis()
			);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
			fullBatteryNotification.contentIntent = contentIntent;
			fullBatteryNotification.flags |= Notification.FLAG_AUTO_CANCEL;
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if (Settings.shouldVibrate(settings, audioManager)) {
				fullBatteryNotification.vibrate = VIBRATE_PATTERN;
			}
			if (Settings.shouldSound(settings, audioManager) != Settings.SHOULD_SOUND_FALSE) {
				fullBatteryNotification.sound = Settings.getAlertRingtone(settings);
			}
			fullBatteryNotification.setLatestEventInfo(this, getString(R.string.battery_level_is_full), "", contentIntent);
			notificationService.notify(BATTERY_FULL_NOTIFY_ID, fullBatteryNotification);
		}
	}

	void showLowBatteryNotification(boolean canInsist) {
		stopInsist();
		SharedPreferences settings = this.settings;
		boolean shouldInsist = !Settings.isVibroDisabled(settings) || !Settings.isSoundDisabled(settings);
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		if (Settings.shouldVibrate(settings, audioManager)) {
			lowBatteryNotification.vibrate = VIBRATE_PATTERN;
		}
		if (Settings.shouldSound(settings, audioManager) != Settings.SHOULD_SOUND_FALSE) {
			lowBatteryNotification.sound = Settings.getAlertRingtone(settings);
		}
		lowBatteryNotification.when = System.currentTimeMillis();
		updateLowBatteryNotificationInfo(false);
		if (canInsist && shouldInsist) {
			startInsist();
		}
	}

	void updateLowBatteryNotificationInfo(boolean makeSilent) {
		Log.d(TAG, "updateLowBatteryNotificationInfo: lastBatteryLevel=" + lastBatteryLevel);
		String contentText;
		long unpluggedSince = this.unpluggedSince;
		if (unpluggedSince == 0) {
			contentText = getString(R.string.battery_level_notification_info, lastBatteryLevel);
		}
		else {
			String since = DateUtils.formatDateTime(this,
				unpluggedSince,
				DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH
			);
			contentText = getString(R.string.battery_level_notification_info_ex, lastBatteryLevel, since);
		}
		Notification lowBatteryNotification = this.lowBatteryNotification;
		lowBatteryNotification.setLatestEventInfo(this, getString(R.string.battery_level_is_low), contentText, lowBatteryNotification.contentIntent);
		lowBatteryNotification.icon = lastBatteryLevel > 20 ? R.drawable.battery_almost_low : R.drawable.battery_low;
		if (settings.getBoolean(Settings.SHOW_LEVEL_IN_ICON, getResources().getBoolean(R.bool.default_show_level_in_icon))) {
			if (lowBatteryNotification.number == 0) {
				notificationService.cancel(BATTERY_LOW_NOTIFY_ID);
			}
			lowBatteryNotification.number = lastBatteryLevel;
		}
		else {
			if (lowBatteryNotification.number > 0) {
				notificationService.cancel(BATTERY_LOW_NOTIFY_ID);
			}
			lowBatteryNotification.number = 0;
		}
		if (makeSilent) {
			lowBatteryNotification.sound = null;
			lowBatteryNotification.vibrate = null;
		}
		notificationService.notify(BATTERY_LOW_NOTIFY_ID, lowBatteryNotification);
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

	public static boolean isRunning(Context context) {
		ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
		List<RunningServiceInfo> runningServices = activityManager.getRunningServices(Integer.MAX_VALUE);
		for (RunningServiceInfo serviceInfo : runningServices) {
			String serviceName = serviceInfo.service.getClassName();
			if (serviceName.equals(CLASS)) {
				return true;
			}
		}
		return false;
	}

	public static void start(Context context) {
		context.startService(new Intent(context, BatteryNotifierService.class));
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		settings.edit().putBoolean(Settings.STARTED, true).commit();
	}

	public static void stop(Context context) {
		context.stopService(new Intent(context, BatteryNotifierService.class));
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		settings.edit().putBoolean(Settings.STARTED, false).commit();
	}

}
