package com.noshufou.android.su.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import com.noshufou.android.su.HomeActivity;
import com.noshufou.android.su.R;
import com.noshufou.android.su.SuRequestReceiver;
import com.noshufou.android.su.preferences.Preferences;
import com.noshufou.android.su.provider.PermissionsProvider.Apps;
import com.noshufou.android.su.provider.PermissionsProvider.Logs;
import com.noshufou.android.su.util.Util;

public class ResultService extends IntentService {
    private static final String TAG = "Su.ResultService";
    
    public static final String EXTRA_ACTION = "action";
    public static final int ACTION_RESULT = 1;
    public static final int ACTION_RECYCLE = 2;
    
    final String LAST_NOTIFICATION_UID = "last_notification_uid";
    final String LAST_NOTIFICATION_TIME = "last_notification_time";
    
    public static final String[] PROJECTION = new String[] {
        Apps._ID, Apps.ALLOW, Apps.NOTIFICATIONS, Apps.LOGGING
    };

    private static final int COLUMN_ID = 0;
    private static final int COLUMN_ALLOW = 1;
    private static final int COLUMN_NOTIFICATIONS = 2;
    private static final int COLUMN_LOGGING = 3;

    // TODO: Add in a license check here
//    private boolean mLicenseChecked = false;
//    private boolean mLicensed = true;
    
    private SharedPreferences mPrefs = null;
    private boolean mNotify = true;
    private String mNotifyType = "toast";
    private boolean mLog = true;
    
    private Handler mHandler;
    
    public ResultService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        mHandler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getIntExtra(EXTRA_ACTION, 0)) {
        case ACTION_RESULT:
            ensurePrefs();
            int callerUid = intent.getIntExtra(SuRequestReceiver.EXTRA_CALLERUID, 0);
            int allow = intent.getIntExtra(SuRequestReceiver.EXTRA_ALLOW, -1);
            long currentTime = System.currentTimeMillis();

            long appId = -1;
            String appNotify = null;
            String appLog = null;

            // get what we need from the database
            Cursor c = getContentResolver().query(
                    Uri.withAppendedPath(Apps.CONTENT_URI,
                            "uid/" + callerUid),
                    PROJECTION,
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                appId = c.getLong(COLUMN_ID);
                appNotify = c.getString(COLUMN_NOTIFICATIONS);
                appLog = c.getString(COLUMN_LOGGING);
                int dbAllow = c.getInt(COLUMN_ALLOW);
                if (dbAllow != -1) {
                    allow = dbAllow;
                }
            }
            c.close();

            sendNotification(appId, callerUid, allow, currentTime, appNotify);
            addLog(appId, callerUid, intent.getIntExtra(SuRequestReceiver.EXTRA_UID, 0),
                    intent.getStringExtra(SuRequestReceiver.EXTRA_CMD), allow, currentTime,
                    appLog);
            // No break statement here so that we can fall through and recycle the log
        case ACTION_RECYCLE:
            recycle();
            break;
        default:
            throw new IllegalArgumentException();
        }
    }
    
    private void sendNotification(long appId, int callerUid, int allow, long currentTime, String appNotify) {
        // Check to see if we should notify
        if ((appNotify == null && !mNotify) ||
                (appNotify != null && appNotify.equals("0")) ||
                allow == -1) {
            return;
        }
        final String notificationMessage = getString(
                allow==1?R.string.notification_text_allow:R.string.notification_text_deny,
                Util.getAppName(this, callerUid, false));
        if (mNotifyType.equals("toast")) {
            ensurePrefs();
            int lastNotificationUid = mPrefs.getInt(LAST_NOTIFICATION_UID, 0);
            long lastNotificationTime = mPrefs.getLong(LAST_NOTIFICATION_TIME, 0);
            final int gravity = Integer.parseInt(mPrefs.getString(Preferences.TOAST_LOCATION, "0"));
            if (lastNotificationUid != callerUid ||
                    lastNotificationTime + (5 * 1000) < currentTime) {
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        Toast toast = Toast.makeText(getApplicationContext(),
                                notificationMessage, Toast.LENGTH_SHORT);
                        if (gravity > 0) {
                            toast.setGravity(gravity, 0, 0);
                        }
                        toast.show();
                    }

                });
                Editor editor = mPrefs.edit();
                editor.putInt(LAST_NOTIFICATION_UID, callerUid);
                editor.putLong(LAST_NOTIFICATION_TIME, currentTime);
                editor.commit();
            }
        } else if (mNotifyType.equals("status")) {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Intent notificationIntent = new Intent(this, HomeActivity.class);
            // TODO: Include extras to tell HomeActivity what to do
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);
            Notification notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.stat_su)
                    .setTicker(notificationMessage)
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle(getText(R.string.app_name))
                    .setContentText(notificationMessage)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .getNotification();
            nm.notify(callerUid, notification);
        }
    }
    
    private void addLog(long appId, int callerUid, int execUid, String execCmd, int allow,
            long currentTime, String appLog) {
        // Check to see if we should log
        if ((appLog == null && !mLog) ||
                (appLog != null && appLog.equals("0")) ||
                allow == -1) {
            return;
        }
        
        ContentValues values = new ContentValues();
        if (appId == -1) {
            // App was not found in the database, add a row for logging purposes
            values.put(Apps.UID, callerUid);
            values.put(Apps.PACKAGE, Util.getAppPackage(this, callerUid));
            values.put(Apps.NAME, Util.getAppName(this, callerUid, false));
            values.put(Apps.EXEC_UID, execUid);
            values.put(Apps.EXEC_CMD, execCmd);
            values.put(Apps.ALLOW, Apps.AllowType.ASK);
            appId = Long.parseLong(getContentResolver().insert(Apps.CONTENT_URI, values)
                    .getLastPathSegment());
        }
        
        values.clear();
        values.put(Logs.DATE, currentTime);
        values.put(Logs.TYPE, allow);
        getContentResolver()
            .insert(Uri.withAppendedPath(Logs.CONTENT_URI, String.valueOf(appId)), values);
    }

    private void recycle() {
        ensurePrefs();
        if (!mPrefs.getBoolean(Preferences.DELETE_OLD_LOGS, true)) {
            // Log recycling is disabled, no need to go further
            return;
        }
        
        int limit = mPrefs.getInt(Preferences.LOG_ENTRY_LIMIT, 200);
        Cursor c = getContentResolver().query(Logs.CONTENT_URI, new String[] { "COUNT() as rows" },
                null, null, null);
        c.moveToFirst();
        int count = c.getInt(0);
        c.close();
        if (count > limit) {
            c = getContentResolver().query(Logs.CONTENT_URI, new String[] { Logs._ID },
                    null, null, Logs.DATE + " ASC");
            long id = 0;
            while (count > limit && c.moveToNext()) {
                id = c.getLong(0);
                count -= getContentResolver().delete(Logs.CONTENT_URI, Logs._ID + "=?",
                        new String[] { String.valueOf(id) });
            }
        }
    }
    
    private void ensurePrefs() {
        if (mPrefs == null) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            // read some global settings that we need every time
            mNotify = mPrefs.getBoolean(Preferences.NOTIFICATIONS, true);
            mNotifyType = mPrefs.getString(Preferences.NOTIFICATION_TYPE, "toast");
            mLog = mPrefs.getBoolean(Preferences.LOGGING, true);
        }
    }
}
