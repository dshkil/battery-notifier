package com.shkil.battery;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class BatteryNotifierService extends Service {

	private final IBinder binder = new Binder();
	private final int notifyId = 1; 
	private boolean notifyVisible;

	private static final String TAG = BatteryNotifierService.class.getSimpleName();

	private final BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
			switch (status) {
				case BatteryManager.BATTERY_STATUS_CHARGING:
				case BatteryManager.BATTERY_STATUS_FULL:
//					return;
			}
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			int icon = intent.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, 0);
			Log.v(TAG, "level=" + level + ",scale=" + scale + ",status=" + status);
			int percent = level * 100 / scale;
			if (percent < 90 && !notifyVisible) {
				notifyVisible = true;
				NotificationManager notifyService = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
				PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, SettingsActivity.class), 0);
				Notification notification = new Notification(icon, "ticker text (splash)", System.currentTimeMillis());
				notification.flags |= Notification.FLAG_NO_CLEAR;
				// Set the info for the views that show in the notification panel.
				notification.setLatestEventInfo(context, "content title", "content text", pendingIntent);
				notifyService.notify(notifyId, notification);
				Log.d(TAG, "Notification added");
			}
//            Log.i("BatteryNotifierService", action);
//            	int level = intent.getIntExtra("level", -1);
//            	int scale = intent.getIntExtra("scale", 100);
//            	percent = level * 100 / scale;
//            	mHandler.post(mUpdateStatus);
//            	/* Give the service a second to process the update */
//            	mHandler.postDelayed(mUpdateStatus, 1 * 1000);
//            	/* Just in case 1 second wasn't enough */
//            	mHandler.postDelayed(mUpdateStatus, 4 * 1000);
//            }
		}
	};

	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
		registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		unregisterReceiver(batteryInfoReceiver);
		NotificationManager notifyService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notifyService.cancel(notifyId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind");
		return binder;
	}

}
