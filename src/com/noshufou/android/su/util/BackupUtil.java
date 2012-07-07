package com.noshufou.android.su.util;

import com.noshufou.android.su.preferences.Preferences;
import com.noshufou.android.su.provider.PermissionsProvider.Apps;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public class BackupUtil {
    private static final String TAG = "Su.BackupUtil";

    public static boolean makeBackup(Context context) {
        boolean status = false;
        FileOutputStream file = null;
        XmlSerializer serializer = Xml.newSerializer();
        try {
        file = new FileOutputStream(
                new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/subackup.xml"));
            serializer.setOutput(file, "UTF-8");
            serializer.startDocument(null, true);
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startTag("", "backup");

            status = backupApps(context, serializer);
            status = backupPrefs(context, serializer);

            serializer.endTag("", "backup");
            serializer.endDocument();
            serializer.flush();
            file.close();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException", e);
            return false;
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException", e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            return false;
        }
        return status;
    }
    
    private static boolean backupApps(Context context, XmlSerializer serializer) {
        Cursor c = context.getContentResolver().query(Apps.CONTENT_URI, null, null, null, null);
        if (c == null) {
            return false;
        } else if (c.getCount() == 0) {
            c.close();
            return true;
        }
        try {
            serializer.startTag("", "apps");
            while (c.moveToNext()) {
                serializer.startTag("", "app");
                Log.d(TAG, c.getString(c.getColumnIndex(Apps.PACKAGE)));
                serializer.attribute("", Apps.PACKAGE,
                        c.getString(c.getColumnIndex(Apps.PACKAGE)));
                serializer.attribute("", Apps.NAME,
                        c.getString(c.getColumnIndex(Apps.NAME)));
                serializer.attribute("", Apps.EXEC_UID,
                        c.getString(c.getColumnIndex(Apps.EXEC_UID)));
                String cmd = c.getString(c.getColumnIndex(Apps.EXEC_CMD));
                cmd = cmd == null ? "" : cmd;
                serializer.attribute("", Apps.EXEC_CMD, cmd);
                serializer.attribute("", Apps.ALLOW,
                        c.getString(c.getColumnIndex(Apps.ALLOW)));
                String notifications = c.getString(c.getColumnIndex(Apps.NOTIFICATIONS));
                if (notifications != null) {
                    serializer.attribute("", Apps.NOTIFICATIONS, notifications);
                }
                String logging = c.getString(c.getColumnIndex(Apps.LOGGING));
                if (logging != null) {
                    serializer.attribute("", Apps.LOGGING, logging);
                }
                serializer.endTag("", "app");
            }
            serializer.endTag("", "apps");
        } catch (IOException e) {
            Log.e(TAG, "Problem backing up apps", e);
            return false;
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return true;
    }
    
    private static boolean backupPrefs(Context context, XmlSerializer serializer)
            throws IOException {
        Map<String, ?> prefs = 
            PreferenceManager.getDefaultSharedPreferences(context).getAll();
        if (prefs.isEmpty()) {
            return true;
        }

        serializer.startTag("", "prefs");
        for (String key: prefs.keySet()) {
            String type = "unknown";
            if (!key.startsWith("pref_") && !key.equals("pin")) {
                continue;
            }
            
            Object value = prefs.get(key);
            if (value instanceof Boolean) {
                type = "boolean";
            } else if (value instanceof String) {
                type = "string";
            } else if (value instanceof Integer) {
                type = "int";
            } else if (value instanceof Long) {
                type = "long";
            }
            serializer.startTag("", type);
            serializer.attribute("", "name", key);
            if (type.equals("string")) {
                serializer.text(String.valueOf(value));
            } else {
                serializer.attribute("", "value", String.valueOf(value));
            }
            serializer.endTag("", type);
        }
        serializer.endTag("", "prefs");
        return true;
    }
    
    public static int restoreBackup(Context context) {
        XmlPullParser parser = Xml.newPullParser();
        FileInputStream file = null;
        int appsRestored = 0;
        try {
            file = new FileInputStream(
                    new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                            + "/subackup.xml"));
            parser.setInput(file, "UTF-8");
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (parser.getName().equalsIgnoreCase("apps")) {
                        parser.next();
                        appsRestored = restoreApps(context, parser);
                    } else if (parser.getName().equalsIgnoreCase("prefs")) {
                        parser.next();
                        restorePrefs(context, parser);
                    }
                    break;
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Error restoring backup", e);
            return -1;
        } catch (IOException e) {
            Log.e(TAG, "Error restoring backup", e);
            return -1;
       }
       return appsRestored;
    }

    private static int restoreApps(Context context, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int appsRestored = 0;
        PackageManager pm = context.getPackageManager();
        ContentResolver cr = context.getContentResolver();
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT &&
                !(eventType == XmlPullParser.END_TAG 
                        && parser.getName().equalsIgnoreCase("apps"))) {
            if (eventType == XmlPullParser.START_TAG &&
                    parser.getName().equalsIgnoreCase("app")) {
                String pkg = parser.getAttributeValue("", Apps.PACKAGE);
                try {
                    int uid = pm.getApplicationInfo(pkg, 0).uid;
                    ContentValues values = new ContentValues();
                    values.put(Apps.UID, uid);
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        values.put(parser.getAttributeName(i),
                                parser.getAttributeValue(i));
                    }
                    cr.insert(Apps.CONTENT_URI, values);
                    appsRestored++;
                } catch (NameNotFoundException e) {
                    Log.i(TAG, "package" + pkg + " not installed, skipping restore");
                }
            }
            eventType = parser.next();
        }
        return appsRestored;
    }
    
    private static void restorePrefs(Context context, XmlPullParser parser)
            throws XmlPullParserException, IOException {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        editor.clear();
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT &&
                !(eventType == XmlPullParser.END_TAG &&
                        parser.getName().equalsIgnoreCase("prefs"))) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equalsIgnoreCase("boolean")) {
                    editor.putBoolean(parser.getAttributeValue("", "name"),
                            Boolean.parseBoolean(parser.getAttributeValue("", "value")));
                } else if (parser.getName().equalsIgnoreCase("string")) {
                    editor.putString(parser.getAttributeValue("", "name"), parser.nextText());
                } else if (parser.getName().equalsIgnoreCase("int")) {
                    editor.putInt(parser.getAttributeValue("", "name"),
                            Integer.parseInt(parser.getAttributeValue("", "value")));
                } else if (parser.getName().equalsIgnoreCase("long")) {
                    editor.putLong(parser.getAttributeValue("", "name"),
                            Long.parseLong(parser.getAttributeValue("", "value")));
                }
            }
            eventType = parser.next();
        }
        editor.commit();

        if (prefs.getBoolean(Preferences.PIN, false) && !prefs.contains("pin")) {
            prefs.edit().putBoolean(Preferences.PIN, false).commit();
        }
    }
}
