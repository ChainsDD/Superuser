package com.noshufou.android.su.service;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.noshufou.android.su.R;
import com.noshufou.android.su.util.Util;
import com.noshufou.android.su.util.Util.VersionInfo;

public class UpdaterService extends Service {
    private static final String TAG = "UpdaterService";

    private static final String MANIFEST_URL = "http://downloads.androidsu.com/superuser/su/manifestv2.json";

    public static final int TASK_DOWNLOAD_MANIFEST = 0;
    public static final int TASK_UPDATE = 1;
    public static final int NOTIFICATION_ID = 42;

    private class Manifest {
        public String version;
        public int versionCode;
        public String binaryUrl;
        public String binaryMd5;

        public boolean populate(JSONArray jsonArray) {
            try {
                int myVersionCode = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionCode;
                JSONObject manifest = null;
                for (int i = 0; i < jsonArray.length(); i++) {
                    manifest = jsonArray.getJSONObject(i);
                    if (manifest.getInt("min-apk-version") <= myVersionCode)
                        break;
                }

                if (manifest == null)
                    return false;

                version = manifest.getString("version");
                versionCode = manifest.getInt("version-code");

                String abi = Build.CPU_ABI.split("-")[0];
                JSONObject binaryInfo = manifest.getJSONObject(abi);
                binaryUrl = binaryInfo.getString("binary");
                binaryMd5 = binaryInfo.getString("binary-md5sum");
            } catch (JSONException e) {
                return false;
            } catch (NameNotFoundException e) {
                // This should never happen
                Log.e(TAG, "Divided by zero...", e);
                return false;
            }

            // verify that all values have been properly initialized
            if (version == null || versionCode == 0 ||
                    binaryUrl == null || binaryMd5 == null) {
                return false;
            }
            return true;
        }
    }

    public class Step {
        public static final int STATE_FAILED = -1;
        public static final int STATE_IN_PROGRESS = 0;
        public static final int STATE_SUCCESSFUL = 1;

        public int stepNumber;
        public int maxStep;
        public int descRes;
        public int state;
        public CharSequence result;

        public Step(int stepNumber, int maxStep, int[] stepSet) {
            this.stepNumber = stepNumber;
            this.maxStep = maxStep;
            this.descRes = stepSet[stepNumber];
            this.state = STATE_IN_PROGRESS;
        }

        private void finish(boolean success) {
            finish(success, getString(success ? R.string.updater_ok : R.string.updater_fail));
        }

        private void finish(boolean success, CharSequence result) {
            this.state = success ? STATE_SUCCESSFUL : STATE_FAILED;
            this.result = result;
            notifyListener(this);
        }

        private Step increment(int[] stepSet) {
            Step newStep = new Step(++this.stepNumber, this.maxStep, stepSet);
            mSteps.add(newStep);
            notifyListener(newStep);
            return newStep;
        }
    }

    public interface UpdaterListener {
        void onStepsChanged(Step step);
        void onFinishTask(int task);
    }

    public class UpdaterBinder extends Binder {
        public UpdaterService getService() {
            return UpdaterService.this;
        }
    }
    
    private static final int[] DOWNLOAD_MANIFEST_STEPS = new int[] {
        R.string.updater_step_download_manifest,
        R.string.updater_step_parse_manifest,
        R.string.updater_step_latest_version,
        R.string.updater_step_check_installed_version
    };
    
    private static final int[] UPDATER_STEPS = new int[] {
        R.string.updater_step_fix_db,
        R.string.updater_step_unpack_sutools,
        R.string.updater_step_check_installed_path,
        R.string.updater_step_download_su,
        R.string.updater_step_get_root,
        R.string.updater_step_remount_rw,
        R.string.updater_step_cp,
        R.string.updater_step_chmod,
        R.string.updater_step_ops_check,
        R.string.updater_step_mv,
        R.string.updater_step_remount_ro,
        R.string.updater_step_check_installed_version,
        R.string.updater_step_clean_up
    };

    private final IBinder mBinder = new UpdaterBinder();

    private UpdaterListener mListener;
    private Manifest mManifest;
    private VersionInfo mSuVersionInfo;
    private String mSuToolsPath;
    private ArrayList<Step> mSteps = new ArrayList<Step>();
    private boolean mCancelled = false;
    private boolean mRunning = false;
    private boolean mSilent = false;

    private Thread mWorkerThread;
    private Handler mHandler;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        mHandler = new Handler();
    }

    public void getManifest() {
        mCancelled = false;
        mWorkerThread = new Thread(new DownloadManifestRunnable());
        mWorkerThread.start();
    }

    public void update() {
        mCancelled = false;
        mWorkerThread = new Thread(new UpdaterRunnable());
        mWorkerThread.start();
    }
    
    public void cancel() {
        mCancelled = true;
    }
    
    public boolean isRunning() {
        return mRunning;
    }
    
    public void setSilent(boolean silent) {
        mSilent = silent;
    }

    public void registerUpdaterListener(UpdaterListener listener) {
        mListener = listener;
    }

    public void unregisterUpdaterListener() {
        mListener = null;
    }

    private void notifyListener(final Step step) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.onStepsChanged(step);
                }
            }
        });
    }

    private class DownloadManifestRunnable implements Runnable {

        @Override
        public void run() {
            mRunning = true;
            int totalSteps = DOWNLOAD_MANIFEST_STEPS.length;
            Step currentStep;
            boolean stepSuccess = false;

            // Download the manifest
            if (mCancelled) return;
            currentStep = new Step(0, totalSteps, DOWNLOAD_MANIFEST_STEPS);
            mSteps.add(currentStep);
            notifyListener(currentStep);
            stepSuccess = downloadFile(MANIFEST_URL, "manifest", null);
            currentStep.finish(stepSuccess);

            // Ensure the manifest was created
            if (mCancelled || !stepSuccess) return;
            currentStep = currentStep.increment(DOWNLOAD_MANIFEST_STEPS);
            stepSuccess = (mManifest != null);
            currentStep.finish(stepSuccess);

            // Display the latest version
            if (mCancelled || !stepSuccess) return;
            currentStep = currentStep.increment(DOWNLOAD_MANIFEST_STEPS);
            currentStep.finish(true, mManifest.version);
            
            // Check currently installed version
            if (mCancelled) return;
            currentStep = currentStep.increment(DOWNLOAD_MANIFEST_STEPS);
            mSuVersionInfo = Util.getSuVersionInfo();
            currentStep.finish(mSuVersionInfo.versionCode >= mManifest.versionCode,
                    mSuVersionInfo.version);

            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (mListener != null)
                        mListener.onFinishTask(TASK_DOWNLOAD_MANIFEST);
                }
                
            });
            
            if (mSilent && mSuVersionInfo.versionCode < mManifest.versionCode) {
                mWorkerThread = new Thread(new UpdaterRunnable());
                mWorkerThread.start();
            } else {
                mRunning = false;
            }
        }
    }

    private class UpdaterRunnable implements Runnable {
        @Override
        public void run() {
            mRunning = true;
            int totalSteps = UPDATER_STEPS.length;
            Step currentStep = new Step(0, totalSteps, UPDATER_STEPS);
            boolean stepSuccess = false;
            boolean fixDb = false;

            // Fix the DB if necessary
            if (mCancelled) return;
            if (mSuVersionInfo.versionCode == 0) {
                fixDb = true;
                mSteps.add(currentStep);
                notifyListener(currentStep);
                SQLiteDatabase db = null;
                try {
                    db = openOrCreateDatabase("permissions.sqlite", Context.MODE_PRIVATE, null);
                    db.execSQL("CREATE TABLE IF NOT EXISTS apps (_id INTEGER, uid INTEGER, " +
                            "package TEXT, name TEXT, exec_uid INTEGER, exec_cmd TEXT, " +
                            " allow INTEGER, PRIMARY KEY (_id), UNIQUE (uid,exec_uid,exec_cmd))");
                    db.execSQL("CREATE TABLE IF NOT EXISTS logs (_id INTEGER, app_id INTEGER, " +
                            "date INTEGER, type INTEGER, PRIMARY KEY (_id))");
                    db.execSQL("CREATE TABLE IF NOT EXISTS prefs (_id INTEGER, key TEXT, " + 
                            "value TEXT, PRIMARY KEY (_id))");
                } catch (SQLException e) {
                    Log.e(TAG, "Failed to fix database", e);
                    currentStep.finish(false);
                    return;
                } finally {
                    if (db != null) {
                        db.close();
                    }
                }
                currentStep.finish(true);
            }

            // Unpack sutools
            currentStep = currentStep.increment(UPDATER_STEPS);
            mSuToolsPath = Util.ensureSuTools(UpdaterService.this);
            stepSuccess = mSuToolsPath != null;
            currentStep.finish(stepSuccess);

            // Check where current su binary is installed
            if (mCancelled || !stepSuccess) return;
            currentStep = currentStep.increment(UPDATER_STEPS);
            String installedSu = Util.whichSu();
            if (installedSu == null) {
                currentStep.finish(false);
                return;
            } else if (installedSu.equals("/sbin/su")) {
                currentStep.finish(false, installedSu);
                return;
            }
            currentStep.finish(true, installedSu);

            // Download new su binary
            if (mCancelled) return;
            currentStep = currentStep.increment(UPDATER_STEPS);
            String suPath;
            if (downloadFile(mManifest.binaryUrl, "su", mManifest.binaryMd5)) {
                suPath = getFileStreamPath("su").toString();
                currentStep.finish(true);
            } else {
                currentStep.finish(false);
                return;
            }

            Process process = null;
            try {
                // Once try/catch block for all root commands

                // Get root access
                if (mCancelled || !stepSuccess) return;
                currentStep = currentStep.increment(UPDATER_STEPS);
                process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                BufferedReader is = new BufferedReader(new InputStreamReader(
                        new DataInputStream(process.getInputStream())));

                // Long timeout here in case the user has to respond to the prompt
                stepSuccess = executeCommand(os, is, 2000, mSuToolsPath, "id");
                currentStep.finish(stepSuccess);

                // remount system partition
                if (mCancelled || !stepSuccess) return;
                currentStep = currentStep.increment(UPDATER_STEPS);
                stepSuccess = executeCommand(os, is, mSuToolsPath, "mount -o remount,rw /system");
                currentStep.finish(stepSuccess);

                // Copy su to /system
                if (mCancelled || !stepSuccess) return;
                currentStep = currentStep.increment(UPDATER_STEPS);
                File newSu = new File(installedSu + "-new");
                executeCommand(os, is, mSuToolsPath, "cat", suPath, ">", newSu.getAbsolutePath());
                stepSuccess = newSu.exists();
                currentStep.finish(stepSuccess);

                // Change su filemode
                if (mCancelled || !stepSuccess) return;
                currentStep = currentStep.increment(UPDATER_STEPS);
                stepSuccess = executeCommand(os, is, mSuToolsPath, "chmod 06755", newSu.getAbsolutePath());
                currentStep.finish(stepSuccess);

                // Ops check
                if (mCancelled || !stepSuccess) return;
                currentStep = currentStep.increment(UPDATER_STEPS);
                Process process2 = Runtime.getRuntime().exec(newSu.getAbsolutePath());
                DataOutputStream os2 = new DataOutputStream(process2.getOutputStream());
                BufferedReader is2 = new BufferedReader(new InputStreamReader(
                        new DataInputStream(process2.getInputStream())));
                // Another long timeout since there may be a prompt again
                stepSuccess = executeCommand(os2, is2, 2000, mSuToolsPath, "id");
                currentStep.finish(stepSuccess);
                if (stepSuccess) os2.writeBytes("exit\n");

                // Move su to where it belongs
                if (mCancelled || !stepSuccess) return;
                currentStep = currentStep.increment(UPDATER_STEPS);
                stepSuccess = executeCommand(os, is, mSuToolsPath, "mv", newSu.getAbsolutePath(), installedSu);
                currentStep.finish(stepSuccess);

                // remount system partition
                if (mCancelled || !stepSuccess) return;
                currentStep = currentStep.increment(UPDATER_STEPS);
                stepSuccess = executeCommand(os, is, mSuToolsPath, "mount -o remount,ro /system");
                currentStep.finish(stepSuccess);

                os.writeBytes("exit\n");
            } catch (IOException e) {
                Log.e(TAG, "Failed to execute root commands", e);
            }

            // Check currently installed version
            if (mCancelled || !stepSuccess) return;
            currentStep = currentStep.increment(UPDATER_STEPS);
            mSuVersionInfo = Util.getSuVersionInfo();
            stepSuccess = mSuVersionInfo.versionCode == mManifest.versionCode;
            currentStep.finish(stepSuccess, mSuVersionInfo.version);

            // Cleanup
            currentStep = currentStep.increment(UPDATER_STEPS);
            deleteFile("su");
            if (fixDb) {
                deleteDatabase("permissions.sqlite");
            }
            currentStep.finish(true);

            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (mListener != null)
                        mListener.onFinishTask(TASK_UPDATE);
                }
                
            });
            mRunning = false;
        }

    }
    private boolean downloadFile(String urlStr, String localName, String md5) {
        DigestInputStream bis = null;
        
        try {
            URL url = new URL(urlStr);
            
            URLConnection urlCon = url.openConnection();
            MessageDigest md = MessageDigest.getInstance("MD5");
            bis = new DigestInputStream(urlCon.getInputStream(), md);

            ByteArrayBuffer baf = new ByteArrayBuffer(50);
            int current = 0;
            while (((current = bis.read()) != -1)) {
                baf.append((byte) current);
                if (mCancelled) {
                    return false;
                }
            }
            bis.close();
            
            if (mCancelled) {
                return false;
            } else if (localName.equals("manifest")) {
                try {
                    mManifest = new Manifest();
                    return mManifest.populate(new JSONArray(new String(baf.toByteArray())));
                } catch (JSONException e) {
                    Log.e(TAG, "Malformed manifest file", e);
                    return false;
                }
            } else {
                FileOutputStream outFileStream = openFileOutput(localName, 0);
                outFileStream.write(baf.toByteArray());
                outFileStream.close();
            }
            
            if (md5 != null) {
                byte[] digest = md.digest();
                Log.d(TAG, "download md5 = " + convertToHex(digest));
                Log.d(TAG, "target md5   = " + md5);
                if (!convertToHex(digest).equals(md5)) return false;
            }

        } catch (MalformedURLException e) {
            Log.e(TAG, "Bad URL: " + urlStr, e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Problem downloading file: " + localName, e);
            return false;
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, "MD5 algorithm not available");
            return false;
        }
        return true;
    }

    private boolean executeCommand(DataOutputStream os, BufferedReader is, String... commands)
            throws IOException {
        return executeCommand(os, is, 400, commands);
    }
    
    private boolean executeCommand(DataOutputStream os, BufferedReader is, int timeout,
            String... commands) throws IOException {
        if (commands.length == 0) return false;
        StringBuilder command = new StringBuilder();
        for (String s : commands) {
            command.append(s).append(" ");
        }
        command.append("\n");
        os.writeBytes(command.toString());
        os.flush();
        Log.d(TAG, command.toString());
        if (is != null) {
            for (int i = 0; i < timeout; i++) {
                if (is.ready()) break;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Sleep timer interrupted", e);
                }
            }
            if (is.ready()) {
                String result = is.readLine();
                Log.d(TAG, result);
                return result.equals("0");
            } else {
                Log.d(TAG, "timeout");
                return false;
            }
        } else {
            Log.d(TAG, "null input stream");
            return false;
        }
    }

    private static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                    halfbyte = data[i] & 0x0F;
                } while(two_halfs++ < 1);
            }
        return buf.toString();
    }}
