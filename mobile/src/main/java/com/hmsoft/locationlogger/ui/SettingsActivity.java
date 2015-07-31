package com.hmsoft.locationlogger.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.TimePicker;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.service.LocationService;
import com.hmsoft.locationlogger.service.SyncAuthenticatorService;
import com.hmsoft.locationlogger.service.SyncService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import static com.hmsoft.locationlogger.R.string;
import static com.hmsoft.locationlogger.R.xml;

public class SettingsActivity extends PreferenceActivity 
	implements OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {

	private static final String TAG = "SettingsActivity";
	
	private PreferenceCategory mPrefCategoryService;
    private PreferenceCategory mPrefCategorySync;

    private boolean mServicePrefChanged;
    private boolean mSyncPrefChanged;
    private AdvancedSettings mAdvancedSettings;

    private static class AdvancedSettings extends Properties {
        private SettingsActivity mActivity;

        private void putAllPreferences(PreferenceGroup group) {
            int c = 0;
            String advancedPrefix = mActivity.getString(string.pref_advanced_prefix);
            while(c < group.getPreferenceCount()) {
                String key;
                Preference preference = group.getPreference(c);
                if(preference instanceof PreferenceGroup) {
                    putAllPreferences((PreferenceGroup)preference);
                    key = preference.getKey();
                } else {
                    key = String.format("%s.%s", group.getKey(), preference.getKey());
                }
                setProperty(key, String.valueOf(!preference.getKey().startsWith(advancedPrefix)));
                c++;
            }
        }

        private boolean showAll() {
            return true;//"true".equals(getProperty("show_all", "false"));
        }

        private boolean isVisible(PreferenceGroup parent, Preference preference) {
            String key;
            if(preference instanceof PreferenceGroup) {
                key = preference.getKey();
            } else {
                key = String.format("%s.%s", parent.getKey(), preference.getKey());
            }
            return "true".equals(getProperty(key, "true"));
        }

        public AdvancedSettings(SettingsActivity activity) {
            mActivity = activity;
            File file = new File(activity.getExternalFilesDir("config"), "pref-settings.txt");
            if(!file.exists()) {
                setProperty("show_all", "false");
                //noinspection deprecation
                putAllPreferences(activity.getPreferenceScreen());
                try {
                    FileOutputStream fo = new FileOutputStream(file);
                    this.store(fo, "");
                    fo.close();
                } catch (IOException e) {
                    Logger.warning(TAG, "Error storing advanced.txt", e);
                }
            }
            try {
                FileInputStream fi = new FileInputStream(file);
                load(fi);
                fi.close();
            } catch (IOException e) {
               Logger.warning(TAG, "Error loading advanced.txt", e);
            }
        }
    }

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(Logger.DEBUG) Logger.debug(TAG, "onCreate");

        setPrefDefaults();
		addPreferencesFromResource(xml.settings);
        addCustomPreferecences();
        mAdvancedSettings = new AdvancedSettings(this);

     	mPrefCategoryService = (PreferenceCategory)findPreference(getString(string.pref_service_settings_key));
        mPrefCategorySync = (PreferenceCategory)findPreference(getString(string.pref_locatrack_settings_key));

        if(GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            Preference gmspref = mPrefCategoryService.findPreference(getString(R.string.pref_use_gms_if_available_key));
            if(gmspref != null) {
                mPrefCategoryService.removePreference(gmspref);
            }
        }

        if(getString(R.string.action_sync_settings).equals(getIntent().getAction())) {
            getPreferenceScreen().removePreference(mPrefCategoryService);
        } else {
            //hideAdvancedPreferences(getPreferenceScreen());
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                mPrefCategoryService.removePreference(mPrefCategoryService.findPreference(getString(string.pref_set_airplanemode_key)));
            }
        }
        bindPreferencesSummaryToValue(getPreferenceScreen());
	}

    /*private void hideAdvancedPreferences(PreferenceGroup group) {
        if(mAdvancedSettings.showAll()) {
            return;
        }

        int c = 0;
        while(c < group.getPreferenceCount()) {
            Preference pref = group.getPreference(c);
            if(!mAdvancedSettings.isVisible(group, pref)) {
                group.removePreference(pref);
            } else {
                if(pref instanceof PreferenceGroup) {
                    hideAdvancedPreferences((PreferenceGroup)pref);
                }
                c++;
            }
        }
    }*/

    private void addCustomPreferecences() {
        //noinspection deprecation
        final DialogPreference prefSyncTime = (DialogPreference)findPreference(getString(string.pref_synctime_key));
        final Context context = this;

        prefSyncTime.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                TimePicker timePicker = (TimePicker) prefSyncTime.getDialog().findViewById(R.id.timePicker);
                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String[] syncTime = preferences.getString(getString(R.string.pref_synctime_key),
                        getString(string.pref_synctime_default)).split(":");
                int syncHour = Integer.parseInt(syncTime[0]);
                int syncMinute = Integer.parseInt(syncTime[1]);
                timePicker.setCurrentHour(syncHour);
                timePicker.setCurrentMinute(syncMinute);
                timePicker.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
                    @Override
                    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                        Preference.OnPreferenceChangeListener listener = prefSyncTime.getOnPreferenceChangeListener();
                        String value = String.format("%d:%d", hourOfDay, minute);
                        preferences.edit().putString(context.getString(R.string.pref_synctime_key), value).apply();
                        if (listener != null) {
                            listener.onPreferenceChange(prefSyncTime, value);
                        }
                    }
                });
                return true;
            }
        });
    }

    private void setPrefDefaults() {
        setPrefDefaults(this);
    }

     public  static void setPrefDefaults(Context context) {
        PreferenceManager.setDefaultValues(context, xml.settings, false);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = null;
        String deviceIdKey = context.getString(R.string.pref_locatrack_deviceid_key);
        String devId = preferences.getString(deviceIdKey, "");
        if(TextUtils.isEmpty(devId)) {
            editor = preferences.edit();
            editor.putString(deviceIdKey, SyncAuthenticatorService.getGoogleAccount(context));
        }
    }

	public static void start(Context context) {
		start(context, null);
	}


    public static void start(Context context, String action) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.setAction(action);
        context.startActivity(intent);
    }
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {	
		
		if(mPrefCategoryService.findPreference(key) != null) {
			mServicePrefChanged = true;
			if(key.equals(getString(string.pref_service_enabled_key))) {
                boolean serviceChangedToEnabled = preferences.getBoolean(key, true);
				if(serviceChangedToEnabled) {
					LocationService.enable(this);
				} else {
					LocationService.configure(this);
					mServicePrefChanged = false;
				}
			}
			if(Logger.DEBUG) Logger.debug(TAG, "Preference %s chaged. (Service)", key);
		} 
		else if(mPrefCategorySync.findPreference(key) != null) {
            mSyncPrefChanged = true;
            if(Logger.DEBUG) Logger.debug(TAG, "Preference %s chaged. (Sync)", key);
        } else {
			if(Logger.DEBUG) Logger.debug(TAG, "Preference %s chaged. (Unknow)", key);
		}
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
	    super.onResume();
	    if(Logger.DEBUG) Logger.debug(TAG, "onResume");
		
		mServicePrefChanged = false;

		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPause() {	    
	    if(Logger.DEBUG) Logger.debug(TAG, "onPause");
		
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		
		if(mServicePrefChanged || mSyncPrefChanged) {
			LocationService.configure(this);
		}

		super.onPause();
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object value) {	
		String stringValue = value.toString();
		if (preference instanceof ListPreference) {
			// For list preferences, look up the correct display value in
			// the preference's 'entries' list.
			ListPreference listPreference = (ListPreference) preference;
			int index = listPreference.findIndexOfValue(stringValue);

			// Set the summary to reflect the new value.
			stringValue = (index >= 0 ? listPreference.getEntries()[index] : "").toString();
		} else if(preference instanceof CheckBoxPreference) {
			boolean checked = Boolean.parseBoolean(stringValue);
            stringValue = (checked ? getString(string.enabled_string) : getString(string.disabled_string));
		} else if(getString(string.pref_synctime_key).equals(preference.getKey())) {
            String[] time = stringValue.split(":");
            int syncHour = Integer.parseInt(time[0]);
            int syncMinute = Integer.parseInt(time[1]);
            Date alarmeDate = new Date(SyncService.getMillisOfTomorrowTime(syncHour, syncMinute));
            SimpleDateFormat alarmTimeFormatter = new SimpleDateFormat("MMM d h:ma ");

            Date deltaDate = new Date(alarmeDate.getTime() - System.currentTimeMillis());
            SimpleDateFormat deltaFormatter = new SimpleDateFormat("HH:mm");

            stringValue = String.format("%s (%s)", alarmTimeFormatter.format(alarmeDate),
                    deltaFormatter.format(deltaDate));
        }
		
		preference.setSummary(stringValue);
		return true;
	}
	
	private void bindPreferencesSummaryToValue(PreferenceGroup group) {
		int prefCount = group.getPreferenceCount();
		for(int c = 0; c < prefCount; c++) {
			Preference preference = group.getPreference(c);
            if(preference instanceof PreferenceGroup) {
                bindPreferencesSummaryToValue((PreferenceGroup)preference);
            } else {
                bindPreferenceSummaryToValue(preference);
            }
		}
	}
	
	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 */
	private void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(this);
		
		Object value = null;		
		if(preference instanceof CheckBoxPreference) {
			value = ((CheckBoxPreference)preference).isChecked();
		} else if(preference instanceof ListPreference) {
			value = ((ListPreference)preference).getValue();
		} else if(preference instanceof EditTextPreference) {
			value = ((EditTextPreference)preference).getText();
		}
		// Trigger the listener immediately with the preference's
		// current value.
		if(value != null) {
			this.onPreferenceChange(preference,	value);
		}
	}
}
