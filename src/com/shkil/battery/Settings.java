package com.shkil.battery;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.util.Log;

public class Settings {

	public static final String STARTED = "service_started";
	public static final String START_AT_BOOT = "start_at_boot";
	public static final String ALWAYS_SHOW_ICON = "always_show_icon";
	public static final String LOW_BATTERY_LEVEL = "low_battery_level";
	public static final String NOTIFY_FULL_BATTERY = "notify_full_battery";
	public static final String ALERT_INTERVAL = "alert_interval";
	public static final String ALERT_RINGTONE = "alert_ringtone";
	public static final String SHOW_LEVEL_IN_ICON = "show_level_in_icon";

	public static final String LOW_CHARGE_SOUND_MODE = "sound_mode";
	public static final String LOW_CHARGE_VIBRO_MODE = "vibro_mode";
	public static final String FULL_CHARGE_SOUND_MODE = "full_charge_sound_mode";
	public static final String FULL_CHARGE_VIBRO_MODE = "full_charge_vibro_mode";

	public static final int MODE_SYSTEM = 0;
	public static final int MODE_ALWAYS = 1;
	public static final int MODE_NEVER = 2;
	public static final int MODE_NORMAL_ONLY = 3;
	public static final int MODE_SILENT_ONLY = 4;

	public static final int SHOULD_SOUND_FALSE = 0;
	public static final int SHOULD_SOUND_TRUE = 1;
	public static final int SHOULD_SOUND_SYSTEM = 2;

	private static final String MODE_NEVER_STR = String.valueOf(MODE_NEVER);

	public static Uri getAlertRingtone(SharedPreferences settings) {
		String ringtone = settings.getString(Settings.ALERT_RINGTONE, null);
		if (ringtone != null) {
			return Uri.parse(ringtone);
		}
		return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
	}

	public static boolean isAlarmDisabled(String soundModeKey, String vibroModeKey, SharedPreferences settings) {
		return MODE_NEVER_STR.equals(settings.getString(soundModeKey, null)) && MODE_NEVER_STR.equals(settings.getString(vibroModeKey, null));
	}

	public static int shouldSound(String soundModeKey, SharedPreferences settings, AudioManager audioManager) {
		try {
			int mode = Integer.parseInt(settings.getString(soundModeKey, "0"));
			switch (mode) {
				case MODE_SYSTEM:
					return SHOULD_SOUND_SYSTEM;
				case MODE_NORMAL_ONLY:
					return audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL ? SHOULD_SOUND_TRUE : SHOULD_SOUND_FALSE;
				case MODE_ALWAYS:
					return SHOULD_SOUND_TRUE;
				case MODE_NEVER:
					return SHOULD_SOUND_FALSE;
				case MODE_SILENT_ONLY:
					return audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT ? SHOULD_SOUND_TRUE : SHOULD_SOUND_FALSE;
			}
		}
		catch (Exception ex) {
			Log.w(Settings.class.getSimpleName(), "", ex);
		}
		return SHOULD_SOUND_SYSTEM;
	}

	public static boolean shouldVibrate(String vibroModeKey, SharedPreferences settings, AudioManager audioManager) {
		try {
			int mode = Integer.parseInt(settings.getString(vibroModeKey, "0"));
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
