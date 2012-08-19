package com.noshufou.android.su.preferences;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.Gravity;
import android.widget.Switch;
import android.widget.Toast;

import com.noshufou.android.su.PinActivity;
import com.noshufou.android.su.R;
import com.noshufou.android.su.TagWriterActivity;
import com.noshufou.android.su.provider.PermissionsProvider.Logs;
import com.noshufou.android.su.service.ResultService;
import com.noshufou.android.su.util.BackupUtil;
import com.noshufou.android.su.util.Util;
import com.noshufou.android.su.widget.NumberPickerDialog;
import com.noshufou.android.su.widget.NumberPickerDialog.OnNumberSetListener;

@TargetApi(11)
public class PreferencesFragment extends PreferenceFragment
        implements OnSharedPreferenceChangeListener, OnNumberSetListener {
    private static final String TAG = "Su.PreferenceFragment";

    private CheckBoxPreference mPin = null;
    private CheckBoxPreference mGhostMode = null;
    private Preference mSecretCode = null;
    private PreferenceEnabler mEnabler = null;

    private SharedPreferences mPrefs;
    private boolean mElite = false;
    private int mScreen;

    @TargetApi(14)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mScreen = getActivity().getResources().
                getIdentifier(getArguments().getString("resource"),
                        "xml",
                        getActivity().getPackageName());
        addPreferencesFromResource(mScreen);
        
        mPrefs = getPreferenceScreen().getSharedPreferences();

        mElite = Util.elitePresent(getActivity(), false, 0);
        if (!mElite) {
            for (String s : Preferences.ELITE_PREFS) {
                
                Preference pref = findPreference(s);
                if (pref != null) {
                    pref.setEnabled(false);
                    pref.setSummary(R.string.pref_elite_only);
                }
            }
        } else {
            Preference pref = findPreference(Preferences.GET_ELITE);
            if (pref != null)
                getPreferenceScreen().removePreference(pref);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
                getActivity() instanceof PreferenceActivity) {
            PreferenceActivity activity = (PreferenceActivity) getActivity();
            boolean addSwitch = false;
            String key = null;
            if (mScreen == R.xml.prefs_log) {
                getPreferenceScreen().removePreference(findPreference(Preferences.LOGGING));
                addSwitch = true;
                key = Preferences.LOGGING;
            } else if (mScreen == R.xml.prefs_notifications) {
                getPreferenceScreen().removePreference(findPreference(Preferences.NOTIFICATIONS));
                addSwitch = true;
                key = Preferences.NOTIFICATIONS;
            }
            if (addSwitch && (activity.onIsHidingHeaders() || !activity.onIsMultiPane())) {
                Switch actionBarSwitch = new Switch(activity);
                final int padding = activity.getResources().getDimensionPixelSize(
                        R.dimen.action_bar_switch_padding);
                actionBarSwitch.setPadding(0, 0, padding, 0);
                activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                        ActionBar.DISPLAY_SHOW_CUSTOM);
                activity.getActionBar().setCustomView(actionBarSwitch,
                        new ActionBar.LayoutParams(
                                ActionBar.LayoutParams.WRAP_CONTENT,
                                ActionBar.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER_VERTICAL | Gravity.RIGHT));
                mEnabler = new PreferenceEnabler(activity, actionBarSwitch, key, true);
            }
        }

        mPin = (CheckBoxPreference) findPreference(Preferences.PIN);
        mGhostMode = (CheckBoxPreference) findPreference(Preferences.GHOST_MODE);
        mSecretCode = (Preference) findPreference(Preferences.SECRET_CODE);
        if (mSecretCode != null)
            mSecretCode.setSummary(getString(R.string.pref_secret_code_summary,
                    mPrefs.getString(Preferences.SECRET_CODE, "787378737")));
//        mToastLocation = findPreference(Preferences.TOAST_LOCATION);

        updateTimeout(mPrefs.getInt(Preferences.TIMEOUT, 0));
        updateLogLimit(mPrefs.getInt(Preferences.LOG_ENTRY_LIMIT, 200));
        setDepsLog(mPrefs.getBoolean(Preferences.LOGGING, true));
        setDepsNotifications(mPrefs.getBoolean(Preferences.NOTIFICATIONS, true));
        setDepsNfc(mPrefs.getBoolean(Preferences.PIN, false));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mEnabler != null)
            mEnabler.resume();
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        if (mEnabler != null)
            mEnabler.pause();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();

        // Security
        if (key.equals(Preferences.PIN)) {
            Intent intent = new Intent(getActivity(), PinActivity.class);
            if (preferenceScreen.getSharedPreferences().getBoolean(Preferences.PIN, false)) {
                intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_NEW);
                startActivityForResult(intent, Preferences.REQUEST_ENABLE_PIN);
            } else {
                intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHECK);
                startActivityForResult(intent, Preferences.REQUEST_DISABLE_PIN);
            }
            return true;
        } else if (key.equals(Preferences.CHANGE_PIN)) {
            Intent intent = new Intent(getActivity(), PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHANGE);
            startActivityForResult(intent, Preferences.REQUEST_CHANGE_PIN);
        } else if (key.equals(Preferences.TIMEOUT)) {
            new NumberPickerDialog(getActivity(),
                    this,
                    mPrefs.getInt(Preferences.TIMEOUT, 0),
                    0, 600,
                    R.string.pref_timeout_title, R.string.pref_timeout_unit,
                    Preferences.DIALOG_TIMEOUT).show();
        } else if (key.equals(Preferences.GHOST_MODE)) {
            final boolean ghostMode = mPrefs.getBoolean(Preferences.GHOST_MODE, false);
            if (ghostMode) {
                new AlertDialog.Builder(getActivity()).setTitle(R.string.pref_ghost_mode_title)
                .setMessage(R.string.pref_ghost_mode_message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGhostMode.setChecked(true);
                        Util.toggleAppIcon(getActivity(), !ghostMode);
                        new AlertDialog.Builder(getActivity())
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
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mGhostMode.setChecked(false);
                    }
                }).create().show();
            } else {
                Util.toggleAppIcon(getActivity(), !ghostMode);
            }
        } else if (key.equals(Preferences.SECRET_CODE)) {
            Intent intent = new Intent(getActivity(), PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SECRET_CODE);
            startActivityForResult(intent, Preferences.REQUEST_SECRET_CODE);

        // Log
        } else if (key.equals(Preferences.LOG_ENTRY_LIMIT)) {
            new NumberPickerDialog(getActivity(),
                    this,
                    mPrefs.getInt(Preferences.LOG_ENTRY_LIMIT, 200),
                    0, 500,
                    R.string.pref_log_entry_limit_title, 0, Preferences.DIALOG_LOG_LIMIT).show();
        } else if (key.equals(Preferences.CLEAR_LOG)) {
            new ClearLog().execute();

        // NFC
        } else if (key.equals(Preferences.USE_ALLOW_TAG) ||
                key.equals(Preferences.WRITE_ALLOW_TAG)) {
            if (!preferenceScreen.getSharedPreferences()
                    .getBoolean(Preferences.USE_ALLOW_TAG, false)) {
                return false;
            } else {
                Intent intent = new Intent(getActivity(), PinActivity.class);
                intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHECK);
                startActivityForResult(intent, Preferences.REQUEST_WRITE_TAG);
                return true;
            }

            // Backup/restore
        } else if (key.equals(Preferences.BACKUP)) {
            new BackupApps().execute();
        } else if (key.equals(Preferences.RESTORE)) {
            new RestoreApps().execute();
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Preferences.LOGGING) ||
                key.equals(Preferences.DELETE_OLD_LOGS)){
            setDepsLog(sharedPreferences.getBoolean(Preferences.LOGGING, true));
        } else if (key.equals(Preferences.NOTIFICATIONS) ||
                key.equals(Preferences.NOTIFICATION_TYPE)) {
            setDepsNotifications(sharedPreferences.getBoolean(Preferences.NOTIFICATIONS, true));
        } else if (key.equals(Preferences.SECRET_CODE)) {
            updateSecretCode(sharedPreferences.getString(key, "787378737"));
        } else if (key.equals(Preferences.PIN)) {
            setDepsNfc(sharedPreferences.getBoolean(Preferences.PIN, false));
        } else if (key.equals(Preferences.AUTOMATIC_ACTION)) {
            Util.writeDefaultStoreFile(getActivity());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            switch (requestCode) {
            case Preferences.REQUEST_ENABLE_PIN:
                mPin.setChecked(false);
                break;
            case Preferences.REQUEST_DISABLE_PIN:
                mPin.setChecked(true);
                break;
            }
            return;
        }
        
        switch (requestCode) {
        case Preferences.REQUEST_ENABLE_PIN:
        case Preferences.REQUEST_CHANGE_PIN:
            if (data.hasExtra(PinActivity.EXTRA_PIN)) {
                CharSequence newPin = data.getCharSequenceExtra(PinActivity.EXTRA_PIN);
                mPrefs.edit().putString("pin", newPin.toString()).commit();
                mPin.setChecked(true);
            }
            break;
        case Preferences.REQUEST_DISABLE_PIN:
            mPin.setChecked(false);
            break;
        case Preferences.REQUEST_WRITE_TAG:
            Intent intent = new Intent(getActivity(), TagWriterActivity.class);
            intent.putExtra(TagWriterActivity.EXTRA_TAG, TagWriterActivity.TAG_ALLOW);
            startActivity(intent);
            break;
        case Preferences.REQUEST_SECRET_CODE:
            CharSequence newSecretCode = data.getCharSequenceExtra(PinActivity.EXTRA_SECRET_CODE);
            mPrefs.edit().putString(Preferences.SECRET_CODE, newSecretCode.toString()).commit();
            updateSecretCode(newSecretCode);
            break;
        }
    }

    @Override
    public void onNumberSet(int dialogId, int number) {
        if (dialogId == Preferences.DIALOG_TIMEOUT) {
            updateTimeout(number);
            mPrefs.edit().putInt(Preferences.TIMEOUT, number).commit();
        } else if (dialogId == Preferences.DIALOG_LOG_LIMIT) {
            updateLogLimit(number);
            mPrefs.edit().putInt(Preferences.LOG_ENTRY_LIMIT, number).commit();
            final Intent intent = new Intent(getActivity(), ResultService.class);
            intent.putExtra(ResultService.EXTRA_ACTION, ResultService.ACTION_RECYCLE);
            getActivity().startService(intent);
        }
    }

    private void setDepsLog(boolean enabled) {
        if (mScreen == R.xml.prefs_app_list) {
            findPreference(Preferences.APPLIST_SHOW_LOG_DATA).setEnabled(enabled);
        }
        else if (mScreen == R.xml.prefs_log) {
            getPreferenceScreen().setEnabled(enabled);
            findPreference(Preferences.LOG_ENTRY_LIMIT).setEnabled(
                    mPrefs.getBoolean(Preferences.DELETE_OLD_LOGS, true) && enabled);
        }
    }
    
    private void setDepsNotifications(boolean enabled) {
        if (mScreen == R.xml.prefs_notifications) {
            getPreferenceScreen().setEnabled(enabled);
            findPreference(Preferences.TOAST_LOCATION).setEnabled(
                    mPrefs.getString(Preferences.NOTIFICATION_TYPE, "toast").equals("toast") && enabled);
        }
    }
    
    private void setDepsNfc(boolean pinEnabled) {
        if (mScreen == R.xml.prefs_nfc) {
            getPreferenceScreen().setEnabled(pinEnabled);
            findPreference(Preferences.WRITE_ALLOW_TAG).setEnabled(
                    mPrefs.getBoolean(Preferences.USE_ALLOW_TAG, false) && pinEnabled);
        }
    }
    
    private void updateSecretCode(CharSequence code) {
        if (mScreen == R.xml.prefs_security)
            findPreference(Preferences.SECRET_CODE)
                    .setSummary(getString(R.string.pref_secret_code_summary, code));
    }
    
    private void updateTimeout(int timeout) {
        if (mScreen == R.xml.prefs_security)
            findPreference(Preferences.TIMEOUT)
                    .setSummary(getString(R.string.pref_timeout_summary, timeout));
    }
    private void updateLogLimit(int limit) {
        if (mScreen == R.xml.prefs_log)
            findPreference(Preferences.LOG_ENTRY_LIMIT)
                    .setSummary(getString(R.string.pref_log_entry_limit_summary, limit));
    }

    private class ClearLog extends AsyncTask<Void, Void, Integer> {

        @Override
        protected void onPreExecute() {
            Preference clearLog = findPreference(Preferences.CLEAR_LOG);
            clearLog.setTitle(R.string.pref_clearing_log_title);
            clearLog.setSummary(R.string.pref_clearing_log_summary);
            clearLog.setEnabled(false);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            return getActivity().getContentResolver().delete(Logs.CONTENT_URI, null, null);
        }

        @Override
        protected void onPostExecute(Integer result) {
            Preference clearLog = findPreference(Preferences.CLEAR_LOG);
            clearLog.setTitle(R.string.pref_clear_log_title);
            clearLog.setSummary("");
            clearLog.setEnabled(true);
            Toast.makeText(getActivity(),
                    getResources().getQuantityString(R.plurals.pref_logs_deleted, result, result),
                    Toast.LENGTH_SHORT).show();
        }

    }

    private class BackupApps extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            getActivity().setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return BackupUtil.makeBackup(getActivity());
        }

        @Override
        protected void onPostExecute(Boolean result) {
            getActivity().setProgressBarIndeterminateVisibility(false);
            if (result) {
                Toast.makeText(getActivity(),
                        getString(R.string.backup_complete), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private class RestoreApps extends AsyncTask<Void, Void, Integer> {

        @Override
        protected void onPreExecute() {
            getActivity().setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            return BackupUtil.restoreBackup(getActivity());
        }

        @Override
        protected void onPostExecute(Integer result) {
            getActivity().setProgressBarIndeterminateVisibility(false);
            if (result > -1) {
                String message = result > 0 ?
                        getResources().getQuantityString(R.plurals.restore_complete, result, result):
                        getString(R.string.restore_complete_prefs_only);
                Toast.makeText(getActivity(),
                        message,
                        Toast.LENGTH_SHORT).show();
            }
        }

    }
}
