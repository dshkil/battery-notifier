package com.shkil.battery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

	static final int NOTIFICATION_ID = 1;
	static final int DEFAULT_NOTIFICATION_FLAGS = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_NO_CLEAR;

	static final int STATE_OKAY = 1;
	static final int STATE_LOW = 2;
	static final int STATE_CHARGING = 3;
	static final int STATE_FULL = 4;

	// Cached settings values
	int lowBatteryLevel;
	int insistInterval;
	boolean alwaysShowNotification;
	boolean showLevelInIcon;

	// Battery state information
	int batteryState;
	int batteryLevel;
	long unpluggedSince;

	SharedPreferences settings;
	NotificationManager notificationService;
	Notification notification;
	PendingIntent insistTimerPendingIntent;
	boolean insistTimerActive;

	static Method startForegroundMethod;
	static Method stopForegroundMethod;
	static {
		try {
			startForegroundMethod = Service.class.getMethod("startForeground", new Class[] {int.class, Notification.class});
			stopForegroundMethod = Service.class.getMethod("stopForeground", new Class[] {boolean.class});
		}
		catch (NoSuchMethodException e) {
			startForegroundMethod = null;
		}
	}

	final BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
		private int lastRawLevel;
		private int lastStatus;
		@Override
		public void onReceive(Context context, Intent intent) {
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			boolean statusChanged = status != lastStatus;
			boolean levelChanged = level != lastRawLevel;
			if (levelChanged || statusChanged) {
				lastStatus = status;
				lastRawLevel = level;
				Log.v(TAG, "batteryInfoReceiver: status=" + status + ", level=" + level);
				if (status == BatteryManager.BATTERY_STATUS_FULL) {
					batteryLevel = 100;
					boolean isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0; //TODO is it necessary?
					setBatteryState(isPlugged ? STATE_FULL : STATE_OKAY, false);
				}
				else {
					if (levelChanged) {
						setBatteryLevel(level * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
					}
					if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
						if (statusChanged) {
							unpluggedSince = 0;
							setBatteryState(STATE_CHARGING, false);
						}
					}
					else {
						int oldBatteryState = batteryState;
						if (statusChanged && (oldBatteryState == STATE_CHARGING || oldBatteryState == STATE_FULL)) {
							unpluggedSince = System.currentTimeMillis();
						}
						int newBatteryState = checkBatteryLevel();
						if (levelChanged && oldBatteryState == STATE_LOW && newBatteryState == STATE_LOW) {
							updateLowBatteryNotificationInfo();
						}
					}
				}
			}
		}
	};	

	/**
	 * @return battery state
	 */
	int checkBatteryLevel() {
		Log.d(TAG, "checkBatteryLevel(): batteryState=" + batteryState);
		if (batteryLevel > lowBatteryLevel || lowBatteryLevel == 0) {
			setBatteryState(STATE_OKAY, false);
			return STATE_OKAY;
		}
		else {
			setBatteryState(STATE_LOW, false);
			return STATE_LOW;
		}
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
		setForeground(true); // ignored by latest Android versions
		insistTimerPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, AlarmReciever.class), 0);
		notificationService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notification = new Notification();
		notification.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		updateValuesFromSettings(settings, null);
		registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		settings.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		unregisterReceiver(batteryInfoReceiver);
		if (insistTimerActive) {
			stopInsist();
		}
		settings.unregisterOnSharedPreferenceChangeListener(this);
		hideNotification();
		setForeground(false); // ignored by latest Android versions
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		updateValuesFromSettings(settings, key);
	}

	void updateValuesFromSettings(SharedPreferences settings, String key) {
		Log.v(TAG, "Updating values from settings...");
		if (Settings.SOUND_MODE.equals(key) || Settings.VIBRO_MODE.equals(key)) {
			if (batteryState == STATE_LOW) {
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
			if (key == null || Settings.ALWAYS_SHOW_ICON.equals(key)) {
				alwaysShowNotification = settings.getBoolean(Settings.ALWAYS_SHOW_ICON, resources.getBoolean(R.bool.default_always_show_icon));
				if (key != null) {
					setBatteryState(batteryState, true);
				}
			}
			if (key == null || Settings.SHOW_LEVEL_IN_ICON.equals(key)) {
				showLevelInIcon = settings.getBoolean(Settings.SHOW_LEVEL_IN_ICON, resources.getBoolean(R.bool.default_show_level_in_icon));
				updateLevelInIcon();
			}
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
			Log.d(TAG, "setLowBatteryLevel(" + level + "): batteryLevel=" + batteryLevel);
			if (batteryLevel > 0 && batteryState != STATE_CHARGING && batteryState != STATE_FULL) {
				checkBatteryLevel();
			}
		}
	}

	void setInsistInterval(int interval) {
		this.insistInterval = interval;
		if (insistTimerActive) {
			startInsist();
		}
	}

	void startInsist() {
		AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		if (insistTimerActive) {
			alarmManager.cancel(insistTimerPendingIntent);
		}
		insistTimerActive = true;
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

	/**
	 * This is a wrapper around the new startForeground method, using the older APIs if it is not available.
	 */
	public void startForegroundCompat(int id, Notification notification) {
		// If we have the new startForeground API, then use it
		if (startForegroundMethod != null) {
			try {
				startForegroundMethod.invoke(this, new Object[] {Integer.valueOf(id), notification});
			}
			catch (InvocationTargetException e) {
				Log.w(TAG, "Unable to invoke startForeground", e);
			}
			catch (IllegalAccessException e) {
				Log.w(TAG, "Unable to invoke startForeground", e);
			}
		}
		else { // Fall back on the old API
			notificationService.notify(id, notification);
		}
	}

	/**
	 * This is a wrapper around the new stopForeground method, using the older APIs if it is not available.
	 */
	public void stopForegroundCompat(int id) {
		// If we have the new stopForeground API, then use it.
		if (stopForegroundMethod != null) {
			try {
				stopForegroundMethod.invoke(this, new Object[] {Boolean.TRUE});
			}
			catch (InvocationTargetException e) {
				Log.w(TAG, "Unable to invoke stopForeground", e);
			}
			catch (IllegalAccessException e) {
				Log.w(TAG, "Unable to invoke stopForeground", e);
			}
		}
		else {
			// Fall back on the old API
			setForeground(false);
		}
	}

	public static int getBatteryIcon(int level) {
		return level > 20 ? R.drawable.battery_almost_low : R.drawable.battery_low;
	}

	void setBatteryLevel(int level) {
		this.batteryLevel = level;
		updateLevelInIcon();
	}

	void updateLevelInIcon() {
		Notification notification = this.notification;
		if (showLevelInIcon) {
			if (notification.number == 0) {
				hideNotification();
			}
			notification.number = batteryLevel;
		}
		else if (notification.number > 0) {
			hideNotification();
			notification.number = 0;
		}
		boolean isNotificationVisible = alwaysShowNotification || batteryState == STATE_LOW; //FIXME how about full battery?
		if (isNotificationVisible) {
			showNotification(notification);
		}
	}

	void setBatteryState(int state, boolean force) {
		boolean stateChanged = this.batteryState != state;  
		if (stateChanged || force) {
			if (insistTimerActive) {
				stopInsist();
			}
			this.batteryState = state;
			Notification notification = this.notification;
			notification.when = System.currentTimeMillis();
			notification.flags = DEFAULT_NOTIFICATION_FLAGS;
			switch (state) {
				case STATE_LOW: {
					if (stateChanged) {
						Log.d(TAG, "Battery became low");
						onBatteryLow();
					}
					break;
				}
				case STATE_CHARGING: {
					Log.d(TAG, "Battery starts charging");
					if (alwaysShowNotification) {
						notification.icon = getBatteryIcon(batteryLevel);
						notification.tickerText = null;
						notification.setLatestEventInfo(this, "Battery status", "Battery is charging", notification.contentIntent); //FIXME
						showNotification(notification);
					}
					else {
						hideNotification();
					}
					break;
				}
				case STATE_OKAY: {
					Log.d(TAG, "Battery became okay");
					if (alwaysShowNotification) {
						notification.icon = getBatteryIcon(batteryLevel);
						notification.tickerText = null;
						notification.setLatestEventInfo(this, "Battery status", "Battery level is ok", notification.contentIntent); //FIXME
						showNotification(notification);
					}
					else {
						hideNotification();
					}
					break;
				}
				case STATE_FULL: {
					Log.d(TAG, "Battery became full");
					onBatteryFull();
					break;
				}
			}
		}
	}

	private void showNotification(Notification notification) {
		startForegroundCompat(NOTIFICATION_ID, notification);
	}

	private void hideNotification() {
		stopForegroundCompat(NOTIFICATION_ID);
	}

	private void onBatteryLow() {
		updateLowBatteryNotificationInfo();
		notification.tickerText = getString(R.string.low_battery_level_ticker);
		showNotification(notification);
		SharedPreferences settings = this.settings;
		boolean shouldInsist = !Settings.isVibroDisabled(settings) || !Settings.isSoundDisabled(settings);
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		if (Settings.shouldVibrate(settings, audioManager)) {
			// notification.vibrate = VIBRATE_PATTERN;
		}
		if (Settings.shouldSound(settings, audioManager) != Settings.SHOULD_SOUND_FALSE) {
			// notification.sound = Settings.getAlertRingtone(settings);
		}
		if (shouldInsist) {
			startInsist();
		}
	}

	private void onBatteryFull() {
		SharedPreferences settings = this.settings;
		boolean notify;
		if (alwaysShowNotification) {
			notify = true;
		}
		else {
			boolean defaultNotifyFullBattery = getResources().getBoolean(R.bool.default_notify_full_battery);
			notify = settings.getBoolean(Settings.NOTIFY_FULL_BATTERY, defaultNotifyFullBattery);
		}
		if (notify) {
			Notification notification = this.notification;
			notification.icon = R.drawable.battery_full;
			notification.tickerText = getString(R.string.full_battery_level_ticker);
			notification.setLatestEventInfo(this, getString(R.string.battery_level_is_full), "", notification.contentIntent);
			if (!alwaysShowNotification) {
				notification.flags &= ~Notification.FLAG_NO_CLEAR;
				notification.flags |= Notification.FLAG_AUTO_CANCEL;
			}
			showNotification(notification);
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if (Settings.shouldVibrate(settings, audioManager)) {
				// fullBatteryNotification.vibrate = VIBRATE_PATTERN;
			}
			if (Settings.shouldSound(settings, audioManager) != Settings.SHOULD_SOUND_FALSE) {
				// fullBatteryNotification.sound = Settings.getAlertRingtone(settings);
			}
		}
		else {
			hideNotification();
		}
	}

	void updateLowBatteryNotificationInfo() {
		Log.d(TAG, "updateLowBatteryNotificationInfo: batteryLevel=" + batteryLevel);
		String contentText;
		long unpluggedSince = this.unpluggedSince;
		if (unpluggedSince == 0) {
			contentText = getString(R.string.battery_level_notification_info, batteryLevel);
		}
		else {
			String since = DateUtils.formatDateTime(this,
				unpluggedSince,
				DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH
			);
			contentText = getString(R.string.battery_level_notification_info_ex, batteryLevel, since);
		}
		Notification notification = this.notification;
		notification.setLatestEventInfo(this, getString(R.string.battery_level_is_low), contentText, notification.contentIntent);
		notification.icon = getBatteryIcon(batteryLevel);
	}

}
