package com.shkil.battery;

import android.content.SharedPreferences;
import android.net.Uri;

public class Settings {

	public static final String SERVICE_ENABLED = "service_enabled";
	public static final String LOW_BATTERY_LEVEL = "low_battery_level";
	public static final String NOTIFY_FULL_BATTERY = "notify_full_battery";
	public static final String ALERT_INTERVAL = "alert_interval";
	public static final String ALERT_VIBRO_ON = "alert_vibro_on";
	public static final String ALERT_SOUND_ON = "alert_sound_on";
	public static final String ALERT_RINGTONE = "alert_ringtone";

	public static Uri getAlertRingtone(SharedPreferences settings) {
		String ringtone = settings.getString(Settings.ALERT_RINGTONE, null);
		if (ringtone != null) {
			return Uri.parse(ringtone);
		}
		return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
	}

}
