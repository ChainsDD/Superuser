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
package com.noshufou.android.su.provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.noshufou.android.su.util.Util;

public class PermissionsProvider extends ContentProvider {
    private static final String TAG = "Su.PermissionsProvider";
    
    public static final String AUTHORITY = "com.noshufou.android.su.provider";

    public static class Apps {
        public static final String CREATE = "CREATE TABLE IF NOT EXISTS " + Apps.TABLE_NAME +
                " (_id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, package TEXT, name TEXT,  " +
                "exec_uid INTEGER, exec_cmd TEXT, allow INTEGER, notifications INTEGER, " +
                "logging INTEGER, dirty INTEGER, UNIQUE (uid,exec_uid,exec_cmd));";

        public static final Uri CONTENT_URI =
            Uri.parse("content://com.noshufou.android.su.provider/apps");
        public static final Uri COUNT_CONTENT_URI = 
            Uri.parse("content://com.noshufou.android.su.provider/apps/count");
        public static final String TABLE_NAME = "apps";
        public static final String APPS_LOGS_JOIN =
                "apps LEFT OUTER JOIN logs ON apps._id=logs.app_id";
        public static final String _ID = "_id";
        public static final String UID = "uid";
        public static final String PACKAGE = "package";
        public static final String NAME = "name";
        public static final String EXEC_UID = "exec_uid";
        public static final String EXEC_CMD = "exec_cmd";
        public static final String ALLOW = "allow";
        public static final String LAST_ACCESS = Logs.DATE;
        public static final String LAST_ACCESS_TYPE = Logs.TYPE;
        public static final String NOTIFICATIONS = "notifications";
        public static final String LOGGING = "logging";

        public static final class AllowType {
            public static final int ASK = -1;
            public static final int DENY = 0;
            public static final int ALLOW = 1;
        }

        public static final String[] DEFAULT_PROJECTION = new String[] {
            _ID, UID, PACKAGE, NAME, EXEC_UID, EXEC_CMD, ALLOW,
            Logs.DATE, Logs.TYPE, NOTIFICATIONS, LOGGING
        };

        public static final String DEFAULT_SORT_ORDER =
            "apps.allow DESC, apps.name ASC";
    }

    public static class Logs {
        public static final String CREATE = "CREATE TABLE IF NOT EXISTS " + Logs.TABLE_NAME +
                " (_id INTEGER PRIMARY KEY AUTOINCREMENT, app_id INTEGER, date INTEGER, " +
                "type INTEGER);";

        public static final Uri CONTENT_URI =
            Uri.parse("content://com.noshufou.android.su.provider/logs");
        public static final String TABLE_NAME = "logs";
        public static final String LOGS_APPS_JOIN =
                "logs LEFT OUTER JOIN apps ON logs.app_id=apps._id";
        public static final String _ID = "_id";
        public static final String APP_ID = "app_id";
        public static final String UID = Apps.UID;
        public static final String NAME = Apps.NAME;
        public static final String PACKAGE = Apps.PACKAGE;
        public static final String DATE = "date";
        public static final String TYPE = "type";

        public static final class LogType {
            public static final int DENY = 0;
            public static final int ALLOW = 1;
            public static final int CREATE = 2;
            public static final int TOGGLE = 3;
        }

        public static final String[] DEFAULT_PROJECTION = new String[] {
            _ID, APP_ID, UID, NAME, PACKAGE, DATE, TYPE
        };

        public static final String DEFAULT_SORT_ORDER = "logs.date DESC";
    }

    private static final int APPS = 100;
    private static final int APP_ID = 101;
    private static final int APP_CLEAN = 107;
    private static final int APP_ID_LOGS = 102;
    private static final int APP_UID = 103;
    private static final int APP_UID_LOGS = 104;
    private static final int APP_COUNT = 105;
    private static final int APP_COUNT_TYPE = 106;
    private static final int LOGS = 200;
    private static final int LOGS_APP_ID = 202;
    private static final int LOGS_TYPE = 203;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sUriMatcher.addURI(AUTHORITY, "apps", APPS);
        sUriMatcher.addURI(AUTHORITY, "apps/#", APP_ID);
        sUriMatcher.addURI(AUTHORITY, "apps/clean", APP_CLEAN);
        sUriMatcher.addURI(AUTHORITY, "apps/#/logs", APP_ID_LOGS);
        sUriMatcher.addURI(AUTHORITY, "apps/uid/#", APP_UID);
        sUriMatcher.addURI(AUTHORITY, "apps/uid/#/logs", APP_UID_LOGS);
        sUriMatcher.addURI(AUTHORITY, "apps/count", APP_COUNT);
        sUriMatcher.addURI(AUTHORITY, "apps/count/#", APP_COUNT_TYPE);
        sUriMatcher.addURI(AUTHORITY, "logs", LOGS);
        sUriMatcher.addURI(AUTHORITY, "logs/#", LOGS_APP_ID);
        sUriMatcher.addURI(AUTHORITY, "logs/type/#", LOGS_TYPE);
    }

    private static final HashMap<String, String> sAppsProjectionMap;
    static {
        sAppsProjectionMap = new HashMap<String, String>();
        sAppsProjectionMap.put(Apps._ID, Apps.TABLE_NAME + "." + Apps._ID + " AS _id");
        sAppsProjectionMap.put(Apps.UID, Apps.TABLE_NAME + "." + Apps.UID);
        sAppsProjectionMap.put(Apps.PACKAGE, Apps.TABLE_NAME + "." + Apps.PACKAGE);
        sAppsProjectionMap.put(Apps.NAME, Apps.TABLE_NAME + "." + Apps.NAME);
        sAppsProjectionMap.put(Apps.EXEC_UID, Apps.TABLE_NAME + "." + Apps.EXEC_UID);
        sAppsProjectionMap.put(Apps.EXEC_CMD, Apps.TABLE_NAME + "." + Apps.EXEC_CMD);
        sAppsProjectionMap.put(Apps.ALLOW, Apps.TABLE_NAME + "." + Apps.ALLOW);
        sAppsProjectionMap.put(Apps.LAST_ACCESS, Logs.TABLE_NAME + "."  + Logs.DATE);
        sAppsProjectionMap.put(Apps.LAST_ACCESS_TYPE, Logs.TABLE_NAME + "." + Logs.TYPE);
        sAppsProjectionMap.put(Apps.NOTIFICATIONS, Apps.TABLE_NAME + "." + Apps.NOTIFICATIONS);
        sAppsProjectionMap.put(Apps.LOGGING, Apps.TABLE_NAME + "." + Apps.LOGGING);
    }

    private static final HashMap<String, String> sLogsProjectionMap;
    static {
        sLogsProjectionMap = new HashMap<String, String>();
        sLogsProjectionMap.put(Logs._ID, Logs.TABLE_NAME + "." + Logs._ID + " AS _id");
        sLogsProjectionMap.put(Logs.APP_ID, Logs.TABLE_NAME + "." + Logs.APP_ID);
        sLogsProjectionMap.put(Logs.UID, Apps.TABLE_NAME + "." + Apps.UID);
        sLogsProjectionMap.put(Logs.NAME, Apps.TABLE_NAME + "." + Apps.NAME);
        sLogsProjectionMap.put(Logs.PACKAGE, Apps.TABLE_NAME + "." + Apps.PACKAGE);
        sLogsProjectionMap.put(Logs.DATE, Logs.TABLE_NAME + "." + Logs.DATE);
        sLogsProjectionMap.put(Logs.TYPE, Logs.TABLE_NAME + "." + Logs.TYPE);
    }

    private Context mContext;
    private SuDbOpenHelper mDbHelper = null;
    private SQLiteDatabase mDb = null;

    @Override
    public boolean onCreate() {
        mContext = getContext();
        mDbHelper = new SuDbOpenHelper(mContext);
        return (mDbHelper == null)?false:true;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case APPS:
            return "vnd.android.cursor.dir/vnd.noshufou.superuser.apps ";
        case APP_ID:
        case APP_UID:
        case APP_COUNT:
        case APP_COUNT_TYPE:
            return "vnd.android.cursor.item/vnd.noshufou.superuser.apps ";
        case APP_ID_LOGS:
        case APP_UID_LOGS:
        case LOGS:
        case LOGS_APP_ID:
        case LOGS_TYPE:
            return "vnd.android.cursor.dir/vnd.noshufou.superuser.logs ";
        default:
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (!ensureDb()) return null;

        SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();
        String[] defaultProjection = null;
        String groupBy = null;

        int uriMatch = sUriMatcher.match(uri);
        // Set up table and default projection
        switch (uriMatch) {
        case APPS:
        case APP_ID:
        case APP_UID:
            qBuilder.setTables(Apps.APPS_LOGS_JOIN);
            qBuilder.setProjectionMap(sAppsProjectionMap);
            defaultProjection = Apps.DEFAULT_PROJECTION;
//            qBuilder.appendWhere("apps.allow!=-1"); // Leave out apps only there for Log purposes
            groupBy = Apps.TABLE_NAME + "." + Apps._ID;
            sortOrder = sortOrder==null?Apps.DEFAULT_SORT_ORDER:sortOrder;
            sortOrder = sortOrder + (!TextUtils.isEmpty(sortOrder)?", ":"");
            sortOrder = sortOrder + Logs.DATE + " DESC";
            break;
        case APP_ID_LOGS:
        case APP_UID_LOGS:
        case LOGS:
        case LOGS_APP_ID:
        case LOGS_TYPE:
            qBuilder.setTables(Logs.LOGS_APPS_JOIN);
            qBuilder.setProjectionMap(sLogsProjectionMap);
            defaultProjection = Logs.DEFAULT_PROJECTION;
            sortOrder = sortOrder==null?Logs.DEFAULT_SORT_ORDER:sortOrder;
            break;
        case APP_COUNT:
        case APP_COUNT_TYPE:
            qBuilder.setTables(Apps.TABLE_NAME);
            defaultProjection = new String[] { "COUNT() as rows" };
            break;
        default:
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        // Append a particular item if necessary
        switch (uriMatch) {
        case APP_ID:
            qBuilder.appendWhere(" apps._id=" + uri.getPathSegments().get(1));
            break;
        case APP_ID_LOGS:
        case LOGS_APP_ID:
            qBuilder.appendWhere("apps._id=" + uri.getPathSegments().get(1));
            break;
        case APP_UID:
            qBuilder.appendWhere(" apps.uid=" + uri.getPathSegments().get(2));
            break;
        case APP_UID_LOGS:
            qBuilder.appendWhere("apps.uid=" + uri.getPathSegments().get(2));
            break;
        case LOGS_TYPE:
            qBuilder.appendWhere("logs.type=" + uri.getPathSegments().get(2));
            break;
        case APP_COUNT_TYPE:
            qBuilder.appendWhere("apps.allow=" + uri.getPathSegments().get(2));
        }

        // TODO: Check columns in incoming projection to make sure they're valid
        projection = projection==null?defaultProjection:projection;
        Cursor c = null;
        try {
            c = qBuilder.query(mDb,
                    projection,
                    selection,
                    selectionArgs,
                    groupBy,
                    null,
                    sortOrder);
        } catch (SQLiteException e) {
            Log.e(TAG, "Query failed, returning null cursor.", e);
        }
        c.setNotificationUri(mContext.getContentResolver(), uri);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (!ensureDb()) return null;

        long rowId = 0;
        Uri returnUri = null;

        switch (sUriMatcher.match(uri)) {
        case APPS:
            // TODO: Check validity of incoming data before inserting it
            try {
                rowId = mDb.insertOrThrow(Apps.TABLE_NAME, null, values);
            } catch (SQLException e) {
                String where = Apps.UID + "=? AND " + Apps.EXEC_UID + "=? AND "
                        + Apps.EXEC_CMD + "=?";
                String[] whereArgs = new String[] { values.getAsString(Apps.UID),
                        values.getAsString(Apps.EXEC_UID),
                        values.getAsString(Apps.EXEC_CMD)};
                mDb.update(Apps.TABLE_NAME, values,
                        where,
                        whereArgs);
                Cursor c = mDb.query(Apps.TABLE_NAME, new String[] { Apps._ID },
                        where,
                        whereArgs,
                        null, null, null);
                if (c.moveToFirst()) {
                    rowId = c.getLong(0);
                }
                c.close();
            }

            if (values.getAsInteger(Apps.ALLOW) != Apps.AllowType.ASK) {
                ContentValues logValues = new ContentValues();
                logValues.put(Logs.APP_ID, rowId);
                logValues.put(Logs.DATE, System.currentTimeMillis());
                logValues.put(Logs.TYPE, Logs.LogType.CREATE);
                mDb.insert(Logs.TABLE_NAME, null, logValues);
                
                Util.writeStoreFile(mContext,
                        values.getAsInteger(Apps.UID),
                        values.getAsInteger(Apps.EXEC_UID),
                        values.getAsString(Apps.EXEC_CMD),
                        values.getAsInteger(Apps.ALLOW));
            }
            returnUri = ContentUris.withAppendedId(Apps.CONTENT_URI, rowId);

            break;
        case APP_ID_LOGS:
        case LOGS_APP_ID:
            // TODO: Check validity of incoming data before inserting it
            values.put(Logs.APP_ID, uri.getPathSegments().get(1));
            rowId = mDb.insert(Logs.TABLE_NAME, null, values);
            returnUri = ContentUris.withAppendedId(Logs.CONTENT_URI, rowId);
            // Logs are special, they should also notify of a change to the uri
            // logs/app_id, and all apps uri
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Logs.CONTENT_URI,
                            Long.parseLong(uri.getPathSegments().get(1))),
                    null);
            getContext().getContentResolver().notifyChange(Apps.CONTENT_URI, null);
            break;
        default:
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        if (rowId > -1) {
            getContext().getContentResolver().notifyChange(returnUri, null);
            return returnUri;
        }
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        if (!ensureDb()) return -1;

        int count = 0;

        switch (sUriMatcher.match(uri)) {
        case APP_ID:
            count = mDb.update(Apps.TABLE_NAME, values,
                    Apps._ID + "=" + uri.getPathSegments().get(1) +
                    (!TextUtils.isEmpty(selection)? " AND (" +
                            selection  + ")":""),
                    selectionArgs);
            Cursor c = mDb.query(Apps.TABLE_NAME,
                    null,
                    Apps._ID + "=" + uri.getPathSegments().get(1) +
                    (!TextUtils.isEmpty(selection)? " AND (" +
                            selection  + ")":""),
                    selectionArgs,
                    null, null, null);
            if (c.moveToFirst()) {
                Util.writeStoreFile(mContext,
                        c.getInt(c.getColumnIndex(Apps.UID)),
                        c.getInt(c.getColumnIndex(Apps.EXEC_UID)),
                        c.getString(c.getColumnIndex(Apps.EXEC_CMD)),
                        c.getInt(c.getColumnIndex(Apps.ALLOW)));
            }
            c.close();
            break;
        default:
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (!ensureDb()) return -1;

        int count = 0;

        switch (sUriMatcher.match(uri)) {
        case APP_ID:
            Cursor c = mDb.query(Apps.TABLE_NAME,
                    new String[] { Apps.UID, Apps.EXEC_UID },
                    Apps._ID + "=" + uri.getPathSegments().get(1) +
                    (!TextUtils.isEmpty(selection)? " AND (" +
                            selection  + ")":""),
                    selectionArgs,
                    null, null, null);
            if (c.moveToFirst()) {
                File file = new File(mContext.getFilesDir().getAbsolutePath() + "/stored/" +
                        c.getInt(c.getColumnIndex(Apps.UID)) + "-" +
                        c.getInt(c.getColumnIndex(Apps.EXEC_UID)));
                file.delete();
            }
            c.close();
            count = mDb.delete(Apps.TABLE_NAME,
                    Apps._ID + "=" + uri.getPathSegments().get(1) +
                    (!TextUtils.isEmpty(selection)? " AND (" +
                            selection  + ")":""),
                    selectionArgs);
            // No break here so we can fall through and delete associated logs
        case APP_ID_LOGS:
        case LOGS_APP_ID:
            count += mDb.delete(Logs.TABLE_NAME,
                    Logs.APP_ID + "=" + uri.getPathSegments().get(1) +
                    (!TextUtils.isEmpty(selection)? " AND (" +
                            selection  + ")":""),
                    selectionArgs);
            break;
        case APP_CLEAN:
            count = mDb.delete(Apps.TABLE_NAME, selection, selectionArgs);
            break;
        case LOGS:
            count = mDb.delete(Logs.TABLE_NAME, selection, selectionArgs);
            break;
        default:
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        getContext().getContentResolver().notifyChange(Apps.CONTENT_URI, null);
        return count;
    }

    private boolean ensureDb() {
        if (mDb == null) {
            mDb = mDbHelper.getWritableDatabase();
            if (mDb == null) return false;
        }
        return true;
    }

    private class SuDbOpenHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "su.db";
        private static final int DATABASE_VERSION = 6;

        SuDbOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(Apps.CREATE);
            db.execSQL(Logs.CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Pattern for upgrade blocks
            //
            //    if (upgradeVersion == [the DATABASE_VERSION you set] - 1) {
            //        .. your upgrade logic ..
            //        upgradeVersion = [the DATABASE_VERSION you set]
            //    }
            int upgradeVersion = oldVersion;

            if (upgradeVersion == 1) {
                // Don't do anything here anymore, since we don't want to
                // open the permissions.sqlite database
                upgradeVersion = 2;
            }

            if (upgradeVersion == 2) {
                try {
                    db.execSQL("ALTER TABLE apps ADD COLUMN dirty INTEGER");
                } catch (SQLiteException e) {
                    // We're getting this exception because the column already exists
                    // for some reason...
                    Log.e(TAG, "dirty column already exists... wut?", e);
                }
                // Set everything to dirty
                upgradeVersion = 3;
            }

            if (upgradeVersion == 3) {
                Cursor c = db.query(Apps.TABLE_NAME, 
                        new String[] { Apps._ID, Apps.UID, Apps.NAME },
                        null, null, null, null, null);
                while (c.moveToNext()) {
                    if (c.getString(2).equalsIgnoreCase("unknown")) {
                        ContentValues values = new ContentValues();
                        values.put(Apps.NAME, Util.getAppName(mContext, c.getInt(1), false));
                        values.put(Apps.PACKAGE, Util.getAppPackage(mContext, c.getInt(1)));
                        db.update(Apps.TABLE_NAME, values, Apps._ID + "=?",
                                new String[] { String.valueOf(c.getLong(0)) });
                    }
                }
                c.close();
                upgradeVersion = 4;
            }

            if (upgradeVersion <= 5) {
                Cursor c = db.query(Apps.TABLE_NAME, null, null, null, null, null, null);
                while (c.moveToNext()) {
                    Util.writeStoreFile(mContext,
                            c.getInt(c.getColumnIndex(Apps.UID)),
                            c.getInt(c.getColumnIndex(Apps.EXEC_UID)),
                            c.getString(c.getColumnIndex(Apps.EXEC_CMD)),
                            c.getInt(c.getColumnIndex(Apps.ALLOW)));
                }
                c.close();
                mContext.deleteDatabase("permissions.sqlite");
                upgradeVersion = 6;
            }

        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO Auto-generated method stub
        }
    }

}
