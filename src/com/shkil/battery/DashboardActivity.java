package com.shkil.battery;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.Window;

public class DashboardActivity extends Activity {

	public static final int DASHBOARD_DIALOG_ID = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		showDialog(DASHBOARD_DIALOG_ID, savedInstanceState);
	}

	@Override
	protected Dialog onCreateDialog(int id, Bundle savedInstanceState) {
		Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.dashboard);
		return dialog;
	}

}
