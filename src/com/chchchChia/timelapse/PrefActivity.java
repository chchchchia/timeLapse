package com.chchchChia.timelapse;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
public class PrefActivity extends PreferenceActivity{

		@Override
		  public void onCreate(Bundle savedInstanceState) {
			 super.onCreate(savedInstanceState);

		        if(savedInstanceState == null)
		            getFragmentManager().beginTransaction()
		                .replace(android.R.id.content, new PrefFrag())
		                .commit();
		        
		  }


}
