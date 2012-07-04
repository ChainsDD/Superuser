
package com.noshufou.android.su.util;

import java.util.ArrayList;

import com.noshufou.android.su.util.Device.FileSystem;

import android.content.Context;
import android.util.Log;

public class SuOperations {

    private Context mContext;
    private Device mDevice;

    private static final String TAG = "Voodoo OTA RootKeeper ProtectedSuOperation";
    public static final String SU_PATH = "/system/bin/su";
    public static final String SU_BACKUP_BASE_DIR = "/system/usr";
    public static final String SU_BACKUP_DIR = SU_BACKUP_BASE_DIR + "/we-need-root";
    public static final String SU_BACKUP_FILENAME = "su-backup";
    public static final String SU_BACKUP_PATH = SU_BACKUP_DIR + "/" + SU_BACKUP_FILENAME;
    public static final String SU_BACKUP_PATH_OLD = "/system/" + SU_BACKUP_FILENAME;
    public static final String CMD_REMOUNT_RW = "mount -o remount,rw /system /system";
    public static final String CMD_REMOUNT_RO = "mount -o remount,ro /system /system";

    public SuOperations(Context context, Device device) {
        mContext = context;
        mDevice = device;
    }

    public final void backup() {

        Log.i(TAG, "Backup to protected su");

        String suSource = "/system/xbin/su";

        ArrayList<String> commands = new ArrayList<String>();
        commands.add(CMD_REMOUNT_RW);

        // de-protect
        if (mDevice.mFileSystem == FileSystem.EXTFS)
            commands.add(mContext.getFilesDir().getAbsolutePath()
                    + "/chattr -i " + SU_BACKUP_PATH);

        if (Util.isSuid(mContext, SU_PATH))
            suSource = SU_PATH;

        commands.add("mkdir " + SU_BACKUP_BASE_DIR);
        commands.add("mkdir " + SU_BACKUP_DIR);
        commands.add("chmod 001 " + SU_BACKUP_DIR);
        commands.add("cat " + suSource + " > " + SU_BACKUP_PATH);
        commands.add("chmod 06755 " + SU_BACKUP_PATH);

        // protect
        if (mDevice.mFileSystem == FileSystem.EXTFS)
            commands.add(mContext.getFilesDir().getAbsolutePath()
                    + "/chattr +i " + SU_BACKUP_PATH);

        commands.add(CMD_REMOUNT_RO);

        Util.run("su", commands);
    }

    public final void restore() {
        String[] commands = {
                CMD_REMOUNT_RW,

                // restore su binary to /system/bin/su
                // choose bin over xbin to avoid confusion
                "cat " + mDevice.validSuPath + " > " + SU_PATH,
                "chown 0:0 " + SU_PATH,
                "chmod 06755 " + SU_PATH,
                "rm /system/xbin/su",

                CMD_REMOUNT_RO,
        };

        Util.run(mDevice.validSuPath, commands);
        upgradeSuBackup();
    }

    public final void deleteBackup() {

        Log.i(TAG, "Delete protected or backup su");

        ArrayList<String> commands = new ArrayList<String>();
        commands.add(CMD_REMOUNT_RW);

        // de-protect
        if (mDevice.mFileSystem == FileSystem.EXTFS)
            commands.add(mContext.getFilesDir().getAbsolutePath()
                    + "/chattr -i " + mDevice.validSuPath);

        commands.add("rm " + mDevice.validSuPath);
        commands.add("rm -r " + SU_BACKUP_DIR);
        commands.add(CMD_REMOUNT_RO);

        Util.run("su", commands);
    }

    public final void unRoot() {

        Log.i(TAG, "Unroot device but keep su backup");

        upgradeSuBackup();
        if (!mDevice.isSuProtected) {
            backup();
        }

        String[] commands = new String[] {
                CMD_REMOUNT_RW,
                "rm /system/*bin/su",
                CMD_REMOUNT_RO,
        };

        Util.run("su", commands);

    }

    private void upgradeSuBackup() {
        if (!mDevice.needSuBackupUpgrade)
            return;

        Log.i(TAG, "Upgrade su backup");
        deleteBackup();
        backup();
        mDevice.analyzeSu();
    }
}