package com.shkil.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import com.shkil.battery.R;

public class TimePickerPreference extends DialogPreference {

	private TimePicker timePicker;
	private int currentHours = -1;
	private int currentMinutes = -1;
	private final String summaryFormat;
	private final String summaryEmpty;

	public TimePickerPreference(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.dialogPreferenceStyle);
	}

	public TimePickerPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setPersistent(true);
		TypedArray styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.SummaryFormat);
		summaryFormat = styledAttrs.getString(R.styleable.SummaryFormat_summaryFormat);
		summaryEmpty = styledAttrs.getString(R.styleable.SummaryFormat_summaryEmpty);
		setSummary(summaryEmpty);
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		String value = a.getString(index);
		if (value != null) {
			String[] chunks = value.split(":");
			try {
				if (chunks.length == 2) {
					int hours = Integer.parseInt(chunks[0]);
					int minutes = Integer.parseInt(chunks[1]);
					return hours * 3600000 + minutes * 60000;
				}
				else {
					return Integer.valueOf(value);
				}
			}
			catch (RuntimeException ex) {
				return -1;
			}
		}
		return null;
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
		if (restorePersistedValue) {
			setTime(getPersistedInt(-1));
		}
		else if (defaultValue != null) {
			setTime((Integer) defaultValue);
		}
	}

	public void setTime(int timeInMillis) {
		if (timeInMillis > 0) {
			currentHours = timeInMillis / 3600000;
			currentMinutes = timeInMillis % 3600000 / 60000;
		}
		else {
			currentHours = 0;
			currentMinutes = 0;
		}
		if (timePicker != null) {
			timePicker.setCurrentHour(currentHours);
			timePicker.setCurrentMinute(currentMinutes);
		}
		updateSummary(timeInMillis);
	}

	private void updateSummary(int timeInMillis) {
		if (timeInMillis >= 0) {
			String timeString = DateUtils.formatDateTime(getContext(), timeInMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_UTC | DateUtils.FORMAT_NO_NOON_MIDNIGHT);
			setSummary(summaryFormat != null ? String.format(summaryFormat, timeString) : timeString);
		}
		else {
			setSummary(summaryEmpty);
		}
	}

	@Override
	protected View onCreateDialogView() {
		Context context = getContext();
		timePicker = new TimePicker(context);
		timePicker.setIs24HourView(DateFormat.is24HourFormat(context));
		if (currentHours >= 0) {
			timePicker.setCurrentHour(currentHours);
		}
		if (currentMinutes >= 0) {
			timePicker.setCurrentMinute(currentMinutes);
		}
		return timePicker;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			final TimePicker timePicker = this.timePicker;
			timePicker.clearFocus();
			currentHours = timePicker.getCurrentHour();
			currentMinutes = timePicker.getCurrentMinute();
			int timeInMillis = currentHours * 3600000 + currentMinutes * 60000;
			if (callChangeListener(timeInMillis)) {
				if (isPersistent()) {
					persistInt(timeInMillis);
				}
				updateSummary(timeInMillis);
				notifyChanged();
			}
		}
	}

}
