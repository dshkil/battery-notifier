package com.shkil.battery;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.util.Log;

public class Settings {

	public static final String SERVICE_ENABLED = "service_enabled";
	public static final String LOW_BATTERY_LEVEL = "low_battery_level";
	public static final String NOTIFY_FULL_BATTERY = "notify_full_battery";
	public static final String ALERT_INTERVAL = "alert_interval";
	public static final String ALERT_RINGTONE = "alert_ringtone";

	public static final String SOUND_MODE = "sound_mode";
	public static final String VIBRO_MODE = "vibro_mode";
	public static final int MODE_SYSTEM = 0;
	public static final int MODE_ALWAYS = 1;
	public static final int MODE_NEVER = 2;
	public static final int MODE_NORMAL_ONLY = 3;
	public static final int MODE_SILENT_ONLY = 4;

	public static Uri getAlertRingtone(SharedPreferences settings) {
		String ringtone = settings.getString(Settings.ALERT_RINGTONE, null);
		if (ringtone != null) {
			return Uri.parse(ringtone);
		}
		return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
	}

	public static boolean isSoundDisabled(SharedPreferences settings) {
		return String.valueOf(MODE_NEVER).equals(settings.getString(SOUND_MODE, "0"));
	}

	public static boolean isVibroDisabled(SharedPreferences settings) {
		return String.valueOf(MODE_NEVER).equals(settings.getString(VIBRO_MODE, "0"));
	}

	public static boolean shouldSound(SharedPreferences settings, AudioManager audioManager) {
		try {
			int mode = Integer.parseInt(settings.getString(SOUND_MODE, "0"));
			switch (mode) {
				case MODE_SYSTEM:
				case MODE_NORMAL_ONLY:
					return audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
				case MODE_ALWAYS:
					return true;
				case MODE_NEVER:
					return false;
				case MODE_SILENT_ONLY:
					return audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
			}
		}
		catch (Exception ex) {
			Log.w(Settings.class.getSimpleName(), "", ex);
		}
		return false;
	}

	public static boolean shouldVibrate(SharedPreferences settings, AudioManager audioManager) {
		try {
			int mode = Integer.parseInt(settings.getString(VIBRO_MODE, "0"));
			switch (mode) {
				case MODE_SYSTEM:
					return audioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_NOTIFICATION);
				case MODE_ALWAYS:
					return true;
				case MODE_NEVER:
					return false;
				case MODE_NORMAL_ONLY:
					return audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
				case MODE_SILENT_ONLY:
					switch (audioManager.getRingerMode()) {
						case AudioManager.RINGER_MODE_SILENT:
						case AudioManager.RINGER_MODE_VIBRATE:
							return true;
					}
					return false;
			}
		}
		catch (Exception ex) {
			Log.w(Settings.class.getSimpleName(), "", ex);
		}
		return false;
	}

}
