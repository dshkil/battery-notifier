package com.shkil.battery;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class DashboardActivity extends Activity implements OnClickListener {

	public static final int DASHBOARD_DIALOG_ID = 1;

	@Override
	protected void onResume() {
		super.onResume();
		showDialog(DASHBOARD_DIALOG_ID);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = new Dialog(this) {
			@Override
			public boolean onCreateOptionsMenu(Menu menu) {
				menu.add(R.string.settings_title).setIntent(new Intent(getBaseContext(), SettingsActivity.class));
				return true;
			}
			@Override
			public void onDetachedFromWindow() {
				super.onDetachedFromWindow();
				finish();
			}
		};
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.dashboard);
		Button batteryUseButton = (Button) dialog.findViewById(R.id.batteryUseButton);
		TextView batteryInfoButton = (TextView) dialog.findViewById(R.id.batteryInfoButton);
		batteryUseButton.setOnClickListener(this);
		batteryInfoButton.setOnClickListener(this);
		return dialog;
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.batteryUseButton:
				startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.fuelgauge.PowerUsageSummary"));
				//finish();
				break;
			case R.id.batteryInfoButton:
				startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.BatteryInfo"));
				//finish();
				break;
		}
	}

}
