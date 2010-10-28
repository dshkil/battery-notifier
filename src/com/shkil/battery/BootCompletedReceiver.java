package com.shkil.battery;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootCompletedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		if (settings.getBoolean("is_enabled", true)) {
			ComponentName comp = new ComponentName(context, BatteryNotifierService.class);
			context.startService(new Intent().setComponent(comp));
		}
	}

}
