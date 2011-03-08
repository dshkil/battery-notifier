package com.shkil.battery;

import static com.shkil.battery.Settings.LOW_CHARGE_RINGTONE;
import static com.shkil.battery.Settings.LOW_CHARGE_SOUND_MODE;
import static com.shkil.battery.Settings.LOW_CHARGE_VIBRO_MODE;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class AlarmReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		if (!Settings.isQuietHoursActive(settings, Settings.MUTE_LOW_CHARGE_ALERTS)) {
			BatteryNotifierService.alarm(LOW_CHARGE_SOUND_MODE, LOW_CHARGE_VIBRO_MODE, LOW_CHARGE_RINGTONE, settings, context);
		}
	}

}
