package com.shkil.battery;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_title);
		addPreferencesFromResource(R.xml.settings);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		if (Settings.ENABLED_KEY.equals(key)) {
			Context context = getApplicationContext();
			if (settings.getBoolean(Settings.ENABLED_KEY, true)) {
				context.startService(new Intent(context, BatteryNotifierService.class));
			}
			else {
				context.stopService(new Intent(context, BatteryNotifierService.class));
			}
		}
	}

}