package com.noshufou.android.su;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.noshufou.android.su.preferences.Preferences;

public class SecretCodeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Uri uri = intent.getData();
        String secretCode = uri.getHost();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(Preferences.GHOST_MODE, false) &&
                prefs.getString(Preferences.SECRET_CODE, "787378737").equals(secretCode)) {
            Intent appList = new Intent(context, HomeActivity.class);
            appList.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(appList);
        }
    }

}
