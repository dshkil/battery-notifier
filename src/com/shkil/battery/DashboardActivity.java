package com.shkil.battery;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class DashboardActivity extends Activity {

	static final int DASHBOARD_DIALOG_ID = 1;

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (settings.getBoolean(Settings.STARTED, true) && !BatteryNotifierService.isRunning(this)) {
			new Thread() {
				@Override
				public void run() {
					BatteryNotifierService.start(DashboardActivity.this);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(DashboardActivity.this, R.string.service_started, Toast.LENGTH_SHORT).show();
						}
					});
				}
			}.start();
		}
		showDialog(DASHBOARD_DIALOG_ID);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		return new DashboardDialog(this);
	}

}
