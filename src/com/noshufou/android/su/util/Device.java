
package com.noshufou.android.su.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public class Device {

    private static final String TAG = "Voodoo OTA RootKeeper Device";

    private Context mContext;
    public SuOperations mSuOps;

    public Boolean isRooted = false;
    public Boolean isSuperuserAppInstalled = false;
    public Boolean isSuProtected = false;

    public String validSuPath = SuOperations.SU_BACKUP_PATH;
    public boolean needSuBackupUpgrade = false;

    public enum FileSystem {
        EXTFS,
        UNSUPPORTED
    }

    public FileSystem mFileSystem = FileSystem.UNSUPPORTED;

    public Device(Context context) {
        mContext = context;

        ensureAttributeUtilsAvailability();
        detectSystemFs();

        analyzeSu();

        mSuOps = new SuOperations(context, this);
    }

    private void detectSystemFs() {
        // detect an ExtFS filesystem

        try {
            BufferedReader in = new BufferedReader(new FileReader("/proc/mounts"), 8192);

            String line;
            String parsedFs;

            while ((line = in.readLine()) != null) {
                if (line.matches(".*system.*")) {
                    Log.i(TAG, "/system mount point: " + line);
                    parsedFs = line.split(" ")[2].trim();

                    if (parsedFs.equals("ext2")
                            || parsedFs.equals("ext3")
                            || parsedFs.equals("ext4")) {
                        Log.i(TAG, "/system filesystem support extended attributes");
                        mFileSystem = FileSystem.EXTFS;
                        return;
                    }
                }
            }
            in.close();

        } catch (Exception e) {
            Log.e(TAG, "Impossible to parse /proc/mounts");
            e.printStackTrace();
        }

        Log.i(TAG, "/system filesystem doesn't support extended attributes");
        mFileSystem = FileSystem.UNSUPPORTED;

    }

    private void ensureAttributeUtilsAvailability() {

        String[] symlinks = {
                "test",
                "lsattr",
                "chattr"
        };

        // verify custom busybox presence by test, lsattr and chattr
        // files/symlinks
        try {
            mContext.openFileInput("busybox");
            for (String s : symlinks)
                mContext.openFileInput(s);

        } catch (FileNotFoundException notfoundE) {
            Log.d(TAG, "Extracting tools from assets is required");

            try {
                Util.copyFromAssets(mContext, "busybox-armeabi", "busybox");

                String filesPath = mContext.getFilesDir().getAbsolutePath();
                String script = "chmod 700 " + filesPath + "/busybox\n";
                for (String s : symlinks) {
                    script += "rm " + filesPath + "/" + s + "\n";
                    script += "ln -s busybox " + filesPath + "/" + s + "\n";
                }

                Util.run(script);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void analyzeSu() {
        isRooted = detectValidSuBinaryInPath();
        isSuperuserAppInstalled = isSuperUserApkinstalled();
        isSuProtected = isSuProtected();
    }

    private Boolean isSuProtected() {

        HashMap<String, Boolean> paths = new HashMap<String, Boolean>();
        paths.put(SuOperations.SU_BACKUP_PATH, false);
        paths.put(SuOperations.SU_BACKUP_PATH_OLD, true);

        for (String path : paths.keySet())
            switch (mFileSystem) {
                case EXTFS:
                    String lsattr = mContext.getFilesDir().getAbsolutePath() + "/lsattr";

                    ArrayList<String> output = Util.run(lsattr + " " + path);

                    if (output.size() == 1) {
                        String attrs = output.get(0);
                        Log.d(TAG, "attributes: " + attrs);

                        if (attrs.matches(".*-i-.*" + SuOperations.SU_BACKUP_FILENAME)) {
                            if (Util.isSuid(mContext, path)) {
                                Log.i(TAG, "su binary is already protected");
                                validSuPath = path;
                                needSuBackupUpgrade = paths.get(path);
                                return true;
                            }
                        }
                    }

                    break;

                case UNSUPPORTED:
                    if (Util.isSuid(mContext, SuOperations.SU_BACKUP_PATH)) {
                        validSuPath = path;
                        needSuBackupUpgrade = paths.get(path);
                        return true;
                    }

            }
        return false;
    }

    private Boolean detectValidSuBinaryInPath() {
        // search for valid su binaries in PATH

        String[] pathToTest = System.getenv("PATH").split(":");

        for (String path : pathToTest) {
            File suBinary = new File(path + "/su");

            if (suBinary.exists()) {
                if (Util.isSuid(mContext, suBinary.getAbsolutePath())) {
                    Log.d(TAG, "Found adequate su binary at " + suBinary.getAbsolutePath());
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean isSuperUserApkinstalled() {
        try {
            mContext.getPackageManager().getPackageInfo("com.noshufou.android.su", 0);
            Log.d(TAG, "Superuser.apk installed");
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

}