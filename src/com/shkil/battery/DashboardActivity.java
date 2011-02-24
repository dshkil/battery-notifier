package com.shkil.battery;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class DashboardActivity extends Activity {

	static final int DASHBOARD_DIALOG_ID = 1;

	private DashboardDialog dialog;

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (settings.getBoolean(Settings.SERVICE_STARTED, true) && !BatteryNotifierService.isRunning(this)) {
			BatteryNotifierService.start(DashboardActivity.this);
			Toast.makeText(DashboardActivity.this, R.string.service_started, Toast.LENGTH_SHORT).show();
		}
		showDialog(DASHBOARD_DIALOG_ID);
		dialog.onActivityResume();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		return dialog = new DashboardDialog(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		dialog.onActivityPause();
	}

}
