package com.noshufou.android.su;

import com.noshufou.android.su.util.Util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdatePermissionsReceiver extends BroadcastReceiver {
    private static final String TAG = "Su.UpdatePermissionsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Util.updatePermissionsDb(context);
    }

}
