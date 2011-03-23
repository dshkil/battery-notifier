package com.shkil.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.content.res.Resources;
import android.text.Html;

public class Util {

	public static String readTextResource(Resources resources, int id) {
		InputStream inputStream = resources.openRawResource(id);
		try {
			InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
			StringBuilder result = new StringBuilder();
			char[] buffer = new char[4096];
			for (int length = reader.read(buffer); length != -1; length = reader.read(buffer)) {
				result.append(buffer, 0, length);
			}
			return result.toString();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		finally {
			try {
				inputStream.close();
			}
			catch (IOException ioe) {
			}
		}
	}

	public static CharSequence readHtmlResource(Resources resources, int id) {
		return Html.fromHtml(readTextResource(resources, id));
	}

}
