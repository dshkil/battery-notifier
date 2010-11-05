package com.shkil.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AsyncPlayer;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

public class AlarmReciever extends BroadcastReceiver {

	private static final String TAG = AlarmReciever.class.getSimpleName();
	private static AsyncPlayer player;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive");
		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		if (Settings.shouldVibrate(settings, audioManager)) {
			Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
			vibrator.vibrate(BatteryNotifierService.VIBRATE_PATTERN, -1);
		}
		int shouldSound = Settings.shouldSound(settings, audioManager);
		if (shouldSound != Settings.SHOULD_SOUND_FALSE) {
			if (player == null) {
				player = new AsyncPlayer(TAG);
			}
			Uri ringtone = Settings.getAlertRingtone(settings);
			int stream = shouldSound == Settings.SHOULD_SOUND_TRUE ? AudioManager.STREAM_ALARM : AudioManager.STREAM_NOTIFICATION;
			player.play(context, ringtone, false, stream);
		}
	}

}
