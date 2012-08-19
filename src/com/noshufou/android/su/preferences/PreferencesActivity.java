/*******************************************************************************
 * Copyright (c) 2011 Adam Shanks (ChainsDD)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.noshufou.android.su.preferences;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.noshufou.android.su.PinActivity;
import com.noshufou.android.su.R;
import com.noshufou.android.su.TagWriterActivity;
import com.noshufou.android.su.UpdaterActivity;
import com.noshufou.android.su.provider.PermissionsProvider.Logs;
import com.noshufou.android.su.service.ResultService;
import com.noshufou.android.su.service.UpdaterService;
import com.noshufou.android.su.util.BackupUtil;
import com.noshufou.android.su.util.Util;
import com.noshufou.android.su.util.Util.VersionInfo;
import com.noshufou.android.su.widget.AncientNumberPickerDialog;
import com.noshufou.android.su.widget.ChangeLog;

public class PreferencesActivity extends SherlockPreferenceActivity
implements OnSharedPreferenceChangeListener, OnPreferenceChangeListener {
    private static final String TAG = "Su.PreferencesActivity";

    private static final int REQUEST_ENABLE_PIN = 1;
    private static final int REQUEST_DISABLE_PIN = 2;
    private static final int REQUEST_CHANGE_PIN = 3;
    private static final int REQUEST_WRITE_TAG = 4;
    private static final int REQUEST_SECRET_CODE = 5;
    
    SharedPreferences mPrefs = null;

    private Preference mLogLimit = null;
    private Preference mClearLog = null;
    private Preference mToastLocation = null;
    private Preference mTimeoutPreference = null;
    private CheckBoxPreference mPin = null;
    private CheckBoxPreference mGhostMode = null;
    private Preference mSecretCode = null;
    
    private Context mContext;
    private boolean mElite = false;

    @TargetApi(10)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setSupportProgressBarIndeterminateVisibility(false);

        setContentView(R.layout.activity_preferences);

        addPreferencesFromResource(R.xml.preferences);

        mContext = getApplicationContext();

        PreferenceScreen prefScreen = getPreferenceScreen();
        mPrefs = prefScreen.getSharedPreferences();

        mElite = Util.elitePresent(mContext, false, 0);
        if (!mElite) {
            Log.i(TAG, "Elite not found, removing Elite preferences");
            for (String s : Preferences.ELITE_PREFS) {
                Preference pref = findPreference(s);
                if (pref != null) {
                    pref.setEnabled(false);
                    pref.setSummary(R.string.pref_elite_only);
                }

            }
        } else {
            mLogLimit = prefScreen.findPreference(Preferences.LOG_ENTRY_LIMIT);
            mLogLimit.setSummary(getString(R.string.pref_log_entry_limit_summary,
                    mPrefs.getInt(Preferences.LOG_ENTRY_LIMIT, 200)));
            mTimeoutPreference = prefScreen.findPreference(Preferences.TIMEOUT);
            mTimeoutPreference.setSummary(getString(R.string.pref_timeout_summary,
                    mPrefs.getInt(Preferences.TIMEOUT, 0)));
            mPin = (CheckBoxPreference) prefScreen.findPreference(Preferences.PIN);
            mGhostMode = (CheckBoxPreference) prefScreen.findPreference(Preferences.GHOST_MODE);
            mGhostMode.setOnPreferenceChangeListener(this);
            mSecretCode = (Preference) prefScreen.findPreference(Preferences.SECRET_CODE);
            mSecretCode.setSummary(getString(R.string.pref_secret_code_summary,
                    mPrefs.getString(Preferences.SECRET_CODE, "787378737")));
            mSecretCode.setOnPreferenceChangeListener(this);
            mToastLocation = prefScreen.findPreference(Preferences.TOAST_LOCATION);
            mToastLocation.setEnabled(prefScreen.getSharedPreferences()
                    .getString(Preferences.NOTIFICATION_TYPE, "toast").equals("toast"));

            // Remove NFC options if there's no NFC hardware
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
                if (NfcAdapter.getDefaultAdapter(this) == null) {
                    prefScreen.removePreference(findPreference(Preferences.CATEGORY_NFC));
                }
            }
        }
        
        mClearLog = prefScreen.findPreference(Preferences.CLEAR_LOG);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            goHome();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void goHome() {
        Util.goHome(this);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        String pref = preference.getKey();
        if (pref.equals(Preferences.LOG_ENTRY_LIMIT)) {
            new AncientNumberPickerDialog(this,
                    mLogEntriesSet,
                    mPrefs.getInt(Preferences.LOG_ENTRY_LIMIT, 200),
                    0,
                    500,
                    R.string.pref_log_entry_limit_title, 0).show();
        } else if (pref.equals(Preferences.CLEAR_LOG)) {
            new ClearLog().execute();
        } else if (pref.equals(Preferences.PIN)) {
            Intent intent = new Intent(this, PinActivity.class);
            if (preferenceScreen.getSharedPreferences().getBoolean(Preferences.PIN, false)) {
                intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_NEW);
                startActivityForResult(intent, REQUEST_ENABLE_PIN);
            } else {
                intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHECK);
                startActivityForResult(intent, REQUEST_DISABLE_PIN);
            }
            return true;
        } else if (pref.equals(Preferences.CHANGE_PIN)) {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHANGE);
            startActivityForResult(intent, REQUEST_CHANGE_PIN);
        } else if (pref.equals(Preferences.GHOST_MODE)) {
            return true;
        } else if (pref.equals(Preferences.SECRET_CODE)) {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SECRET_CODE);
            startActivityForResult(intent, REQUEST_SECRET_CODE);
        } else if (pref.equals(Preferences.TIMEOUT)) {
            new AncientNumberPickerDialog(this,
                    mTimeoutSet,
                    mPrefs.getInt(Preferences.TIMEOUT, 0),
                    0, 600,
                    R.string.pref_timeout_title,
                    R.string.pref_timeout_unit).show();
        } else if (pref.equals(Preferences.USE_ALLOW_TAG) ||
                pref.equals(Preferences.WRITE_ALLOW_TAG)) {
            if (!preferenceScreen.getSharedPreferences()
                    .getBoolean(Preferences.USE_ALLOW_TAG, false)) {
                return false;
            } else {
                Intent intent = new Intent(this, PinActivity.class);
                intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHECK);
                startActivityForResult(intent, REQUEST_WRITE_TAG);
                return true;
            }
        } else if (pref.equals(Preferences.BACKUP)) {
            new BackupApps().execute();
        } else if (pref.equals(Preferences.RESTORE)) {
            new RestoreApps().execute();
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String pref = preference.getKey();
        if (pref.equals(Preferences.GHOST_MODE)) {
            final boolean ghostMode = (Boolean) newValue;
            if (ghostMode) {
                new AlertDialog.Builder(this).setTitle(R.string.pref_ghost_mode_title)
                .setMessage(R.string.pref_ghost_mode_message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGhostMode.setChecked(true);
                        Util.toggleAppIcon(getApplicationContext(), !ghostMode);
                        new AlertDialog.Builder(PreferencesActivity.this)
                        .setTitle(R.string.pref_ghost_mode_title)
                        .setMessage(R.string.pref_ghost_mode_enabled_message)
                        .setPositiveButton(R.string.ok, null)
                        .create().show();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGhostMode.setChecked(false);
                    }
                }).create().show();
            } else {
                Util.toggleAppIcon(getApplicationContext(), !ghostMode);
                return true;
            }
            return false;
        } else if (pref.equals(Preferences.SECRET_CODE)) {
            mSecretCode.setSummary(getString(R.string.pref_secret_code_summary,
                    ((String)newValue)));
            return true;
        } else if (pref.equals(Preferences.OUTDATED_NOTIFICATION) && !((Boolean) newValue)) {
            Log.d(TAG, "Cancel the notification");
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE))
                    .cancel(UpdaterService.NOTIFICATION_ID);
        } else if (pref.equals(Preferences.TIMEOUT)) {
            int value = Integer.valueOf((String) newValue);
            mPrefs.edit().putInt(Preferences.TIMEOUT, value).commit();
            mTimeoutPreference.setSummary(getString(R.string.pref_timeout_summary, value));
            return false;
        } else if (pref.equals(Preferences.LOG_ENTRY_LIMIT)) {
            int value = Integer.valueOf((String) newValue);
            mPrefs.edit().putInt(Preferences.LOG_ENTRY_LIMIT, Integer.valueOf((String) newValue)).commit();
            mLogLimit.setSummary(getString(R.string.pref_log_entry_limit_summary, value));
            return false;
        }
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        Log.d(TAG, "Preference changed - " + key);
        if (key.equals(Preferences.NOTIFICATION_TYPE) && mToastLocation != null) {
            mToastLocation.setEnabled(sharedPreferences
                    .getString(Preferences.NOTIFICATION_TYPE, "toast").equals("toast"));
        } else if (key.equals(Preferences.AUTOMATIC_ACTION)) {
            Util.writeDefaultStoreFile(this);
        }
    }

    AncientNumberPickerDialog.OnNumberSetListener mLogEntriesSet =
        new AncientNumberPickerDialog.OnNumberSetListener() {

        @Override
        public void onNumberSet(int number) {
            mLogLimit.setSummary(getString(R.string.pref_log_entry_limit_summary, number));
            mPrefs.edit().putInt(Preferences.LOG_ENTRY_LIMIT, number).commit();
            final Intent intent = new Intent(mContext, ResultService.class);
            intent.putExtra(ResultService.EXTRA_ACTION, ResultService.ACTION_RECYCLE);
            startService(intent);
        }
    };

    AncientNumberPickerDialog.OnNumberSetListener mTimeoutSet =
        new AncientNumberPickerDialog.OnNumberSetListener() {

        @Override
        public void onNumberSet(int number) {
            mTimeoutPreference.setSummary(getString(R.string.pref_timeout_summary, number));
            mPrefs.edit().putInt(Preferences.TIMEOUT, number).commit();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED) {
            switch (requestCode) {
            case REQUEST_ENABLE_PIN:
                mPin.setChecked(false);
                break;
            case REQUEST_DISABLE_PIN:
                mPin.setChecked(true);
                break;
            }
            return;
        }
        
        switch (requestCode) {
        case REQUEST_ENABLE_PIN:
        case REQUEST_CHANGE_PIN:
            if (data.hasExtra(PinActivity.EXTRA_PIN)) {
                CharSequence newPin = data.getCharSequenceExtra(PinActivity.EXTRA_PIN);
                mPrefs.edit().putString("pin", newPin.toString()).commit();
                mPin.setChecked(true);
            }
            break;
        case REQUEST_DISABLE_PIN:
            mPin.setChecked(false);
            break;
        case REQUEST_WRITE_TAG:
            Intent intent = new Intent(this, TagWriterActivity.class);
            intent.putExtra(TagWriterActivity.EXTRA_TAG, TagWriterActivity.TAG_ALLOW);
            startActivity(intent);
            break;
        case REQUEST_SECRET_CODE:
            CharSequence newSecretCode = data.getCharSequenceExtra(PinActivity.EXTRA_SECRET_CODE);
            mPrefs.edit().putString(Preferences.SECRET_CODE, newSecretCode.toString()).commit();
            mSecretCode.setSummary(getString(R.string.pref_secret_code_summary, newSecretCode));
            break;
        }
    }
    
    private class ClearLog extends AsyncTask<Void, Void, Integer> {

        @Override
        protected void onPreExecute() {
            mClearLog.setTitle(R.string.pref_clearing_log_title);
            mClearLog.setSummary(R.string.pref_clearing_log_summary);
            mClearLog.setEnabled(false);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            return getContentResolver().delete(Logs.CONTENT_URI, null, null);
        }

        @Override
        protected void onPostExecute(Integer result) {
            mClearLog.setTitle(R.string.pref_clear_log_title);
            mClearLog.setSummary("");
            mClearLog.setEnabled(true);
            Toast.makeText(mContext,
                    getResources().getQuantityString(R.plurals.pref_logs_deleted, result, result),
                    Toast.LENGTH_SHORT).show();
        }
        
    }

    private class BackupApps extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            setSupportProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return BackupUtil.makeBackup(PreferencesActivity.this);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            setSupportProgressBarIndeterminateVisibility(false);
            if (result) {
                Toast.makeText(getApplicationContext(),
                        getString(R.string.backup_complete), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private class RestoreApps extends AsyncTask<Void, Void, Integer> {

        @Override
        protected void onPreExecute() {
            setSupportProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            return BackupUtil.restoreBackup(PreferencesActivity.this);
        }

        @Override
        protected void onPostExecute(Integer result) {
            setSupportProgressBarIndeterminateVisibility(false);
            if (result > -1) {
                String message = result > 0 ?
                        getResources().getQuantityString(R.plurals.restore_complete, result, result):
                        getString(R.string.restore_complete_prefs_only);
                Toast.makeText(getApplicationContext(),
                        message,
                        Toast.LENGTH_SHORT).show();
//                Intent intent = getIntent();
//                finish();
//                startActivity(intent);
//                ((BaseAdapter)getPreferenceScreen().getRootAdapter()).notifyDataSetChanged();
                Log.d(TAG, "call onContentChanged()");
                onContentChanged();
                Log.d(TAG, "onContentChanged() returned");
            }
        }
    }
}
