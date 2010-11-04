package com.shkil.battery;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings.System;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
		updateSummary();
		updateRingtoneSummary();
		new Thread() {
			@Override
			public void run() {
				SharedPreferences settings = getPreferenceScreen().getSharedPreferences();
				if (settings.getBoolean(Settings.SERVICE_ENABLED, true)) {
					startService(new Intent(SettingsActivity.this, BatteryNotifierService.class));
				}
			}
		}.start();
		Preference aboutPreference = getPreferenceScreen().findPreference("about");
		aboutPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference pref) {
				showAlertDialog(R.string.about_message, R.string.about);
				return true;
			}
		});
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			aboutPreference.setSummary(getString(R.string.about_summary, info.versionName));
		} catch (NameNotFoundException e) {
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateRingtoneSummary();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		if (Settings.SERVICE_ENABLED.equals(key)) {
			if (settings.getBoolean(Settings.SERVICE_ENABLED, true)) {
				startService(new Intent(this, BatteryNotifierService.class));
			}
			else {
				stopService(new Intent(this, BatteryNotifierService.class));
			}
		}
		else if (Settings.LOW_BATTERY_LEVEL.equals(key) || Settings.ALERT_INTERVAL.equals(key)) {
			updateSummary();
		}
		else if (Settings.VIBRO_MODE.equals(key) || Settings.SOUND_MODE.equals(key)) {
			updateSummary();
		}
	}

	protected void updateSummary() {
		PreferenceScreen preferenceScreen = getPreferenceScreen();
		SharedPreferences settings = preferenceScreen.getSharedPreferences();
		Resources resources = getResources();

		int lowLevelValue = settings.getInt(Settings.LOW_BATTERY_LEVEL, resources.getInteger(R.integer.default_low_level));
		String lowLevelSummary;
		if (lowLevelValue == 0) {
			lowLevelSummary = getString(R.string.low_battery_level_disabled);
		}
		else {
			lowLevelSummary = getString(R.string.low_battery_level_summary, lowLevelValue);
		}
		preferenceScreen.findPreference(Settings.LOW_BATTERY_LEVEL).setSummary(lowLevelSummary);

		ListPreference intervalPreference = (ListPreference) preferenceScreen.findPreference(Settings.ALERT_INTERVAL);
		String intervalSummary = getString(R.string.alert_interval_summary, intervalPreference.getEntry());
		intervalPreference.setSummary(intervalSummary);

		ListPreference vibroModePreference = (ListPreference) preferenceScreen.findPreference(Settings.VIBRO_MODE);
		String vibroModeValue = vibroModePreference.getValue();
		String vibroModeSummary;
		if (String.valueOf(Settings.MODE_ALWAYS).equals(vibroModeValue)) {
			vibroModeSummary = getString(R.string.mode_always_summary);
		}
		else if (String.valueOf(Settings.MODE_NEVER).equals(vibroModeValue)) {
			vibroModeSummary = getString(R.string.mode_never_summary);
		}
		else {
			vibroModeSummary = getString(R.string.vibro_mode_summary, vibroModePreference.getEntry());
		}
		vibroModePreference.setSummary(vibroModeSummary);

		ListPreference soundModePreference = (ListPreference) preferenceScreen.findPreference(Settings.SOUND_MODE);
		String sounfModeValue = soundModePreference.getValue();
		String soundModeSummary;
		if (String.valueOf(Settings.MODE_ALWAYS).equals(sounfModeValue)) {
			soundModeSummary = getString(R.string.mode_always_summary);
		}
		else if (String.valueOf(Settings.MODE_NEVER).equals(sounfModeValue)) {
			soundModeSummary = getString(R.string.mode_never_summary);
		}
		else {
			soundModeSummary = getString(R.string.vibro_mode_summary, soundModePreference.getEntry());
		}
		soundModePreference.setSummary(soundModeSummary);
	}

	protected void updateRingtoneSummary() {
		PreferenceScreen preferenceScreen = getPreferenceScreen();
		SharedPreferences settings = preferenceScreen.getSharedPreferences();
		Preference ringtonePreference = preferenceScreen.findPreference(Settings.ALERT_RINGTONE);
		String ringtone = settings.getString(Settings.ALERT_RINGTONE, null);
		if (ringtone == null || System.DEFAULT_NOTIFICATION_URI.toString().equals(ringtone)) {
			ringtonePreference.setSummary(R.string.alert_ringtone_is_default);
		}
		else {
			ringtonePreference.setSummary(R.string.alert_ringtone_is_custom);
		}
	}

	void showAlertDialog(int messageId, int titleId) {
		new AlertDialog.Builder(this)
		.setMessage(messageId)
		.setTitle(titleId)
		.setNeutralButton(android.R.string.ok, null)
		.create()
		.show();
	}

}