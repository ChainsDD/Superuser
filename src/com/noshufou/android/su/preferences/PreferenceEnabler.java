package com.noshufou.android.su.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.CompoundButton;
import android.widget.Switch;

public class PreferenceEnabler implements CompoundButton.OnCheckedChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PreferencesEnabler";
    private final Context mContext;
    private Switch mSwitch;
    private SharedPreferences mPrefs;
    private final String mKey;
    private final boolean mDefValue;
    private boolean mStateMachineEvent = false;

    public PreferenceEnabler(Context context, Switch switch_, String key, boolean defValue) {
        mContext = context;
        mSwitch = switch_;
        mKey = key;
        mDefValue = defValue;
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void setSwitch(Switch switch_) {
        if (mSwitch == switch_) return;
        mSwitch.setOnCheckedChangeListener(null);
        mSwitch = switch_;
        mSwitch.setOnCheckedChangeListener(this);
        
        mSwitch.setChecked(mPrefs.getBoolean(mKey, mDefValue));
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mStateMachineEvent) return;
        mPrefs.edit().putBoolean(mKey, isChecked).commit();
    }

    public void pause() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mSwitch.setOnCheckedChangeListener(null);
    }

    public void resume() {
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        setSwitchChecked(mPrefs.getBoolean(mKey, mDefValue));
        mSwitch.setOnCheckedChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(mKey)) {
            setSwitchChecked(sharedPreferences.getBoolean(mKey, mDefValue));
        }
    }

    private void setSwitchChecked(boolean checked) {
        if (checked != mSwitch.isChecked()) {
            mStateMachineEvent = true;
            mSwitch.setChecked(checked);
            mStateMachineEvent = false;
        }
    }
}
