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
package com.noshufou.android.su.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseArray;

import com.noshufou.android.su.HomeActivity;
import com.noshufou.android.su.R;
import com.noshufou.android.su.UpdaterActivity;
import com.noshufou.android.su.preferences.Preferences;
import com.noshufou.android.su.preferences.PreferencesActivity;
import com.noshufou.android.su.preferences.PreferencesActivityHC;
import com.noshufou.android.su.provider.PermissionsProvider.Apps.AllowType;
import com.noshufou.android.su.service.UpdaterService;

public class Util {
    private static final String TAG = "Su.Util";
    
    public static final int MALICIOUS_NOT = 0;
    public static final int MALICIOUS_UID = 1;
    public static final int MALICIOUS_RESPOND = 2;
    public static final int MALICIOUS_PROVIDER_WRITE = 3;
    
    private static final SparseArray<String> sSystemUids = new SparseArray<String>(32);
    static {
        sSystemUids.put(0, "root");
        sSystemUids.put(1000, "system");
        sSystemUids.put(1001, "radio");
        sSystemUids.put(1002, "bluetooth");
        sSystemUids.put(1003, "graphics");
        sSystemUids.put(1004, "input");
        sSystemUids.put(1005, "audio");
        sSystemUids.put(1006, "camera");
        sSystemUids.put(1007, "log");
        sSystemUids.put(1008, "compass");
        sSystemUids.put(1009, "mount");
        sSystemUids.put(1010, "wifi");
        sSystemUids.put(1011, "adb");
        sSystemUids.put(1012, "install");
        sSystemUids.put(1013, "media");
        sSystemUids.put(1014, "dhcp");
        sSystemUids.put(1015, "sdcard_rw");
        sSystemUids.put(1016, "vpn");
        sSystemUids.put(1017, "keystore");
        sSystemUids.put(1018, "usb");
        sSystemUids.put(1021, "gps");
        sSystemUids.put(1025, "nfc");
        sSystemUids.put(2000, "shell");
        sSystemUids.put(2001, "cache");
        sSystemUids.put(2002, "diag");
        sSystemUids.put(3001, "net_bt_admin");
        sSystemUids.put(3002, "net_bt");
        sSystemUids.put(3003, "inet");
        sSystemUids.put(3004, "net_raw");
        sSystemUids.put(3005, "net_admin");
        sSystemUids.put(9998, "misc");
        sSystemUids.put(9999, "nobody");
    }
    
    public static class MenuId {
        public static final int ELITE = 0;
        public static final int TOGGLE = 1;
        public static final int FORGET = 2;
        public static final int INFO = 3;
        public static final int LOG = 4;
        public static final int CLEAR_LOG = 5;
        public static final int USE_APP_SETTINGS = 6;
        public static final int NOTIFICATIONS = 7;
        public static final int LOGGING = 8;
        public static final int TEMP_UNROOT = 9;
        public static final int OTA_SURVIVE = 10;
        public static final int PREFERENCES = 11;
    }
    
    public static class VersionInfo {
        public String version = "";
        public int versionCode = 0;
    }

    public static String getAppName(Context c, int uid, boolean withUid) {
        if (sSystemUids.get(uid) != null) {
            return sSystemUids.get(uid);
        }

        PackageManager pm = c.getPackageManager();
        String appName = "Unknown";
        String[] packages = pm.getPackagesForUid(uid);

        if (packages != null) {
            try {
                if (packages.length == 1) {
                    appName = pm.getApplicationLabel(pm.getApplicationInfo(packages[0], 0))
                            .toString();
                } else if (packages.length > 1) {
                    appName = "";
                    for (int i = 0; i < packages.length; i++) {
                        appName += packages[i];
                        if (i < packages.length - 1) {
                            appName += ", ";
                        }
                    }
                }
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Package name not found", e);
            }
        } else {
            Log.e(TAG, "Package not found for uid " + uid);
        }

        if (withUid) {
            appName += " (" + uid + ")";
        }

        return appName;
    }

    public static String getAppPackage(Context c, int uid) {
        if (sSystemUids.get(uid) != null) {
            return sSystemUids.get(uid);
        }

        PackageManager pm = c.getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        String appPackage = "unknown";

        if (packages != null) {
            if (packages.length == 1) {
                appPackage = packages[0];
            } else if (packages.length > 1) {
                appPackage = "";
                for (int i = 0; i < packages.length; i++) {
                    appPackage += packages[i];
                    if (i < packages.length - 1) {
                        appPackage += ", ";
                    }
                }
            }
        } else {
            Log.e(TAG, "Package not found");
        }

        return appPackage;
    }

    public static Drawable getAppIcon(Context c, int uid) {
        PackageManager pm = c.getPackageManager();
        Drawable appIcon = c.getResources().getDrawable(R.drawable.sym_def_app_icon);
        String[] packages = pm.getPackagesForUid(uid);

        if (packages != null) {
//            if (packages.length == 1) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packages[0], 0);
                appIcon = pm.getApplicationIcon(appInfo);
            } catch (NameNotFoundException e) {
                Log.e(TAG, "No package found matching with the uid " + uid);
            }
//            }
        } else {
            Log.e(TAG, "Package not found for uid " + uid);
        }

        return appIcon;
    }

    public static Drawable getStatusIconDrawable(Context context, int allow) {
        int[][] statusButtons = {
                { R.drawable.perm_deny_dot, R.drawable.perm_allow_dot },
                { R.drawable.perm_deny_emo, R.drawable.perm_allow_emo }
        };

        String iconTypeString = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(Preferences.STATUS_ICON_TYPE, "emote");
        int statusIconType = 1;
        if (iconTypeString.equals("dot")) {
            statusIconType = 0;
        } else if (iconTypeString.equals("emote")) {
            statusIconType = 1;
        }

        if (allow < 0 || allow > 1) {
            Log.e(TAG, "Bad value given to getStatusButtonDrawable(int). Expecting 0 or 1, got " + allow);
            return null;
        }

        Drawable drawable = context.getResources().getDrawable(statusButtons[statusIconType][allow]);
        return drawable;
    }

    public static String getUidName(Context c, int uid, boolean withUid) {
        PackageManager pm = c.getPackageManager();
        String uidName= "";
        if (uid == 0) {
            uidName = "root";
        } else {
            pm.getNameForUid(uid);
        }

        if (withUid) {
            uidName += " (" + uid + ")";
        }

        return uidName;
    }
    
    public static void goHome(Context context) {
        // Be clever here, otherwise the home button doesn't work as advertised.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Intent intent;
        if (prefs.getBoolean(Preferences.GHOST_MODE, false)) {
            intent = new Intent(context, HomeActivity.class);
        } else {
            intent = new Intent();
            intent.setComponent(new ComponentName("com.noshufou.android.su",
                    "com.noshufou.android.su.Su"));
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    public static String getSuVersion() {
        Process process = null;
        String inLine = null;

        try {
            process = Runtime.getRuntime().exec("sh");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader is = new BufferedReader(new InputStreamReader(
                    new DataInputStream(process.getInputStream())), 64);
            os.writeBytes("su -v\n");

            // We have to hold up the thread to make sure that we're ready to read
            // the stream, using increments of 5ms makes it return as quick as
            // possible, and limiting to 1000ms makes sure that it doesn't hang for
            // too long if there's a problem.
            for (int i = 0; i < 400; i++) {
                if (is.ready()) {
                    break;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Sleep timer got interrupted...");
                }
            }
            if (is.ready()) {
                inLine = is.readLine();
                if (inLine != null) {
                    return inLine;
                }
            } else {
                os.writeBytes("exit\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Problems reading current version.", e);
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return null;
    }

    public static int getSuVersionCode() {
        Process process = null;
        String inLine = null;

        try {
            process = Runtime.getRuntime().exec("sh");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader is = new BufferedReader(new InputStreamReader(
                    new DataInputStream(process.getInputStream())), 64);
            os.writeBytes("su -v\n");

            // We have to hold up the thread to make sure that we're ready to read
            // the stream, using increments of 5ms makes it return as quick as
            // possible, and limiting to 1000ms makes sure that it doesn't hang for
            // too long if there's a problem.
            for (int i = 0; i < 400; i++) {
                if (is.ready()) {
                    break;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Sleep timer got interrupted...");
                }
            }
            if (is.ready()) {
                inLine = is.readLine();
                if (inLine != null && Integer.parseInt(inLine.substring(0, 1)) > 2) {
                    inLine = null;
                    os.writeBytes("su -V\n");
                    inLine = is.readLine();
                    if (inLine != null) {
                        return Integer.parseInt(inLine);
                    }
                } else {
                    return 0;
                }
            } else {
                os.writeBytes("exit\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Problems reading current version.", e);
            return 0;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return 0;
    }
    
    public static VersionInfo getSuVersionInfo() {
        VersionInfo info = new VersionInfo();
        Process process = null;
        String inLine = null;

        try {
            process = Runtime.getRuntime().exec("sh");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader is = new BufferedReader(new InputStreamReader(
                    new DataInputStream(process.getInputStream())), 64);
            os.writeBytes("su -v\n");

            // We have to hold up the thread to make sure that we're ready to read
            // the stream, using increments of 5ms makes it return as quick as
            // possible, and limiting to 1000ms makes sure that it doesn't hang for
            // too long if there's a problem.
            for (int i = 0; i < 400; i++) {
                if (is.ready()) {
                    break;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Sleep timer got interrupted...");
                }
            }
            if (is.ready()) {
                inLine = is.readLine();
                if (inLine != null) {
                    info.version = inLine;
                }
            } else {
                // If 'su -v' isn't supported, neither is 'su -V'. return legacy info
                os.writeBytes("exit\n");
                info.version = "legacy";
                info.versionCode = 0;
                return info;
            }

            os.writeBytes("su -v\n");

            // We have to hold up the thread to make sure that we're ready to read
            // the stream, using increments of 5ms makes it return as quick as
            // possible, and limiting to 1000ms makes sure that it doesn't hang for
            // too long if there's a problem.
            for (int i = 0; i < 400; i++) {
                if (is.ready()) {
                    break;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Sleep timer got interrupted...");
                }
            }
            if (is.ready()) {
                inLine = is.readLine();
                if (inLine != null && Integer.parseInt(inLine.substring(0, 1)) > 2) {
                    inLine = null;
                    os.writeBytes("su -V\n");
                    inLine = is.readLine();
                    if (inLine != null) {
                        info.versionCode = Integer.parseInt(inLine);
                    }
                } else {
                    info.versionCode = 0;
                }
            } else {
                os.writeBytes("exit\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Problems reading current version.", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return info;
    }

    public static VersionInfo getSuperuserVersionInfo(Context context) {
        VersionInfo info = new VersionInfo();
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pInfo = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            info.version = pInfo.versionName;
            info.versionCode = pInfo.versionCode;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Superuser is not installed?", e);
        }
        return info;
    }

    public static boolean isSuCurrent() {
        if (getSuVersionCode() < 10) {
            return false;
        }
        return true;
    }

    public static String formatDate(Context context, long date) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String format = prefs.getString("pref_date_format", "default");
        if (format.equals("default")) {
            return DateFormat.getDateFormat(context).format(date);
        } else {
            return (String)DateFormat.format(format, date);
        }
    }

    public static String formatTime(Context context, long time) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean hour24 = prefs.getBoolean("pref_24_hour_format", true);
        boolean showSeconds = prefs.getBoolean("pref_show_seconds", false);
        String hour = "kk";
        String min = "mm";
        String sec = ":ss";
        String post = "";

        if (hour24) {
            hour = "kk";
        } else {
            hour = "hh";
            post = "aa";
        }

        if (showSeconds) {
            sec = ":ss";
        } else {
            sec = "";
        }

        String format = String.format("%s:%s%s%s", hour, min, sec, post);
        return (String)DateFormat.format(format, time);
    }

    public static String formatDateTime(Context context, long date) {
        return formatDate(context, date) + " " + formatTime(context, date);
    }

    public static boolean elitePresent(Context context, boolean versionCheck, int minVersion) {
        PackageManager pm = context.getPackageManager();
        int sigs = pm.checkSignatures("com.noshufou.android.su", "com.noshufou.android.su.elite");
        if (sigs != PackageManager.SIGNATURE_MATCH) {
            return false;
        } else {
            if (versionCheck) {
                PackageInfo pi;
                try {
                    pi = pm.getPackageInfo("com.noshufou.android.su.elite", 0);
                    if (pi.versionCode >= minVersion) {
                        return true;
                    } else {
                        return false;
                    }
                } catch (NameNotFoundException e) {
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    public static void launchPreferences(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            context.startActivity(new Intent(context, PreferencesActivity.class));
        } else {
            context.startActivity(new Intent(context, PreferencesActivityHC.class));
        }
    }

    public static String getHash(String pin) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            Log.e("Utils", "NoSuchAlgorithm, storing in plain text...", e);
            return pin;
        }
        digest.reset();
        try {
            byte[] input = digest.digest(pin.getBytes("UTF-8"));
            String base64 = Base64.encode(input);
            return base64;
        } catch (UnsupportedEncodingException e) {
            Log.e("Utils", "UnsupportedEncoding, storing in plain text...", e);
            return pin;
        }
    }

    public static boolean checkPin(Context context, String pin) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String setPin = prefs.getString("pin", "");
        return getHash(pin).equals(setPin);
    }

    public static void toggleAppIcon(Context context, boolean newState) {
        ComponentName componentName = new ComponentName("com.noshufou.android.su",
                "com.noshufou.android.su.Su");
        context.getPackageManager().setComponentEnabledSetting(componentName, 
                newState ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static List<String> findMaliciousPackages(Context context) {
        List<String> maliciousApps = new ArrayList<String>();
        List<PackageInfo> installedApps = context.getPackageManager()
                .getInstalledPackages(PackageManager.GET_PERMISSIONS);

        for (PackageInfo pkg : installedApps) {
            int result = isPackageMalicious(context, pkg);
            if (result != 0) {
                maliciousApps.add(pkg.packageName + ":" + result);
            }
        }
        return maliciousApps;
    }

    public static int isPackageMalicious(Context context, PackageInfo packageInfo) {
        // If the package being checked is this one, it's not malicious
        if (packageInfo.packageName.equals(context.getPackageName())) {
            return MALICIOUS_NOT;
        }

        // If the package being checked shares a UID with Superuser, it's
        // probably malicious
        if (packageInfo.applicationInfo.uid == context.getApplicationInfo().uid) {
            return MALICIOUS_UID;
        }

        // Finally we check for any permissions that other apps should not have.
        if (packageInfo.requestedPermissions != null) {
            String[] bannedPermissions = new String[] { 
                "com.noshufou.android.su.RESPOND",
                "com.noshufou.android.su.provider.WRITE"
            };
            for (String s : packageInfo.requestedPermissions) {
                for (int i = 0; i < 2; i++) {
                    if (s.equals(bannedPermissions[i]) &&
                        context.getPackageManager().
                                checkPermission(bannedPermissions[i], packageInfo.packageName)
                                == PackageManager.PERMISSION_GRANTED) {
                        return i + 2;
                    }
                }
            }
        }

        return MALICIOUS_NOT;
    }

    public static void showOutdatedNotification(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Preferences.OUTDATED_NOTIFICATION, true)) {
            NotificationManager nm = 
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                    new Intent(context, UpdaterActivity.class), 0);
            Notification notification = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.stat_su)
                    .setTicker(context.getText(R.string.notif_outdated_ticker))
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle(context.getText(R.string.notif_outdated_title))
                    .setContentText(context.getText(R.string.notif_outdated_text))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .getNotification();
            nm.notify(UpdaterService.NOTIFICATION_ID, notification);
        }
    }

    public static String whichSu() {
        for (String s : System.getenv("PATH").split(":")) {
            File su = new File(s + "/su");
            if (su.exists() && su.isFile()) {
                try {
                    if (su.getAbsolutePath().equals(su.getCanonicalPath())) {
                        return su.getAbsolutePath();
                    }
                } catch (IOException e) {
                    // If we get an exception here, it's probably not the right file,
                    // Log it and move on
                    Log.w(TAG, "IOException while finding canonical path of " + su.getAbsolutePath(), e);
                }
            }
        }
        return null;
    }

    public static String ensureSuTools(Context context) {
        File suTools = context.getFileStreamPath("sutools");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int sutoolsVersion = prefs.getInt("sutoolsVersion", 0);
        
        PackageManager pm = context.getPackageManager();
        int appVersionCode;
        try {
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            appVersionCode = info.versionCode;
        } catch (NameNotFoundException e) {
            appVersionCode = 0;
        }

        if (suTools.exists() && appVersionCode == sutoolsVersion) {
            return suTools.getAbsolutePath();
        }
        Log.d(TAG, "extracting sutools");

        try {
            copyFromAssets(context, "sutools-" + Build.CPU_ABI.split("-")[0], "sutools");
        } catch (IOException e) {
            Log.e(TAG, "Could not extract sutools");
            return null;
        }
        
        Process process;
        try {
            process = new ProcessBuilder()
                    .command("chmod", "700", suTools.getAbsolutePath())
                    .redirectErrorStream(true).start();
            process.waitFor();
            process.destroy();
        } catch (IOException e) {
            Log.e(TAG, "Failed to set filemode of sutools");
            return null;
        } catch (InterruptedException e) {
            Log.w(TAG, "process interrupted");
        }

        prefs.edit().putInt("sutoolsVersion", appVersionCode).commit();
        return suTools.getAbsolutePath();
    }

    public static final void copyFromAssets(Context context, String source, String destination)
            throws IOException {

        // read file from the apk
        InputStream is = context.getAssets().open(source);
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();

        // write files in app private storage
        FileOutputStream output = context.openFileOutput(destination, Context.MODE_PRIVATE);
        output.write(buffer);
        output.close();

        Log.d(TAG, source + " asset copied to " + destination);
    }

    public static final Boolean isSuid(Context context, String filename) {

        try {

            Process p = Runtime.getRuntime().exec(context.getFilesDir() + "/test -u " + filename);
            p.waitFor();
            if (p.exitValue() == 0) {
                Log.d(TAG, filename + " is set-user-ID");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, filename + " is not set-user-ID");
        return false;

    }

    public static ArrayList<String> run(String command) {
        return run("/system/bin/sh", command);
    }

    public static ArrayList<String> run(String shell, String command) {
        return run(shell, new String[] {
                command
        });
    }

    public static ArrayList<String> run(String shell, ArrayList<String> commands) {
        String[] commandsArray = new String[commands.size()];
        commands.toArray(commandsArray);
        return run(shell, commandsArray);
    }

    public static ArrayList<String> run(String shell, String[] commands) {
        ArrayList<String> output = new ArrayList<String>();

        try {
            Process process = Runtime.getRuntime().exec(shell);

            BufferedOutputStream shellInput =
                    new BufferedOutputStream(process.getOutputStream());
            BufferedReader shellOutput =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            for (String command : commands) {
                Log.i(TAG, "command: " + command);
                shellInput.write((command + " 2>&1\n").getBytes());
            }

            shellInput.write("exit\n".getBytes());
            shellInput.flush();

            String line;
            while ((line = shellOutput.readLine()) != null) {
                Log.d(TAG, "command output: " + line);
                output.add(line);
            }

            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return output;
    }

    public static boolean writeStoreFile(Context context, int uid, int execUid, String cmd, int allow) {
        File storedDir = new File(context.getFilesDir().getAbsolutePath() + File.separator + "stored");
        storedDir.mkdirs();
        if (cmd == null) {
            Log.d(TAG, "App stored for logging purposes, file not required");
            return false;
        }
        String fileName = uid + "-" + execUid;
        try {
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(
                    new File(storedDir.getAbsolutePath() + File.separator + fileName)));
            switch (allow) {
                case AllowType.ALLOW:
                    out.write("allow\n");
                    break;
                case AllowType.DENY:
                    out.write("deny\n");
                    break;
                default:
                    out.write("prompt\n");
            }
            out.write(cmd);
            out.write('\n');
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Store file not written", e);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "Store file not written", e);
            return false;
        }
        return true;
    }
    
    public static boolean writeDefaultStoreFile(Context context) {
        File storedDir = new File(context.getFilesDir().getAbsolutePath() + File.separator + "stored");
        storedDir.mkdirs();
        File defFile = new File(storedDir.getAbsolutePath() + File.separator + "default");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String action = prefs.getString(Preferences.AUTOMATIC_ACTION, "prompt");
        try {
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(defFile.getAbsolutePath()));
            out.write(action);
            out.write("\n");
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Default file not written", e);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "Default file not written", e);
            return false;
        }
        return true;
    }
}
