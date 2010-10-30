package com.shkil.battery;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Audio;
import android.util.Log;

public class BatteryNotifierService extends Service implements OnSharedPreferenceChangeListener {

	static final String TAG = BatteryNotifierService.class.getSimpleName();
	static final int BATTERY_LOW_NOTIFY_ID = 1; 
	static final long[] VIBRATE_PATTERN = new long[] {0,50,200,100};

	boolean isBatteryLow;
	int lowBatteryLevel;
	int insistInterval;
	int lastBatteryLevel = -1;

	final Handler handler = new Handler();
	AsyncPlayer player;
	Uri sound = Uri.withAppendedPath(Audio.Media.INTERNAL_CONTENT_URI, "6");

	final BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
				if (isBatteryLow) {
					isBatteryLow = false;
					onBatteryOkay();
					lastBatteryLevel = -1; //FIXME #debug remove?
				}
			}
			else if (level != lastBatteryLevel) {
				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				int percent = level * 100 / scale;
				Log.v(TAG, "Level changed. level=" + percent + "%, status=" + status);
				if (percent <= lowBatteryLevel) { //FIXME #debug above only
					if (!isBatteryLow) {
						isBatteryLow = true;
						onBatteryLow();
					}
				}
				else if (isBatteryLow) {
					isBatteryLow = false;
					onBatteryOkay();
				}
			}
			lastBatteryLevel = level;
		}
	};

	final Runnable insistRunnable = new Runnable() {
		@Override
		public void run() {
			onInsist();
			if (isBatteryLow) {
				handler.postDelayed(this, insistInterval);
			}
		}
	};

	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		updateValuesFromSettings(settings);
		registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		settings.registerOnSharedPreferenceChangeListener(this);
		//onBatteryLow(); //FIXME #debug remove
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
		if (Settings.LOW_BATTERY_LEVEL.equals(key) || Settings.INTERVAL.equals(key)) {
			updateValuesFromSettings(settings);
		}
	}

	void updateValuesFromSettings(SharedPreferences settings) {
		Log.v(TAG, "Updating values from settings...");
		try {
			String lowLevelValue = settings.getString(Settings.LOW_BATTERY_LEVEL, null);
			if (lowLevelValue == null) {
				lowLevelValue = getString(R.string.default_low_level);
			}
			lowBatteryLevel = Integer.parseInt(lowLevelValue);
		}
		catch (NumberFormatException ex) {
			lowBatteryLevel = getResources().getInteger(R.string.default_low_level);
		}
		try {
			String intervalValue = settings.getString(Settings.INTERVAL, null);
			if (intervalValue == null) {
				intervalValue = getString(R.string.default_interval);
			}
			setInsistInterval(Integer.parseInt(intervalValue));
		}
		catch (NumberFormatException ex) {
			setInsistInterval(getResources().getInteger(R.string.default_interval));
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
		NotificationManager notifyService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
		Notification notification = new Notification(
			R.drawable.battery_low, getString(R.string.low_battery_level_ticker), System.currentTimeMillis()
		);
		notification.flags |= Notification.FLAG_NO_CLEAR;
		Resources resources = getResources();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean shouldInsist = false;
		if (settings.getBoolean(Settings.VIBRATE, resources.getBoolean(R.bool.default_vibrate))) {
			notification.vibrate = VIBRATE_PATTERN;
			shouldInsist = true;
		}
		if (settings.getBoolean(Settings.SOUND, resources.getBoolean(R.bool.default_sound))) {
			notification.sound = sound;
			shouldInsist = true;
		}
		notification.setLatestEventInfo(this, getString(R.string.battery_level_is_low), "", contentIntent);
		notifyService.notify(BATTERY_LOW_NOTIFY_ID, notification);
		if (canInsist && shouldInsist) {
			startInsist();
		}
	}

	void setInsistInterval(int interval) {
		this.insistInterval = interval;
		if (isBatteryLow) {
			startInsist();
		}
	}

	void startInsist() {
		handler.removeCallbacks(insistRunnable);
		handler.postDelayed(insistRunnable, insistInterval);
	}

	void stopInsist() {
		handler.removeCallbacks(insistRunnable);
	}

	void onInsist() {
		Log.d(TAG, "onInsist");
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Resources resources = getResources();
		if (settings.getBoolean(Settings.VIBRATE, resources.getBoolean(R.bool.default_vibrate))) {
			AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
			int vibrateSetting = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION);
			if (vibrateSetting != AudioManager.VIBRATE_SETTING_OFF) {
				Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
				vibrator.vibrate(VIBRATE_PATTERN, -1);
			}
		}
		if (settings.getBoolean(Settings.SOUND, resources.getBoolean(R.bool.default_sound))) {
			if (player == null) {
				player = new AsyncPlayer("insist");
			}
			player.play(this, sound, false, AudioManager.STREAM_NOTIFICATION);
		}
	}

}
