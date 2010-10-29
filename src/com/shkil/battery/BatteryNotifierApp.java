package com.shkil.battery;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BatteryNotifierApp extends Application {

	@Override
	public void onCreate() {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (settings.getBoolean(Settings.ENABLED_KEY, true)) {
			startService(new Intent(this, BatteryNotifierService.class));
		}
	}

}
