package com.larryvgs.battery;

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
import android.media.AsyncPlayer;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;

public class BatteryNotifierService extends Service implements OnSharedPreferenceChangeListener {

	static final String TAG = BatteryNotifierService.class.getSimpleName();
	static final String CLASS = BatteryNotifierService.class.getName();

	static final long[] VIBRATE_PATTERN = new long[] {0,50,200,100};

	static final int NOTIFICATION_ID = 1;
	static final int DEFAULT_NOTIFICATION_FLAGS = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

	static final int STATE_OKAY = 1;
	static final int STATE_LOW = 2;
	static final int STATE_CHARGING = 3;
	static final int STATE_FULL = 4;

	static final int SINCE_TIME_FORMAT = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH;

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
	boolean notificationVisible;
	PendingIntent insistTimerPendingIntent;
	boolean insistTimerActive;
	static AsyncPlayer player;

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
				if (levelChanged) {
					batteryLevel = level * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
					if (showLevelInIcon) {
						notification.number = batteryLevel;
					}
				}
				if (status == BatteryManager.BATTERY_STATUS_FULL) {
					if (statusChanged) {
						setBatteryState(STATE_FULL, false);
					}
					else if (notificationVisible) {
						updateNotification();
					}
				}
				else {
					if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
						if (statusChanged) {
							unpluggedSince = 0;
							setBatteryState(STATE_CHARGING, false);
						}
						else {
							updateNotification();
						}
					}
					else {
						if (statusChanged) {
							switch (batteryState) {
								case STATE_CHARGING:
								case STATE_FULL:
									unpluggedSince = System.currentTimeMillis();
							}
						}
						if (levelChanged) {
							int oldBatteryState = batteryState;
							checkBatteryLevel();
							if (notificationVisible && oldBatteryState == batteryState) {
								updateNotification();
							}
						}
						else {
							checkBatteryLevel();
						}
					}
				}
			}
		}
	};	

	void checkBatteryLevel() {
		Log.d(TAG, "checkBatteryLevel(): batteryState=" + batteryState);
		if (batteryLevel > lowBatteryLevel || lowBatteryLevel == 0) {
			setBatteryState(STATE_OKAY, false);
		}
		else {
			setBatteryState(STATE_LOW, false);
		}
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
		if (startForegroundMethod == null) {
			setForeground(true);
		}
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
		if (stopForegroundMethod == null) {
			setForeground(false);
		}
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
		if (Settings.LOW_CHARGE_SOUND_MODE.equals(key) || Settings.LOW_CHARGE_VIBRO_MODE.equals(key)) {
			if (batteryState == STATE_LOW) {
				if (Settings.isAlarmDisabled(Settings.LOW_CHARGE_SOUND_MODE, Settings.LOW_CHARGE_VIBRO_MODE, settings)) {
					if (insistTimerActive) {
						stopInsist();
					}
				}
				else if (!insistTimerActive) {
					startInsist();
				}
			}
		}
		else if (Settings.NOTIFY_FULL_BATTERY.equals(key)) {
			setBatteryState(batteryState, true);
		}
		else {
			Resources resources = getResources();
			if (key == null || Settings.ALWAYS_SHOW_NOTIFICATION.equals(key)) {
				alwaysShowNotification = settings.getBoolean(Settings.ALWAYS_SHOW_NOTIFICATION, resources.getBoolean(R.bool.default_always_show_notification));
				if (key != null) {
					setBatteryState(batteryState, true);
				}
			}
			if (key == null || Settings.SHOW_LEVEL_IN_ICON.equals(key)) {
				showLevelInIcon = settings.getBoolean(Settings.SHOW_LEVEL_IN_ICON, resources.getBoolean(R.bool.default_show_level_in_icon));
				if (notificationVisible) {
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
					notification.tickerText = null;
					showNotification(notification);
				}
				else {
					notification.number = showLevelInIcon ? batteryLevel : 0;
				}
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
		if (player != null) {
			player.stop();
		}
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
			notificationService.cancel(id);
		}
	}

	public static int getBatteryIcon(int level) {
		if (level == 100) {
			return R.drawable.g100;
		}
		if (level == 99) {
			return R.drawable.g99;
		}
		if (level == 98) {
			return R.drawable.g98;
		}
		if (level == 97) {
			return R.drawable.g97;
		}
		if (level == 96) {
			return R.drawable.g96;
		}
		if (level == 95) {
			return R.drawable.g95;
		}
		if (level == 94) {
			return R.drawable.g94;
		}
		if (level == 93) {
			return R.drawable.g93;
		}
		if (level == 92) {
			return R.drawable.g92;
		}
		if (level == 91) {
			return R.drawable.g91;
		}
		if (level == 90) {
			return R.drawable.g90;
		}
		if (level == 89) {
			return R.drawable.g89;
		}
		if (level == 88) {
			return R.drawable.g88;
		}
		if (level == 87) {
			return R.drawable.g87;
		}
		if (level == 86) {
			return R.drawable.g86;
		}
		if (level == 85) {
			return R.drawable.g85;
		}
		if (level == 84) {
			return R.drawable.g84;
		}
		if (level == 83) {
			return R.drawable.g83;
		}
		if (level == 82) {
			return R.drawable.g82;
		}
		if (level == 81) {
			return R.drawable.g81;
		}
		if (level == 80) {
			return R.drawable.g80;
		}
		if (level == 79) {
			return R.drawable.g79;
		}
		if (level == 78) {
			return R.drawable.g78;
		}
		if (level == 77) {
			return R.drawable.g77;
		}
		if (level == 76) {
			return R.drawable.g76;
		}
		if (level == 75) {
			return R.drawable.g75;
		}
		if (level == 74) {
			return R.drawable.g74;
		}
		if (level == 73) {
			return R.drawable.g73;
		}
		if (level == 72) {
			return R.drawable.g72;
		}
		if (level == 71) {
			return R.drawable.g71;
		}
		if (level == 70) {
			return R.drawable.g70;
		}
		if (level == 69) {
			return R.drawable.g69;
		}
		if (level == 68) {
			return R.drawable.g68;
		}
		if (level == 67) {
			return R.drawable.g67;
		}
		if (level == 66) {
			return R.drawable.g66;
		}
		if (level == 65) {
			return R.drawable.g65;
		}
		if (level == 64) {
			return R.drawable.g64;
		}
		if (level == 63) {
			return R.drawable.g63;
		}
		if (level == 62) {
			return R.drawable.g62;
		}
		if (level == 61) {
			return R.drawable.g61;
		}
		if (level == 60) {
			return R.drawable.g60;
		}
		if (level == 59) {
			return R.drawable.g59;
		}
		if (level == 58) {
			return R.drawable.g58;
		}
		if (level == 57) {
			return R.drawable.g57;
		}
		if (level == 56) {
			return R.drawable.g56;
		}
		if (level == 55) {
			return R.drawable.g55;
		}
		if (level == 54) {
			return R.drawable.g54;
		}
		if (level == 53) {
			return R.drawable.g53;
		}
		if (level == 52) {
			return R.drawable.g52;
		}
		if (level == 51) {
			return R.drawable.g51;
		}
		if (level == 50) {
			return R.drawable.g50;
		}
		if (level == 49) {
			return R.drawable.b49;
		}
		if (level == 48) {
			return R.drawable.b48;
		}
		if (level == 47) {
			return R.drawable.b47;
		}
		if (level == 46) {
			return R.drawable.b46;
		}
		if (level == 45) {
			return R.drawable.b45;
		}
		if (level == 44) {
			return R.drawable.b44;
		}
		if (level == 43) {
			return R.drawable.b43;
		}
		if (level == 42) {
			return R.drawable.b42;
		}
		if (level == 41) {
			return R.drawable.b41;
		}
		if (level == 40) {
			return R.drawable.b40;
		}
		if (level == 39) {
			return R.drawable.b39;
		}
		if (level == 38) {
			return R.drawable.b38;
		}
		if (level == 37) {
			return R.drawable.b37;
		}
		if (level == 36) {
			return R.drawable.b36;
		}
		if (level == 35) {
			return R.drawable.b35;
		}
		if (level == 34) {
			return R.drawable.b34;
		}
		if (level == 33) {
			return R.drawable.b33;
		}
		if (level == 32) {
			return R.drawable.b32;
		}
		if (level == 31) {
			return R.drawable.b31;
		}
		if (level == 30) {
			return R.drawable.b30;
		}
		if (level == 29) {
			return R.drawable.o29;
		}
		if (level == 28) {
			return R.drawable.o28;
		}
		if (level == 27) {
			return R.drawable.o27;
		}
		if (level == 26) {
			return R.drawable.o26;
		}
		if (level == 25) {
			return R.drawable.o25;
		}
		if (level == 24) {
			return R.drawable.o24;
		}
		if (level == 23) {
			return R.drawable.o23;
		}
		if (level == 22) {
			return R.drawable.o22;
		}
		if (level == 21) {
			return R.drawable.o21;
		}
		if (level == 20) {
			return R.drawable.o20;
		}
		if (level == 19) {
			return R.drawable.o19;
		}
		if (level == 18) {
			return R.drawable.o18;
		}
		if (level == 17) {
			return R.drawable.o17;
		}
		if (level == 16) {
			return R.drawable.o16;
		}
		if (level == 15) {
			return R.drawable.o15;
		}
		if (level == 14) {
			return R.drawable.r14;
		}
		if (level == 13) {
			return R.drawable.r13;
		}
		if (level == 12) {
			return R.drawable.r12;
		}
		if (level == 11) {
			return R.drawable.r11;
		}
		if (level == 10) {
			return R.drawable.r10;
		}
		if (level == 9) {
			return R.drawable.r9;
		}
		if (level == 8) {
			return R.drawable.r8;
		}
		if (level == 7) {
			return R.drawable.r7;
		}
		if (level == 6) {
			return R.drawable.r6;
		}
		if (level == 5) {
			return R.drawable.r5;
		}
		if (level == 4) {
			return R.drawable.r4;
		}
		if (level == 3) {
			return R.drawable.r3;
		}
		if (level == 2) {
			return R.drawable.r2;
		}
		if (level == 1) {
			return R.drawable.r1;
		}
		if (level == 0) {
			return R.drawable.r0;
		}
		return level ;
	}

	private void showNotification(Notification notification) {
		startForegroundCompat(NOTIFICATION_ID, notification);
		notificationVisible = true;
	}

	private void hideNotification() {
		notificationVisible = false;
		stopForegroundCompat(NOTIFICATION_ID);
	}

	void setBatteryState(int state, boolean settingsChanged) {
		boolean stateChanged = this.batteryState != state;  
		if (stateChanged || settingsChanged) {
			Notification notification = this.notification;
			if (stateChanged) {
				this.batteryState = state;
				Log.d(TAG, "Battery state became " + state);
				notification.when = System.currentTimeMillis();
				if (insistTimerActive) {
					stopInsist();
				}
			}
			notification.flags = DEFAULT_NOTIFICATION_FLAGS;
			switch (state) {
				case STATE_LOW: {
					if (stateChanged) {
						notification.tickerText = getString(R.string.low_battery_level_ticker);
						updateNotification();
						alarm(Settings.LOW_CHARGE_SOUND_MODE, Settings.LOW_CHARGE_VIBRO_MODE, Settings.LOW_CHARGE_RINGTONE);
						if (!Settings.isAlarmDisabled(Settings.LOW_CHARGE_SOUND_MODE, Settings.LOW_CHARGE_VIBRO_MODE, settings)) {
							startInsist();
						}
					}
					break;
				}
				case STATE_CHARGING: {
					if (alwaysShowNotification) {
						notification.tickerText = null;
						updateNotification();
					}
					else {
						hideNotification();
					}
					break;
				}
				case STATE_OKAY: {
					if (alwaysShowNotification) {
						notification.tickerText = null;
						updateNotification();
					}
					else {
						hideNotification();
					}
					break;
				}
				case STATE_FULL: {
					boolean defaultNotifyFullBattery = getResources().getBoolean(R.bool.default_notify_full_battery);
					boolean notifyFullBattery = settings.getBoolean(Settings.NOTIFY_FULL_BATTERY, defaultNotifyFullBattery);
					if (notifyFullBattery || alwaysShowNotification) {
						notification.tickerText = getString(R.string.full_battery_level_ticker);
						if (!alwaysShowNotification) {
							notification.flags &= ~Notification.FLAG_NO_CLEAR;
							notification.flags |= Notification.FLAG_AUTO_CANCEL;
						}
						updateNotification();
						if (notifyFullBattery && stateChanged) {
							alarm(Settings.FULL_CHARGE_SOUND_MODE, Settings.FULL_CHARGE_VIBRO_MODE, Settings.FULL_CHARGE_RINGTONE);
						}
					}
					else {
						hideNotification();
					}
					break;
				}
			}
		}
	}

	void updateNotification() {
		Notification notification = this.notification;
		switch (batteryState) {
			case STATE_LOW: {
				notification.icon = getBatteryIcon(batteryLevel);
				String contentText;
				long unpluggedSince = this.unpluggedSince;
				if (unpluggedSince == 0) {
					contentText = getString(R.string.battery_level_notification_info, batteryLevel);
				}
				else {
					String since = DateUtils.formatDateTime(this, unpluggedSince, SINCE_TIME_FORMAT);
					contentText = getString(R.string.battery_level_notification_info_ex, batteryLevel, since);
				}
				notification.setLatestEventInfo(this, getString(R.string.battery_level_is_low), contentText, notification.contentIntent);
				break;
			}
			case STATE_CHARGING: {
				notification.icon = getBatteryIcon(batteryLevel);
				String contentText = getString(R.string.battery_is_charging_notification_info, batteryLevel);
				notification.setLatestEventInfo(this, getString(R.string.battery_is_charging), contentText, notification.contentIntent);
				break;
			}
			case STATE_OKAY: {
				notification.icon = getBatteryIcon(batteryLevel);
				String contentText;
				long unpluggedSince = this.unpluggedSince;
				if (unpluggedSince == 0) {
					contentText = getString(R.string.battery_level_notification_info, batteryLevel);
				}
				else {
					String since = DateUtils.formatDateTime(this, unpluggedSince, SINCE_TIME_FORMAT);
					contentText = getString(R.string.battery_level_is_okay_notification_info, batteryLevel, since);
				}
				notification.setLatestEventInfo(this, getString(R.string.battery_level_is_okay), contentText, notification.contentIntent);
				break;
			}
			case STATE_FULL: {
				notification.icon = R.drawable.battery_charge;
				String contentText = getString(R.string.battery_level_is_full_notification_info, batteryLevel);
				notification.setLatestEventInfo(this, getString(R.string.battery_level_is_full), contentText, notification.contentIntent);
				break;
			}
		}
		showNotification(notification);
	}

	public void alarm(String soundModeKey, String vibroModeKey, String ringtoneKey) {
		alarm(soundModeKey, vibroModeKey, ringtoneKey, settings, this);
	}

	public static void alarm(String soundModeKey, String vibroModeKey, String ringtoneKey, SharedPreferences settings, Context context) {
		AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		if (Settings.shouldVibrate(vibroModeKey, settings, audioManager)) {
			Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
			vibrator.vibrate(BatteryNotifierService.VIBRATE_PATTERN, -1);
		}
		int shouldSound = Settings.shouldSound(soundModeKey, settings, audioManager);
		if (shouldSound != Settings.SHOULD_SOUND_FALSE) {
			if (player == null) {
				player = new AsyncPlayer(TAG);
			}
			Uri ringtone = Settings.getRingtone(ringtoneKey, settings);
			int stream = shouldSound == Settings.SHOULD_SOUND_TRUE ? AudioManager.STREAM_ALARM : AudioManager.STREAM_NOTIFICATION;
			player.play(context, ringtone, false, stream);
		}

	}

}
