package com.noshufou.android.su.service;

import com.noshufou.android.su.provider.PermissionsProvider.Apps;
import com.noshufou.android.su.provider.PermissionsProvider.Logs;
import com.noshufou.android.su.util.Util;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

public class PermissionsDbService extends IntentService {
    private static final String TAG = "Su.PermissionsDbService";
    
    private static final String ATTEMPT_PREF = "clean_permissions_attempt";
    
    private static final String[] PROJECTION = new String[] {
        Apps._ID, Apps.UID, Apps.PACKAGE, Apps.NAME, Apps.EXEC_UID, Apps.EXEC_CMD,
        Apps.ALLOW, Apps.DIRTY
    };
    
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_UID = 1;
    private static final int COLUMN_PACKAGE = 2;
    private static final int COLUMN_NAME = 3;
    private static final int COLUMN_EXEC_UID = 4;
    private static final int COLUMN_EXEC_CMD = 5;
    private static final int COLUMN_ALLOW = 6;

    public PermissionsDbService() {
        super(TAG);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // This service doesn't allow binding.
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent()");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int attempt = prefs.getInt(ATTEMPT_PREF, 0);
        boolean allDirty = false;
        if (attempt > 10) {
            Log.d(TAG, "tried to many times, wipe permissions.sqlite and start again");
            deleteDatabase("permissions.sqlite");
            allDirty = true;
        } else if (attempt > 12) {
            // we're obviously not getting anywhere with this, try agin next time
            return;
        }
        SQLiteDatabase pDb = null;
        ContentResolver cr = getContentResolver();
        try {
            pDb = new PermissionsDbOpenHelper(this).getWritableDatabase();
            Log.d(TAG, "permissions.sqlite opened");
            String where = null;
            String whereArgs[] = null;
            if (!allDirty) {
                where = Apps.DIRTY + "=?";
                whereArgs = new String[] { "1" };
            }
            Cursor c = cr.query(Apps.CONTENT_URI,
                    PROJECTION, where, whereArgs, null);
            Log.d(TAG, "got cursor from su.db");
            while (c.moveToNext()) {
                Log.d(TAG, "row " + c.getLong(COLUMN_ID) + " dirty, handle it");
                String deleteWhere = Apps.UID + "=? AND " + Apps.EXEC_UID
                        + "=? AND " + Apps.EXEC_CMD + "=?";
                String[] deleteWhereArgs = new String[] {
                        c.getString(COLUMN_UID), c.getString(COLUMN_EXEC_UID),
                        c.getString(COLUMN_EXEC_CMD)
                };
                if (c.getInt(COLUMN_ALLOW) == Apps.AllowType.TO_DELETE) {
                    Log.d(TAG, "needs deleted");
                    pDb.delete(Apps.TABLE_NAME, deleteWhere, deleteWhereArgs);
                    cr.delete(Uri.withAppendedPath(Apps.CONTENT_URI, "clean"),
                            deleteWhere, deleteWhereArgs);
                    Log.d(TAG, "delete completed");
                    continue;
                } else if (c.getInt(COLUMN_ALLOW) == Apps.AllowType.ASK) {
                    continue;
                }
                Log.d(TAG, "Updating permissions.sqlite for " + c.getString(COLUMN_NAME));
                pDb.delete(Apps.TABLE_NAME, deleteWhere, deleteWhereArgs);
                ContentValues values = new ContentValues();
                values.put(Apps._ID, c.getLong(COLUMN_ID));
                values.put(Apps.UID, c.getInt(COLUMN_UID));
                values.put(Apps.PACKAGE, c.getString(COLUMN_PACKAGE));
                values.put(Apps.NAME, c.getString(COLUMN_NAME));
                values.put(Apps.EXEC_UID, c.getInt(COLUMN_EXEC_UID));
                values.put(Apps.EXEC_CMD, c.getString(COLUMN_EXEC_CMD));
                values.put(Apps.ALLOW, c.getInt(COLUMN_ALLOW));
                pDb.insertOrThrow(Apps.TABLE_NAME, null, values);
                Log.d(TAG, "completed update");
                ContentValues notDirty = new ContentValues();
                notDirty.put(Apps.DIRTY, "0");
                cr.update(
                        ContentUris.withAppendedId(Apps.CONTENT_URI, c.getLong(COLUMN_ID)),
                        notDirty, null, null);
                Log.d(TAG, "updated su.db as clean");
            }
        } catch (SQLiteException e) {
            // Something went wrong in the DB, schedule this service to start again
            // and quit.
            Log.e(TAG, "encountered an exception, schedule a restart", e);
            prefs.edit().putInt(ATTEMPT_PREF, attempt + 1).commit();
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 60 * 1000,
                    PendingIntent.getBroadcast(this, 0,
                            new Intent("com.noshufou.android.su.UPDATE_PERMISSIONS"), 0));
            return;
        } finally {
            if (pDb != null) {
                Log.d(TAG, "closing permissions.sqlite");
                pDb.close();
            }
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean("permissions_dirty", false)
                .putInt(ATTEMPT_PREF, 0)
                .commit();
    }

    private class PermissionsDbOpenHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "permissions.sqlite";
        private static final int DATABASE_VERSION = 8;
        
        private static final String LOG_BLOCK = "CREATE TRIGGER IF NOT EXISTS log_block " +
                "AFTER INSERT ON logs BEGIN DELETE FROM logs; END;";

        public PermissionsDbOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(Apps.CREATE);
            db.execSQL(Logs.CREATE);
            db.execSQL(LOG_BLOCK);
            makePrefs(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            int upgradeVersion = oldVersion;

            // Pattern for upgrade blocks
            //
            //    if (upgradeVersion == [the DATABASE_VERSION you set] - 1) {
            //        .. your upgrade logic ..
            //        upgradeVersion = [the DATABASE_VERSION you set]
            //    }
            if (upgradeVersion < 5) {
                // Really lazy here... Plus I can't really remember the structure
                // of the apps table before version 5...
                
                // Don't drop this table yet, causes the prompt to fail if coming from
                // 2.x
                // db.execSQL("DROP TABLE IF EXISTS permissions;");
                db.execSQL("DROP TABLE IF EXISTS apps;");
                db.execSQL("DROP TABLE IF EXISTS logs;");
                onCreate(db);
                return;
            }

            if (upgradeVersion == 5) {
                try {
                    db.execSQL("ALTER TABLE apps ADD COLUMN notifications INTEGER");
                    db.execSQL("ALTER TABLE apps ADD COLUMN logging INTEGER");
                } catch (SQLiteException e) {
                    // We're getting this exception because the columns already exist
                    // for some reason...
                    Log.e(TAG, "notifications and logging columns already exist... wut?", e);
                }
                upgradeVersion = 6;
            }

            if (upgradeVersion == 6) {
                Cursor c = db.query(Apps.TABLE_NAME, new String[] { Apps._ID, Apps.UID },
                        null, null, null, null, null);
                ContentValues values;
                while (c.moveToNext()) {
                    int uid = c.getInt(c.getColumnIndex(Apps.UID));
                    long appId = c.getLong(c.getColumnIndex(Apps._ID));
                    values = new ContentValues();
                    values.put(Apps.NAME, Util.getAppName(PermissionsDbService.this, uid, false));
                    values.put(Apps.PACKAGE, Util.getAppPackage(PermissionsDbService.this, uid));
                    db.update(Apps.TABLE_NAME, values, Apps._ID + "=?",
                            new String[] { String.valueOf(appId) });
                }
                c.close();
                upgradeVersion = 7;
            }
            
            if (upgradeVersion == 7) {
                db.execSQL(LOG_BLOCK);
                makePrefs(db);
            }
        }
        
        private void makePrefs(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS prefs " +
                    "(_id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT, value TEXT);");
            ContentValues values = new ContentValues();
            values.put("key", "notifications");
            values.put("value", "1");
            db.insert("prefs", null, values);
        }
        
    }

}
