package com.shkil.battery;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

public class BootCompletedReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		if (settings.getBoolean(Settings.START_AT_BOOT, true)) {
			BatteryNotifierService.start(context);
		}
		else {
			setReceiverEnabled(false, context); //TODO obsolete in next versions
		}
	}

	public static boolean isReceiverEnabled(Context context) {
		PackageManager packageManager = context.getPackageManager();
		int bootCompletedReceiverState = packageManager.getComponentEnabledSetting(new ComponentName(context, BootCompletedReceiver.class));
		return bootCompletedReceiverState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
	}

	public static void setReceiverEnabled(boolean enabled, Context context) {
		int enabledState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
		ComponentName bootCompletedReceiverName = new ComponentName(context, BootCompletedReceiver.class);
		context.getPackageManager().setComponentEnabledSetting(bootCompletedReceiverName, enabledState, PackageManager.DONT_KILL_APP);
	}

}
