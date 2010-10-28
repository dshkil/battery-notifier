package com.shkil.battery;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class BatteryNotifierService extends Service {

    private static final BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("BatteryNotifierService", action);
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
//            	int level = intent.getIntExtra("level", -1);
//            	int scale = intent.getIntExtra("scale", 100);
//            	percent = level * 100 / scale;
//            	mHandler.post(mUpdateStatus);
//            	/* Give the service a second to process the update */
//            	mHandler.postDelayed(mUpdateStatus, 1 * 1000);
//            	/* Just in case 1 second wasn't enough */
//            	mHandler.postDelayed(mUpdateStatus, 4 * 1000);
            }
        }
    };

    private final IBinder binder = new Binder();

    @Override
    public void onCreate() {
    	Log.i("BatteryNotifierService", "onCreate");
    	registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    public void onDestroy() {
    	Log.i("BatteryNotifierService", "onDestroy");
    	unregisterReceiver(batteryInfoReceiver);
    }

    @Override
    public IBinder onBind(Intent paramIntent) {
    	Log.i("BatteryNotifierService", "onBind");
    	return binder;
    }

}
