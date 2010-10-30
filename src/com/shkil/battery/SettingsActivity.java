package com.shkil.battery;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_title);
		addPreferencesFromResource(R.xml.settings);
		updateSummary();
		new Thread() {
			@Override
			public void run() {
				SharedPreferences settings = getPreferenceScreen().getSharedPreferences();
				if (settings.getBoolean(Settings.ENABLED, true)) {
					startService(new Intent(SettingsActivity.this, BatteryNotifierService.class));
				}
			}
		}.start();
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
		if (Settings.ENABLED.equals(key)) {
			if (settings.getBoolean(Settings.ENABLED, true)) {
				startService(new Intent(this, BatteryNotifierService.class));
			}
			else {
				stopService(new Intent(this, BatteryNotifierService.class));
			}
			updateSummary();
		}
		else if (Settings.LOW_BATTERY_LEVEL.equals(key) || Settings.INTERVAL.equals(key)) {
			updateSummary();
		}
	}

	protected void updateSummary() {
		PreferenceScreen preferenceScreen = getPreferenceScreen();
		SharedPreferences settings = preferenceScreen.getSharedPreferences();

		boolean isEnabled = settings.getBoolean(Settings.ENABLED, true); //FIXME check if service is actually running
		int isEnabledSummary = isEnabled ? R.string.service_is_running : R.string.service_is_stopped;
		preferenceScreen.findPreference(Settings.ENABLED).setSummary(isEnabledSummary);

		String lowLevelValue = settings.getString(Settings.LOW_BATTERY_LEVEL, null);
		String lowLevelSummary = getString(R.string.low_battery_level_summary, lowLevelValue);
		preferenceScreen.findPreference(Settings.LOW_BATTERY_LEVEL).setSummary(lowLevelSummary);

		ListPreference intervalPreference = (ListPreference) preferenceScreen.findPreference(Settings.INTERVAL);
		String intervalSummary = getString(R.string.interval_summary, intervalPreference.getEntry());
		intervalPreference.setSummary(intervalSummary);
	}

}