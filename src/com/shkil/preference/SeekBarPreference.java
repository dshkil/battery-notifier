package com.shkil.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.shkil.battery.R;

public class SeekBarPreference extends DialogPreference implements OnSeekBarChangeListener {

	private int currentValue = -1;
	private final int minValue;
	private final int maxValue;
	private final String valueFormat;
	private final String summaryFormat;
	private final String summaryZero;
	private final String dialogMessage;

	private SeekBar seekBar;
	private TextView valueText;

	private static final int DEFAULT_MIN_VALUE = 0;
	private static final int DEFAULT_MAX_VALUE = 100;

	public SeekBarPreference(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.dialogPreferenceStyle);
	}

	public SeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setPersistent(true);
		TypedArray seekBarAttrs = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference);
		TypedArray summaryAttrs = context.obtainStyledAttributes(attrs, R.styleable.SummaryFormat);
		valueFormat = seekBarAttrs.getString(R.styleable.SeekBarPreference_valueFormat);
		summaryFormat = summaryAttrs.getString(R.styleable.SummaryFormat_summaryFormat);
		String summaryZero = summaryAttrs.getString(R.styleable.SummaryFormat_summaryEmpty);
		this.summaryZero = summaryZero != null ? summaryZero : "0";
		minValue = seekBarAttrs.getInt(R.styleable.SeekBarPreference_minValue, DEFAULT_MIN_VALUE);
		maxValue = seekBarAttrs.getInt(R.styleable.SeekBarPreference_maxValue, DEFAULT_MAX_VALUE);
		dialogMessage = seekBarAttrs.getString(R.styleable.SeekBarPreference_dialogMessage);
		setValue(minValue);
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInt(index, 0);
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
		int value; 
		if (restorePersistedValue) {
			value = getPersistedInt(minValue);
		}
		else if (defaultValue != null) {
			value = (Integer) defaultValue;
		}
		else {
			value = minValue;
		}
		setValue(value);
	}

	public void setValue(int value) {
		if (value < minValue) {
			value = minValue;
		}
		else if (value > maxValue) {
			value = maxValue;
		}
		if (currentValue != value) {
			currentValue = value;
			if (seekBar != null) {
				seekBar.setProgress(value - minValue);
			}
			if (valueText != null) {
				updateValue(value);
			}
			updateSummary(value);
		}
	}

	private void updateSummary(int value) {
		setSummary(value == 0 ? summaryZero : (summaryFormat != null ? String.format(summaryFormat, value) : String.valueOf(value)));
	}

	private void updateValue(int value) {
		valueText.setText(valueFormat != null ? String.format(valueFormat, value) : String.valueOf(value));
	}

	@Override
	protected View onCreateDialogView() {
		LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View dialogView = layoutInflater.inflate(R.layout.seek_bar_preference, null);
		SeekBar seekBar = (SeekBar) dialogView.findViewById(R.id.seek_bar);
		valueText = (TextView) dialogView.findViewById(R.id.seekbar_value_text);
		this.seekBar = seekBar;
		seekBar.setMax(maxValue - minValue);
		seekBar.setProgress(currentValue - minValue);
		TextView dialogMessageView = (TextView) dialogView.findViewById(R.id.dialog_message);
		if (dialogMessage != null) {
			dialogMessageView.setText(dialogMessage);
		}
		else {
			dialogMessageView.setVisibility(View.GONE);
		}
		updateValue(currentValue);
		seekBar.setOnSeekBarChangeListener(this);
		return dialogView;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			int value = seekBar.getProgress() + minValue;
			this.currentValue = value;
			if (callChangeListener(value)) {
				if (isPersistent()) {
					persistInt(value);
				}
				updateSummary(value);
				notifyChanged();
			}
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		updateValue(progress + minValue);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

}
