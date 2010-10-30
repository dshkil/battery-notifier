package com.shkil.battery;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_title);
		addPreferencesFromResource(R.xml.settings);
		updateSummary();
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
			updateSummary();
		}
		else if (Settings.LOW_BATTERY_LEVEL_KEY.equals(key)) {
			updateSummary();
		}
	}

	protected void updateSummary() {
		PreferenceScreen preferenceScreen = getPreferenceScreen();
		SharedPreferences settings = preferenceScreen.getSharedPreferences();

		boolean isEnabled = settings.getBoolean(Settings.ENABLED_KEY, true); //FIXME check if service is running
		int isEnabledSummary = isEnabled ? R.string.service_is_running : R.string.service_is_stopped;
		preferenceScreen.findPreference(Settings.ENABLED_KEY).setSummary(isEnabledSummary);

		String lowLevelValue = settings.getString(Settings.LOW_BATTERY_LEVEL_KEY, "30");
		String lowLevelSummary = getString(R.string.low_battery_level_summary, lowLevelValue);
		preferenceScreen.findPreference(Settings.LOW_BATTERY_LEVEL_KEY).setSummary(lowLevelSummary);
	}

}