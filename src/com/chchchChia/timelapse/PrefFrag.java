package com.chchchChia.timelapse;

import com.chchchChia.timelapse.R;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;



@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class PrefFrag extends PreferenceFragment implements OnSharedPreferenceChangeListener{
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
       getPreferenceScreen().getSharedPreferences()
        .registerOnSharedPreferenceChangeListener(this);
    }
	
	
	@Override
	public void onResume() {
	    super.onResume();
	    getPreferenceScreen().getSharedPreferences()
	            .registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onPause() {
	    super.onPause();
	    getPreferenceScreen().getSharedPreferences()
	            .unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("extStorage")) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
           // connectionPref.setSummary(sharedPreferences.getString(key, ""));
            TimeLapseService.sdCard=sharedPreferences.getBoolean("extStorage", false);
		}
	}
}