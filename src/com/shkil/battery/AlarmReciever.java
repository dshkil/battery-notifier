package com.shkil.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class AlarmReciever extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		BatteryNotifierService.alarm(Settings.LOW_CHARGE_SOUND_MODE, Settings.LOW_CHARGE_VIBRO_MODE, settings, context);
	}

}
