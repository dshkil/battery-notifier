package com.shkil.battery;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings.System;
import android.util.Log;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	final Handler handler = new Handler();
	Preference serviceStatePreference;
	Preference serviceOptionsPreference;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
		updateSummary();
		updateRingtoneSummary();
		final PreferenceScreen preferenceScreen = getPreferenceScreen();
		serviceOptionsPreference = preferenceScreen.findPreference("service_options");
		serviceStatePreference = preferenceScreen.findPreference("service_state");
		serviceStatePreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference pref) {
				toggleServiceStatus();
				return true;
			}
		});
		Preference aboutPreference = preferenceScreen.findPreference("about");
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
		}
		catch (NameNotFoundException ex) {
			Log.e(SettingsActivity.class.getSimpleName(), "", ex);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateRingtoneSummary();
		SharedPreferences settings = getPreferenceScreen().getSharedPreferences();
		settings.registerOnSharedPreferenceChangeListener(this);
		updateServiceStatus(false);
		if (settings.getBoolean(Settings.STARTED, true) && !BatteryNotifierService.isRunning(this)) {
			new Thread() {
				@Override
				public void run() {
					BatteryNotifierService.start(SettingsActivity.this);
					handler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(SettingsActivity.this, R.string.service_started, Toast.LENGTH_SHORT).show();
							updateServiceStatus(false);
						}
					});
				}
			}.start();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
		if (Settings.LOW_BATTERY_LEVEL.equals(key) || Settings.ALERT_INTERVAL.equals(key)) {
			updateSummary();
		}
		else if (Settings.LOW_CHARGE_VIBRO_MODE.equals(key) || Settings.LOW_CHARGE_SOUND_MODE.equals(key)) {
			updateSummary();
		}
		else if (Settings.FULL_CHARGE_VIBRO_MODE.equals(key) || Settings.FULL_CHARGE_SOUND_MODE.equals(key)) {
			updateSummary();
		}
		else if (Settings.START_AT_BOOT.equals(key)) {
			boolean startAtBoot = settings.getBoolean(Settings.START_AT_BOOT, true);
			BootCompletedReceiver.setReceiverEnabled(startAtBoot, this);
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

		ListPreference lowVibroModePreference = (ListPreference) preferenceScreen.findPreference(Settings.LOW_CHARGE_VIBRO_MODE);
		updateModeSummary(lowVibroModePreference);

		ListPreference lowSoundModePreference = (ListPreference) preferenceScreen.findPreference(Settings.LOW_CHARGE_SOUND_MODE);
		updateModeSummary(lowSoundModePreference);
		boolean lowSoundEnabled = !String.valueOf(Settings.MODE_NEVER).equals(lowSoundModePreference.getValue());
		findPreference(Settings.LOW_CHARGE_RINGTONE).setEnabled(lowSoundEnabled);

		ListPreference fullVibroModePreference = (ListPreference) preferenceScreen.findPreference(Settings.FULL_CHARGE_VIBRO_MODE);
		updateModeSummary(fullVibroModePreference);

		ListPreference fullSoundModePreference = (ListPreference) preferenceScreen.findPreference(Settings.FULL_CHARGE_SOUND_MODE);
		updateModeSummary(fullSoundModePreference);
		boolean fullSoundEnabled = !String.valueOf(Settings.MODE_NEVER).equals(fullSoundModePreference.getValue());
		findPreference(Settings.FULL_CHARGE_RINGTONE).setEnabled(fullSoundEnabled);
	}

	private void updateModeSummary(ListPreference preference) {
		String vibroModeValue = preference.getValue();
		String vibroModeSummary;
		if (String.valueOf(Settings.MODE_ALWAYS).equals(vibroModeValue)) {
			vibroModeSummary = getString(R.string.mode_always_summary);
		}
		else if (String.valueOf(Settings.MODE_NEVER).equals(vibroModeValue)) {
			vibroModeSummary = getString(R.string.mode_never_summary);
		}
		else {
			vibroModeSummary = getString(R.string.mode_summary, preference.getEntry());
		}
		preference.setSummary(vibroModeSummary);
	}

	protected void updateRingtoneSummary() {
		PreferenceScreen preferenceScreen = getPreferenceScreen();
		SharedPreferences settings = preferenceScreen.getSharedPreferences();
		//low battery ringtone
		Preference lowRingtonePreference = preferenceScreen.findPreference(Settings.LOW_CHARGE_RINGTONE);
		String lowRingtone = settings.getString(Settings.LOW_CHARGE_RINGTONE, null);
		if (lowRingtone == null || System.DEFAULT_NOTIFICATION_URI.toString().equals(lowRingtone)) {
			lowRingtonePreference.setSummary(R.string.alert_ringtone_is_default);
		}
		else {
			lowRingtonePreference.setSummary(R.string.alert_ringtone_is_custom);
		}
		//full battery ringtone
		Preference fullRingtonePreference = preferenceScreen.findPreference(Settings.FULL_CHARGE_RINGTONE);
		String fullRingtone = settings.getString(Settings.FULL_CHARGE_RINGTONE, null);
		if (fullRingtone == null || System.DEFAULT_NOTIFICATION_URI.toString().equals(fullRingtone)) {
			fullRingtonePreference.setSummary(R.string.alert_ringtone_is_default);
		}
		else {
			fullRingtonePreference.setSummary(R.string.alert_ringtone_is_custom);
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

	void toggleServiceStatus() {
		int textId;
		if (BatteryNotifierService.isRunning(this)) {
			BatteryNotifierService.stop(this);
			textId = R.string.service_stopped;
		}
		else {
			BatteryNotifierService.start(this);
			textId = R.string.service_started;
		}
		Toast.makeText(this, textId, Toast.LENGTH_SHORT).show();
		updateServiceStatus(true);
	}

	void updateServiceStatus(boolean useWorkaround) {
		boolean isServiceRunning = BatteryNotifierService.isRunning(this);
		serviceStatePreference.setTitle(
			isServiceRunning ? R.string.stop_service : R.string.start_service
		);
		String summary = getString(isServiceRunning ? R.string.service_is_running : R.string.service_is_stopped);
		serviceStatePreference.setSummary(summary);
		if (!summary.equals(serviceOptionsPreference.getSummary())) {
			serviceOptionsPreference.setSummary(summary);
			if (useWorkaround) {
				onContentChanged(); //Workaround. See http://code.google.com/p/android/issues/detail?id=931
			}
		}
	}

}
