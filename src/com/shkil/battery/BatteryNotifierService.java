package com.shkil.battery;

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
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class BatteryNotifierService extends Service implements OnSharedPreferenceChangeListener {

	private final class BatteryInfoReceiver extends BroadcastReceiver {
		private int lastRawLevel;
		private int lastStatus;
		@Override
		public void onReceive(Context context, Intent intent) {
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
			boolean statusChanged = status != lastStatus;
			boolean levelChanged = level != lastRawLevel;
			boolean pluggedChanged = plugged != powerSource;
			if (levelChanged || statusChanged || pluggedChanged) {
				lastRawLevel = level;
				powerSource = plugged;
				//Log.v(TAG, "batteryInfoReceiver: status=" + status + ", level=" + level);
				if (levelChanged) {
					batteryLevel = level * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
					if (showLevelInIcon) {
						notification.number = batteryLevel;
					}
				}
				if (status == BatteryManager.BATTERY_STATUS_FULL) {
					if (statusChanged || pluggedChanged) {
						setBatteryState(plugged == 0 ? STATE_OKAY : STATE_FULL, false);
					}
					else if (notificationVisible) {
						updateNotification();
					}
				}
				else {
					if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
						if (statusChanged) {
							unpluggedSince = 0;
							if (pluggedSince == 0 && lastStatus > 0) {
								pluggedSince = System.currentTimeMillis();
							}
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
									pluggedSince = 0;
									if (unpluggedSince == 0) {
										unpluggedSince = System.currentTimeMillis();
									}
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
				lastStatus = status;
			}
		}
		public void reset() {
			lastRawLevel = lastStatus = 0;
		}
	}

	static final String TAG = BatteryNotifierService.class.getSimpleName();
	static final String CLASS = BatteryNotifierService.class.getName();

	static final long[] VIBRATE_PATTERN = new long[] {0,50,200,100};

	static final int NOTIFICATION_ID = 1;
	static final int DEFAULT_NOTIFICATION_FLAGS = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

	static final int STATE_OKAY = 1;
	static final int STATE_LOW = 2;
	static final int STATE_CHARGING = 3;
	static final int STATE_FULL = 4;

	static final int SINCE_TIME_FORMAT = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH;

	private static volatile BatteryNotifierService instance;

	// Cached settings values
	int lowBatteryLevel;
	int insistInterval;
	long mutedUntilTimeMillis;
	boolean alwaysShowNotification;
	boolean showLevelInIcon;

	// Battery state information
	int batteryState;
	int batteryLevel;
	int powerSource;
	static volatile long pluggedSince;
	static volatile long unpluggedSince;

	SharedPreferences settings;
	NotificationManager notificationService;
	Notification notification;
	boolean notificationVisible;
	PendingIntent insistTimerPendingIntent;
	boolean insistTimerActive;
	static AsyncPlayer player;

	static Method startForegroundMethod;
	static Method stopForegroundMethod;
	static final Object[] startForegroundArgs = new Object[2];
	static final Object[] stopForegroundArgs = new Object[] {Boolean.TRUE};
	static {
		try {
			startForegroundMethod = Service.class.getMethod("startForeground", new Class[] {int.class, Notification.class});
			stopForegroundMethod = Service.class.getMethod("stopForeground", new Class[] {boolean.class});
		}
		catch (NoSuchMethodException e) {
			startForegroundMethod = null;
		}
	}

	final BatteryInfoReceiver batteryInfoReceiver = new BatteryInfoReceiver();
	private int dateTimeViewId;	

	void checkBatteryLevel() {
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
		dateTimeViewId = findEventInfoDateViewId(this);
		insistTimerPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, AlarmReceiver.class), 0);
		notificationService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notification = new Notification();
		notification.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, DashboardActivity.class), 0);
		notification.defaults = 0;
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		updateValuesFromSettings(settings, null);
		batteryInfoReceiver.reset();
		registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		settings.registerOnSharedPreferenceChangeListener(this);
		instance = this;
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		instance = null;
		unregisterReceiver(batteryInfoReceiver);
		stopInsist();
		settings.unregisterOnSharedPreferenceChangeListener(this);
		hideNotification();
		if (stopForegroundMethod == null) {
			setForeground(false);
		}
		unpluggedSince = 0;
		pluggedSince = 0;
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
					stopInsist();
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
			if (key == null || Settings.MUTED_UNTIL_TIME.equals(key)) {
				mutedUntilTimeMillis = settings.getLong(Settings.MUTED_UNTIL_TIME, 0);
				if (insistTimerActive) {
					startInsist();
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
		if (player != null) {
			player.stop();
		}
		long firstTime = System.currentTimeMillis() + insistInterval;
		if (mutedUntilTimeMillis > 0) {
			firstTime = Math.max(firstTime, mutedUntilTimeMillis);
		}
		alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, firstTime, insistInterval, insistTimerPendingIntent);
	}

	void stopInsist() {
		if (insistTimerActive) {
			insistTimerActive = false;
			AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
			alarmManager.cancel(insistTimerPendingIntent);
		}
		if (player != null) {
			player.stop();
		}
	}

	public static boolean isStarted() {
		return instance != null;
	}

	public static BatteryNotifierService getInstance() {
		return instance;
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
		settings.edit().putBoolean(Settings.SERVICE_STARTED, true).commit();
	}

	public static void stop(Context context) {
		context.stopService(new Intent(context, BatteryNotifierService.class));
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		settings.edit().putBoolean(Settings.SERVICE_STARTED, false).commit();
	}

	/**
	 * This is a wrapper around the new startForeground method, using the older APIs if it is not available.
	 */
	public void startForegroundCompat(int id, Notification notification) {
		// If we have the new startForeground API, then use it
		if (startForegroundMethod != null) {
			try {
				Object[] startForegroundArgs = BatteryNotifierService.startForegroundArgs;
				startForegroundArgs[0] = Integer.valueOf(id);
				startForegroundArgs[1] = notification;
				startForegroundMethod.invoke(this, startForegroundArgs);
			}
			catch (Exception e) {
				Log.e(TAG, "Unable to invoke startForeground", e);
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
				stopForegroundMethod.invoke(this, stopForegroundArgs);
			}
			catch (Exception e) {
				Log.e(TAG, "Unable to invoke stopForeground", e);
			}
		}
		else {
			// Fall back on the old API
			notificationService.cancel(id);
		}
	}

	public static int getBatteryIcon(int level) {
		if (level >= 90) {
			return R.drawable.battery_good;
		}
		if (level >= 50) {
			return R.drawable.battery_above_half;
		}
		return level > 25 ? R.drawable.battery_below_half : R.drawable.battery_low;
	}

	private void showNotification(Notification notification) {
		long hiddenTime = Build.VERSION.SDK_INT >= 9 ? -Long.MAX_VALUE : Long.MAX_VALUE;
		notification.when = hiddenTime;
		notification.icon = -1;
		if (dateTimeViewId > 0) {
			notification.contentView.setViewVisibility(dateTimeViewId, View.GONE);
		}
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
				notification.when = System.currentTimeMillis();
				stopInsist();
			}
			notification.flags = DEFAULT_NOTIFICATION_FLAGS;
			switch (state) {
				case STATE_LOW: {
					if (stateChanged) {
						notification.tickerText = getString(R.string.low_battery_level_ticker);
						updateNotification();
						if (System.currentTimeMillis() >= mutedUntilTimeMillis && !Settings.isQuietHoursActive(settings, Settings.MUTE_LOW_CHARGE_ALERTS)) {
							alarm(Settings.LOW_CHARGE_SOUND_MODE, Settings.LOW_CHARGE_VIBRO_MODE, Settings.LOW_CHARGE_RINGTONE);
						}
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
						if (notifyFullBattery && stateChanged && System.currentTimeMillis() >= mutedUntilTimeMillis
							&& !Settings.isQuietHoursActive(settings, Settings.MUTE_FULL_CHARGE_ALERTS))
						{
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
				long unpluggedSince = BatteryNotifierService.unpluggedSince;
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
				long unpluggedSince = BatteryNotifierService.unpluggedSince;
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

	public static long getPluggedSince() {
		return pluggedSince;
	}

	public static long getUnpluggedSince() {
		return unpluggedSince;
	}

	public static void playerStop() {
		if (player != null) {
			player.stop();
		}
	}

	public int getBatteryState() {
		return batteryState;
	}

	private static int findDateViewId(ViewGroup parent) {
		int c = parent.getChildCount();
		for (int i = 0; i < c; i++) {
			View child = parent.getChildAt(i);
			String childClassName = child.getClass().getName();
			if (childClassName.contains("DateTime")) {
				return child.getId();
			}
			if (child instanceof ViewGroup) {
				int viewId = findDateViewId((ViewGroup) child);
				if (viewId > 0) {
					return viewId;
				}
			}
		}
		return -1;
	}

	public static int findEventInfoDateViewId(Context context) {
		try {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			Notification n = new Notification();
			n.setLatestEventInfo(context, "", "", null);
			ViewGroup contentViewLayout = (ViewGroup) inflater.inflate(n.contentView.getLayoutId(), null);
			return findDateViewId(contentViewLayout);
		}
		catch (RuntimeException ex) {
			return -2;
		}
	}

}
