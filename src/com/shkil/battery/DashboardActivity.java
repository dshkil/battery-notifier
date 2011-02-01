package com.shkil.battery;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class DashboardActivity extends Activity implements OnClickListener {

	public static final int DASHBOARD_DIALOG_ID = 1;

	protected Dialog dialog;

	@Override
	protected void onResume() {
		super.onResume();
		showDialog(DASHBOARD_DIALOG_ID);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		dialog = new Dialog(this) {
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
		View snoozeAlertsButton = dialog.findViewById(R.id.snoozeAlertsButton);
		snoozeAlertsButton.setOnClickListener(this);
		View unsnoozeAlertsButton = dialog.findViewById(R.id.unsnoozeAlertsButton);
		unsnoozeAlertsButton.setOnClickListener(this);
		//View batteryUseButton = dialog.findViewById(R.id.batteryUseButton);
		//batteryUseButton.setOnClickListener(this);
		//View batteryInfoButton = dialog.findViewById(R.id.batteryInfoButton);
		//batteryInfoButton.setOnClickListener(this);
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
			case R.id.snoozeAlertsButton: {
				View snoozeAlertsButton = dialog.findViewById(R.id.snoozeAlertsButton);
				View snoozedText = dialog.findViewById(R.id.snoozedText);
				View unsnoozeAlertsButton = dialog.findViewById(R.id.unsnoozeAlertsButton);
				unsnoozeAlertsButton.setVisibility(View.VISIBLE);
				snoozedText.setVisibility(View.VISIBLE);
				snoozeAlertsButton.setVisibility(View.INVISIBLE);
				break;
			}
			case R.id.unsnoozeAlertsButton: {
				View snoozeAlertsButton = dialog.findViewById(R.id.snoozeAlertsButton);
				View snoozedText = dialog.findViewById(R.id.snoozedText);
				View unsnoozeAlertsButton = dialog.findViewById(R.id.unsnoozeAlertsButton);
				unsnoozeAlertsButton.setVisibility(View.INVISIBLE);
				snoozedText.setVisibility(View.INVISIBLE);
				snoozeAlertsButton.setVisibility(View.VISIBLE);
				Animation hyperspaceJump = AnimationUtils.loadAnimation(this, R.anim.unsnooze);
				snoozeAlertsButton.startAnimation(hyperspaceJump);
				break;
			}
		}
	}

}
