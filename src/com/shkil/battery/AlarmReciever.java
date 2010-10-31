package com.shkil.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AsyncPlayer;
import android.media.AudioManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

public class AlarmReciever extends BroadcastReceiver {

	private static final String TAG = AlarmReciever.class.getSimpleName();
	private AsyncPlayer player;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive");
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		Resources resources = context.getResources();
		if (settings.getBoolean(Settings.VIBRATE, resources.getBoolean(R.bool.default_vibrate))) {
			AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			int vibrateSetting = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION);
			if (vibrateSetting != AudioManager.VIBRATE_SETTING_OFF) {
				Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
				vibrator.vibrate(BatteryNotifierService.VIBRATE_PATTERN, -1);
			}
		}
		if (settings.getBoolean(Settings.SOUND, resources.getBoolean(R.bool.default_sound))) {
			if (player == null) {
				player = new AsyncPlayer(TAG);
			}
			player.play(context, BatteryNotifierService.sound, false, AudioManager.STREAM_NOTIFICATION);
		}
	}

}
