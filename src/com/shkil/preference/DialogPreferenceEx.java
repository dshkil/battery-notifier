package com.shkil.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class DialogPreferenceEx extends DialogPreference {

	public DialogPreferenceEx(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.dialogPreferenceStyle);
	}

	public DialogPreferenceEx(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}


}
